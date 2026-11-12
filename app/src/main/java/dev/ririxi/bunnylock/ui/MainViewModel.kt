package dev.ririxi.bunnylock.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.core.Bands
import dev.ririxi.bunnylock.data.Cell
import dev.ririxi.bunnylock.data.LockNotifier
import dev.ririxi.bunnylock.data.Rat
import dev.ririxi.bunnylock.data.Updates
import dev.ririxi.bunnylock.modem.Backend
import dev.ririxi.bunnylock.modem.BandGroupState
import dev.ririxi.bunnylock.modem.CellLockRequest
import dev.ririxi.bunnylock.modem.Group
import dev.ririxi.bunnylock.modem.ImsFeature
import dev.ririxi.bunnylock.modem.LockRat
import dev.ririxi.bunnylock.modem.ModemController
import dev.ririxi.bunnylock.modem.ModemState
import dev.ririxi.bunnylock.modem.NetMode
import dev.ririxi.bunnylock.modem.NrMode
import dev.ririxi.bunnylock.modem.RootState
import dev.ririxi.bunnylock.modem.Text
import dev.ririxi.bunnylock.modem.asSentence
import dev.ririxi.bunnylock.modem.reason
import dev.ririxi.bunnylock.modem.string
import dev.ririxi.bunnylock.shizuku.RootOrShizuku
import dev.ririxi.bunnylock.shizuku.ShizukuClient
import dev.ririxi.bunnylock.shizuku.samsung.SamsungBandMenu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_TOOL_OUTPUT_CHARS = 8000

private const val EXCLUSIVE_BAND_LOCK_KEY = "noBandsTurnsOff"

class MainViewModel(
    private val app: Application,
) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("bunnylock", Context.MODE_PRIVATE)

    private val _ui =
        MutableStateFlow(
            UiState(
                dynamicColor = prefs.getBoolean("dynamicColor", true),
                checkUpdates = prefs.getBoolean("checkUpdates", true),
                exclusiveBandLock = prefs.getBoolean(EXCLUSIVE_BAND_LOCK_KEY, false),
                favoriteBands = loadFavoriteBands(),
            ),
        )

    private val ui get() = _ui.value

    private val latest: UiState get() = ui.copy(modem = modem.state.value)

    private inline fun updateState(transform: UiState.() -> UiState) = _ui.update(transform)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var pendingUnlock = false

    private val modem =
        ModemController(
            app,
            viewModelScope,
            RootOrShizuku(app, ::shizukuLost),
            onConnected = ::unlockIfPending,
            onRefreshed = ::refreshed,
            shizukuMenuLauncher = { SamsungBandMenu.open(app) },
        )

    // Shizuku hands over its binder a moment after the app starts, or whenever it is started later, so a failed attempt is redone then.
    private val stopWatchingShizuku = ShizukuClient.onBinderReceived(modem::reconnect)

    val state =
        combine(modem.state, _ui) { modemState, uiState -> uiState.copy(modem = modemState) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, latest)

    val events = merge(_events, modem.events)

    init {
        modem.connect()
        checkForUpdate()
    }

    private fun appVersion(): String? = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()

    private fun checkForUpdate() =
        viewModelScope.launch {
            if (!ui.checkUpdates) return@launch
            val installed = appVersion() ?: return@launch
            try {
                val release = Updates.newerThan(installed)?.takeIf { it.version != prefs.getString("skippedUpdate", null) }
                if (ui.checkUpdates) updateState { copy(update = release) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                modem.log("Couldn't check for updates. ${e.message?.takeIf { it.isNotBlank() }?.asSentence() ?: "Something went wrong."}")
            }
        }

    fun dismissUpdate() = updateState { copy(update = null) }

    fun skipUpdate() {
        ui.update?.let { prefs.edit().putString("skippedUpdate", it.version).apply() }
        dismissUpdate()
    }

    fun setCheckUpdates(enabled: Boolean) {
        prefs.edit().putBoolean("checkUpdates", enabled).apply()
        updateState { copy(checkUpdates = enabled) }
        if (enabled) checkForUpdate() else dismissUpdate()
    }

    override fun onCleared() {
        stopWatchingShizuku()
        modem.close()
    }

    fun connect() = modem.connect()

    private var shownLocales = app.resources.configuration.locales

    /** Coming back from the Shizuku app after allowing bunnylock there shouldn't need a tap on "Try again". */
    fun resumed() {
        if (modem.state.value.root is RootState.Denied && ShizukuClient.granted()) modem.connect()
        val locales = app.resources.configuration.locales
        if (locales == shownLocales) return
        shownLocales = locales
        if (modem.state.value.root == RootState.Granted) refresh()
    }

    private fun shizukuLost() {
        modem.helperLost(Text(R.string.system_shizuku_lost))
    }

    fun refresh() = modem.refresh()

    suspend fun refreshConnection() = modem.refreshConnection()

    fun unlockFromNotification() {
        if (modem.state.value.root == RootState.Granted && modem.helperRunning) unlockEverything() else pendingUnlock = true
    }

    private fun unlockIfPending() {
        if (!pendingUnlock) return
        pendingUnlock = false
        unlockEverything()
    }

    private fun refreshed(modemState: ModemState) {
        val supported = modemState.groups.associate { it.group to it.supported.toSet() }
        updateState {
            val selections = bandSelections.filterKeys { it in supported }.mapValues { (group, bands) -> bands intersect supported.getValue(group) }
            copy(bandSelections = selections)
        }
        updateNotification(modemState)
    }

    private fun updateNotification(modemState: ModemState) {
        val lines =
            buildList {
                modemState.groups.filter { it.locked && it.stray === null }.forEach { groupState ->
                    val bands =
                        groupState.current
                            .orEmpty()
                            .sorted()
                            .joinToString(" ") { groupState.group.bandLabel(it) }
                    add(app.getString(R.string.system_notify_line_bands, groupState.group.label, bands))
                }
                modemState.plmnLock?.let { add(app.getString(R.string.system_notify_line_network, it)) }
                if (modemState.strayChannel === null) {
                    modemState.lteLock?.let { add(app.getString(R.string.system_notify_line_lte_cell, it)) }
                    modemState.nrLock?.let { add(app.getString(R.string.system_notify_line_nr_cell, it)) }
                    modemState.legacyCellLock?.let { add(app.getString(R.string.system_notify_line_legacy_cell, it)) }
                }
            }
        if (lines.isEmpty()) {
            prefs.edit().remove("lockedSince").apply()
            LockNotifier.cancel(app)
            return
        }
        val lockedSince =
            prefs.getLong("lockedSince", 0L).takeIf { it > 0 }
                ?: System.currentTimeMillis().also { prefs.edit().putLong("lockedSince", it).apply() }
        LockNotifier.show(app, lines, lockedSince)
    }

    fun loadSims() = modem.loadSims()

    fun setSim(sim: Int) {
        discardPendingBands()
        modem.setSim(sim)
    }

    fun setBackend(backend: Backend) {
        discardPendingBands()
        modem.setBackend(backend)
    }

    fun setPersist(enabled: Boolean) = modem.setPersist(enabled)

    fun forgetNeedsChannels() = modem.forgetNeedsChannels()

    fun forgetKeptOffBands() = modem.forgetKeptOffBands()

    fun stopWaiting() = modem.stopWaiting()

    fun setExclusiveBandLock(enabled: Boolean) {
        prefs.edit().putBoolean(EXCLUSIVE_BAND_LOCK_KEY, enabled).apply()
        updateState { copy(exclusiveBandLock = enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean("dynamicColor", enabled).apply()
        updateState { copy(dynamicColor = enabled) }
    }

    fun setAtNode(node: String) = modem.setAtNode(node)

    private fun launchAction(
        refreshAfter: Boolean = true,
        block: suspend () -> String?,
    ) = viewModelScope.launch {
        if (!modem.helperRunning) {
            _events.emit(app.getString(R.string.system_helper_not_running))
            modem.reconnect()
            return@launch
        }
        updateState { copy(busy = true) }
        try {
            block()?.let { _events.emit(it) }
            if (refreshAfter) modem.refreshNow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.emit(app.reason(e))
        } finally {
            updateState { copy(busy = false) }
        }
    }

    private fun groupState(group: Group): BandGroupState? = modem.state.value.groups.firstOrNull { it.group == group }

    private fun updateSelection(
        group: Group,
        pick: UiState.(groupState: BandGroupState, selected: Set<Int>) -> Set<Int>,
    ) {
        val groupState = groupState(group) ?: return
        val modemState = modem.state.value
        updateState {
            val current = copy(modem = modemState)
            val picked = current.pick(groupState, current.selectedBands(groupState))
            val untouched = picked == current.startingBands(groupState)
            copy(bandSelections = if (untouched) bandSelections - group else bandSelections + (group to picked))
        }
    }

    fun setShowAllBands(
        group: Group,
        shown: Boolean,
    ) = updateState { copy(showAllBands = if (shown) showAllBands + group else showAllBands - group) }

    fun toggleBand(
        group: Group,
        band: Int,
    ) = updateSelection(group) { _, selected ->
        if (band in selected) selected - band else selected + band
    }

    fun toggleFavorite(
        group: Group,
        band: Int,
    ) {
        val favorites = ui.favoritesOf(group).let { if (band in it) it - band else it + band }
        prefs.edit().putString(favoritesPrefKey(group.favoritesKey), favorites.sorted().joinToString(",")).apply()
        updateState { copy(favoriteBands = favoriteBands + (group.favoritesKey to favorites)) }
    }

    private fun loadFavoriteBands(): Map<String, Set<Int>> =
        Group.entries.map { it.favoritesKey }.distinct().associateWith { key ->
            prefs.getString(favoritesPrefKey(key), null).orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()
        }

    private fun favoritesPrefKey(key: String) = "favoriteBands.$key"

    fun selectAll(
        group: Group,
        all: Boolean,
    ) = updateSelection(group) { groupState, _ ->
        if (all) visibleBands(groupState).toSet() else emptySet()
    }

    fun unlockGroup(group: Group) = launchAction(refreshAfter = false) { sendGroup(group, unlock = true) }

    fun applyPendingGroups(including: Group? = null) =
        launchAction(refreshAfter = false) {
            val current = latest
            val pendingAtStart = current.modem.groups.filter(current::isPending).map { it.group }.toSet()
            val sendable = current.modem.groups.filter { it.group == including || it.group in pendingAtStart }
            // In exclusive mode each lock turns off the networks without one, so every card with bands picked stays on for the whole press.
            val keepOn = sendable.filter { current.selectedBands(it).isNotEmpty() }.map { it.group }
            val narrowing = sendable.filter(current::locksToFewerBands)
            val outcomes = mutableListOf<String>()
            val togetherOutcome = if (modem.locksBandsTogether && narrowing.size > 1) lockTogether(narrowing, keepOn) else null
            togetherOutcome?.let(outcomes::add)
            for (groupState in if (togetherOutcome === null) sendable else sendable - narrowing.toSet()) {
                val group = groupState.group
                // An earlier lock in this press may already have done what this card asks, such as turning its network off.
                if (group in pendingAtStart && !stillPending(group)) {
                    forgetSent(mapOf(group to latest.bandSelections[group]))
                    continue
                }
                val outcome =
                    try {
                        sendGroup(group, keepOn = keepOn)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The next card works out the network mode from the state, so it has to see what this failure left.
                        modem.refreshNow()
                        app.reason(e)
                    }
                outcome?.let(outcomes::add)
            }
            outcomes.distinct().joinToString("\n").ifEmpty { null }
        }

    private suspend fun lockTogether(
        groupStates: List<BandGroupState>,
        keepOn: List<Group>,
    ): String? =
        try {
            sendGroups(groupStates, keepOn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            modem.log("Locking ${groupStates.joinToString(" and ") { it.group.label }} together didn't work, so each network is locked on its own.")
            modem.refreshNow()
            null
        }

    private fun stillPending(group: Group): Boolean = groupState(group)?.let(latest::isPending) == true

    fun discardPendingBands() = updateState { copy(bandSelections = emptyMap()) }

    private suspend fun sendGroup(
        group: Group,
        unlock: Boolean = false,
        keepOn: List<Group> = emptyList(),
    ): String? {
        val groupState = groupState(group) ?: return null
        val current = latest
        val sent = current.bandSelections[group]
        val selected = current.selectedBands(groupState)
        val exclusive = current.exclusiveBandLock
        val outcome =
            when {
                unlock -> modem.unlockBands(group, exclusive)
                selected.isEmpty() && current.canTurnOff(group) -> modem.turnOff(group)
                else -> modem.lockBands(group, selected, exclusive, keepOn)
            }
        modem.refreshNow()
        forgetSent(mapOf(group to sent))
        return outcome
    }

    private suspend fun sendGroups(
        groupStates: List<BandGroupState>,
        keepOn: List<Group>,
    ): String {
        val current = latest
        val sent = groupStates.associate { it.group to current.bandSelections[it.group] }
        val locks = groupStates.associate { it.group to current.selectedBands(it) }
        val outcome = modem.lockBands(locks, current.exclusiveBandLock, keepOn)
        modem.refreshNow()
        forgetSent(sent)
        return outcome
    }

    // Picks made while the lock ran are newer than what was sent, so they stay.
    private fun forgetSent(sent: Map<Group, Set<Int>?>) =
        updateState { copy(bandSelections = bandSelections.filterNot { (group, bands) -> sent[group] == bands }) }

    fun setNetMode(mode: NetMode) = launchAction { modem.setNetMode(mode) }

    fun setNrMode(mode: NrMode) = launchAction { modem.setNrMode(mode) }

    fun setNrDc(enabled: Boolean) = launchAction { modem.setNrDc(enabled) }

    fun setIms(
        feature: ImsFeature,
        enabled: Boolean,
    ) = launchAction(refreshAfter = false) { modem.setIms(feature, enabled) }

    fun setCarrierAggregation(enabled: Boolean) = launchAction(refreshAfter = false) { modem.setCarrierAggregation(enabled) }

    fun refreshIms() = viewModelScope.launch { modem.refreshIms() }

    fun updateForm(transform: CellForm.() -> CellForm) = updateState { copy(form = form.transform()) }

    fun fillFrom(cell: Cell) =
        updateForm {
            when (cell.rat) {
                Rat.LTE -> {
                    copy(
                        lteEarfcn = cell.arfcn.toString(),
                        ltePcis = cell.pci.toString(),
                        rat = if (rat == LockRat.NSA) rat else LockRat.LTE,
                    )
                }

                Rat.NR -> {
                    val band = cell.band ?: Bands.nrBandsOf(cell.arfcn.toInt()).firstOrNull()
                    copy(
                        nrArfcn = cell.arfcn.toString(),
                        nrPcis = cell.pci.takeIf { it >= 0 }?.toString() ?: nrPcis,
                        nrBand = band,
                        nrScs = band?.let { Bands.nrDefaultScs(it) } ?: nrScs,
                        nrScsPicked = nrScsPicked && band === null,
                        rat = if (rat == LockRat.NSA) rat else LockRat.NR,
                    )
                }

                else -> {
                    this
                }
            }
        }

    private fun parseInts(text: String) = text.split(',', ' ', ';').mapNotNull { it.trim().toIntOrNull() }

    fun lockCells() =
        launchAction {
            val form = ui.form
            val request =
                CellLockRequest(
                    rat = latest.lockRat,
                    earfcn = form.lteEarfcn.toLongOrNull(),
                    ltePcis = parseInts(form.ltePcis),
                    nrArfcn = form.nrArfcn.toLongOrNull(),
                    nrPcis = parseInts(form.nrPcis),
                    nrBand = form.nrBand ?: form.nrCandidates.firstOrNull(),
                    nrScs = form.nrScs,
                )
            modem.lockCells(request)
        }

    fun unlockCells() = launchAction { modem.unlockCells() }

    fun setPlmnForm(
        mcc: String? = null,
        mnc: String? = null,
    ) = updateState {
        copy(plmnMcc = (mcc ?: plmnMcc).filter(Char::isDigit).take(3), plmnMnc = (mnc ?: plmnMnc).filter(Char::isDigit).take(3))
    }

    fun pickPlmn(numeric: String) = updateState { copy(plmnMcc = numeric.take(3), plmnMnc = numeric.drop(3)) }

    fun lockPlmn() = launchAction { modem.lockPlmn(ui.plmnMcc, ui.plmnMnc) }

    fun unlockPlmn() = launchAction { modem.unlockPlmn() }

    fun unlockEverything() =
        launchAction {
            modem.unlockEverything(ui.exclusiveBandLock).also { discardPendingBands() }
        }

    fun radioCycle() = launchAction { modem.radioCycle() }

    fun reattach() = launchAction { modem.reattach() }

    fun relock() = launchAction { modem.relock() }

    fun rebootModem() = launchAction(refreshAfter = false) { modem.rebootModem() }

    fun samsungMenu() = launchAction(refreshAfter = false) { modem.samsungMenu() }

    fun sendAt(command: String) =
        launchAction(refreshAfter = false) {
            val reply = modem.sendAt(command)
            updateState { copy(toolOutput = "> $command\n$reply\n\n$toolOutput".take(MAX_TOOL_OUTPUT_CHARS)) }
            modem.log("The modem replied. See the output below.")
            app.getString(R.string.system_at_replied)
        }

    fun sendQmi(
        service: Int,
        msg: Int,
        payload: String,
    ) = launchAction(refreshAfter = false) {
        val reply = modem.sendQmi(service, msg, payload)
        val entry = "> svc $service msg 0x%04X %s\n%s".format(msg, payload, reply.output)
        updateState { copy(toolOutput = "$entry\n\n$toolOutput".take(MAX_TOOL_OUTPUT_CHARS)) }
        app.getString(R.string.system_qmi_replied, msg, reply.error?.let { app.string(it) }.orEmpty())
    }

    fun clearOutput() = updateState { copy(toolOutput = "") }

    private fun diagnostics(): String {
        val title = listOfNotNull("bunnylock", appVersion(), "diagnostics").joinToString(" ")
        return modem.diagnostics(title, listOf("Shizuku: ${ShizukuClient.describe(app)}"))
    }

    fun copyDiagnostics() {
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        // Kotlin sees primaryClip as read-only because its getter is nullable and its setter isn't.
        @Suppress("UsePropertyAccessSyntax")
        clipboard.setPrimaryClip(ClipData.newPlainText(app.getString(R.string.system_clip_label_diagnostics), diagnostics()))
        // Android 13 and later confirm a copy on their own.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) _events.tryEmit(app.getString(R.string.system_diagnostics_copied))
    }

    fun logFileName(): String = "bunnylock-${SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())}.log"

    fun saveLog(uri: Uri) =
        viewModelScope.launch {
            val saved =
                withContext(Dispatchers.IO) {
                    val text = diagnostics()
                    runCatching { checkNotNull(app.contentResolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray()) } }.isSuccess
                }
            _events.emit(app.getString(if (saved) R.string.system_log_saved else R.string.system_log_not_saved))
        }
}
