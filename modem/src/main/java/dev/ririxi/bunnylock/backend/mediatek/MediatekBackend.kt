package dev.ririxi.bunnylock.backend.mediatek

import dev.ririxi.bunnylock.backend.BackendProfile
import dev.ririxi.bunnylock.backend.BackendReadout
import dev.ririxi.bunnylock.backend.CellLock
import dev.ririxi.bunnylock.backend.HalLocks
import dev.ririxi.bunnylock.backend.ModemBackend
import dev.ririxi.bunnylock.backend.ModemSession
import dev.ririxi.bunnylock.backend.NetModeResult
import dev.ririxi.bunnylock.backend.bandGroupState
import dev.ririxi.bunnylock.backend.bandsToSend
import dev.ririxi.bunnylock.backend.has5g
import dev.ririxi.bunnylock.backend.pixel.Pixel
import dev.ririxi.bunnylock.core.Ran
import dev.ririxi.bunnylock.core.intList
import dev.ririxi.bunnylock.data.RootException
import dev.ririxi.bunnylock.modem.BackendFeatures
import dev.ririxi.bunnylock.modem.Chipset
import dev.ririxi.bunnylock.modem.DeviceInfo
import dev.ririxi.bunnylock.modem.Group
import dev.ririxi.bunnylock.modem.ModemState
import dev.ririxi.bunnylock.modem.R
import dev.ririxi.bunnylock.modem.Text
import dev.ririxi.bunnylock.modem.asClause
import dev.ririxi.bunnylock.modem.followedBy
import dev.ririxi.bunnylock.modem.reasonOf
import dev.ririxi.bunnylock.modem.runSuspendCatching
import dev.ririxi.bunnylock.root.OemHook
import dev.ririxi.bunnylock.root.fail
import dev.ririxi.bunnylock.root.rilNode
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

internal class MediatekBackend(
    session: ModemSession,
    hal: HalLocks,
) : ModemBackend(session, hal) {
    private val current get() = session.current

    // The selected port is kept unless it can't serve. The Pixel port isn't a MediaTek one, and ADB-level access can't open a tty.
    private suspend fun node(): String {
        val selected = current.atNode
        if (selected.startsWith(OemHook.PREFIX)) return rilNode(current.sim)
        if (selected.isNotEmpty() && selected != Pixel.NODE && !current.limited) return selected
        val found = session.call("mtk.findNode").optString("node")
        if (found.isEmpty()) fail(R.string.modem_no_mtk_port)
        session.setAtNode(found)
        return found
    }

    // A modem tty is shared with the phone's own radio software, which can swallow the reply.
    private suspend fun call(
        command: String,
        build: JSONObject.() -> Unit = {},
    ): JSONObject {
        val node = node()
        return try {
            session.call(command) {
                put("node", node)
                build()
            }
        } catch (e: RootException) {
            if (node.startsWith(OemHook.PREFIX) || current.device?.rilPassthrough != true) throw e
            val ril = rilNode(current.sim)
            session.log("$node failed (${session.englishReason(e).asClause()}), so MediaTek commands go through the RIL from now on.")
            session.setAtNode(ril)
            session.call(command) {
                put("node", ril)
                build()
            }
        }
    }

    override suspend fun read(
        sim: Int,
        aospState: JSONObject?,
    ): BackendReadout {
        val mtkState = call("mtk.state")
        val supported = mtkState.optJSONObject("supported") ?: JSONObject()
        val reported = mtkState.optJSONObject("current") ?: JSONObject()
        val groups = mutableListOf(bandGroupState(Group.LTE, supported.intList("lte"), reported.intList("lte")))
        val supportedNr = supported.intList("nr")
        if (supportedNr !== null && (supportedNr.isNotEmpty() || has5g(aospState))) {
            groups += bandGroupState(Group.NR, supportedNr, reported.intList("nr"))
        }
        groups += bandGroupState(Group.WCDMA, supported.intList("wcdma"), reported.intList("wcdma"))
        groups += bandGroupState(Group.GSM, supported.intList("gsm"), reported.intList("gsm"))
        // While Android holds part of the mode back, its mask stays, so the note can explain the difference.
        val allowedMask = mtkState.takeIf { it.has("ratMask") && aospState?.has("modeHeldBack") != true }?.optLong("ratMask")
        val carrierAggregation = mtkState.takeIf { it.has("ca") }?.optBoolean("ca")
        // A modem that refuses AT+EMMCHLCK gets its cell lock through the radio HAL, so the lock is read from there too.
        val cellLockState = mtkState.optJSONObject("cellLock")
        if (cellLockState?.optBoolean("refused") == true || cellLockIgnored) {
            val specs = hal.channelSpecs(aospState, sim)
            return BackendReadout(
                groups = groups,
                lteLock = hal.channelLock(specs, Ran.EUTRAN),
                nrLock = hal.channelLock(specs, Ran.NGRAN),
                cellLocksThroughHal = true,
                allowedMask = allowedMask,
                carrierAggregation = carrierAggregation,
            )
        }
        val cellLock = cellLockState?.takeIf { it.optBoolean("active") }
        val lockLabel = cellLock?.let(::cellLockLabel)
        // A reply too short to name the technology is shown as an LTE lock, the kind bunnylock sets most.
        val lockedAct = cellLock?.optInt("act", Mediatek.ACT_LTE)
        val nrLocked = lockedAct == Mediatek.ACT_NR
        val legacyLocked = lockedAct == Mediatek.ACT_GSM || lockedAct == Mediatek.ACT_UMTS
        return BackendReadout(
            groups = groups,
            lteLock = lockLabel.takeIf { !nrLocked && !legacyLocked },
            nrLock = lockLabel.takeIf { nrLocked },
            legacyCellLock = lockLabel.takeIf { legacyLocked },
            allowedMask = allowedMask,
            carrierAggregation = carrierAggregation,
        )
    }

    private fun cellLockLabel(lock: JSONObject): String {
        val pci = lock.optInt("pci", -1).takeIf { it >= 0 && it != Mediatek.PCI_UNSET }
        val channelName =
            when (lock.optInt("act")) {
                Mediatek.ACT_LTE -> "EARFCN"
                Mediatek.ACT_UMTS -> "UARFCN"
                else -> "ARFCN"
            }
        val arfcn = lock.optInt("arfcn").toString()
        return if (pci === null) "$channelName $arfcn" else session.context.getString(R.string.modem_lock_channel_pci, channelName, arfcn, pci.toString())
    }

    override suspend fun setBands(
        group: Group,
        bands: List<Int>,
        liftsLock: Boolean,
        done: Text,
    ): Text? {
        val key =
            when (group) {
                Group.LTE -> "lte"
                Group.WCDMA -> "wcdma"
                Group.GSM -> "gsm"
                else -> "nr"
            }
        call("mtk.setBands") { put(key, JSONArray(bandsToSend(bands, liftsLock))) }
        return null
    }

    override suspend fun setNetworkMode(
        mask: Long,
        throughAndroid: suspend () -> JSONObject,
    ): NetModeResult {
        val reply =
            try {
                throughAndroid()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                try {
                    call("mtk.setRat") { put("mask", mask) }
                } catch (atError: CancellationException) {
                    throw atError
                } catch (atError: Exception) {
                    session.log("AT+ERAT didn't set the network mode either (${session.englishReason(atError).asClause()}).")
                    throw e
                }
                session.log("Android refused the network mode (${session.englishReason(e).asClause()}), so bunnylock set it with AT+ERAT.")
                return NetModeResult(throughAndroid = false, note = Text(R.string.modem_note_set_by_at))
            }
        // MediaTek's RIL skips a mode it thinks is already set, and it doesn't see one set with AT+ERAT.
        runSuspendCatching { call("mtk.setRat") { put("mask", mask) } }
        return NetModeResult.fromAndroid(reply)
    }

    override suspend fun setCarrierAggregation(
        enabled: Boolean,
        done: Text,
    ): Text {
        val result = call("mtk.setCa") { put("enabled", enabled) }
        session.update { copy(carrierAggregation = result.optBoolean("ca", enabled)) }
        // MediaTek modems only apply AT+ECASW after they restart.
        val restart = runSuspendCatching { session.rebootModemThroughAndroid() }
        restart.exceptionOrNull()?.let { return done.followedBy(Text(R.string.modem_ca_restart_failed, reasonOf(it))) }
        return done.followedBy(restart.getOrNull() ?: Text(R.string.modem_rebooting))
    }

    override suspend fun lockCells(lock: CellLock): Text? {
        if (current.cellLocksThroughHal) return hal.lockCells(lock)
        val pcis = if (lock.lte) lock.ltePcis else lock.nrPcis
        val result =
            if (lock.lte) {
                cellLock(Mediatek.ACT_LTE, lock.earfcn, pcis.firstOrNull())
            } else {
                cellLock(Mediatek.ACT_NR, lock.arfcn, pcis.firstOrNull())
            }
        if (!result.optBoolean("active")) {
            session.log("The modem answered AT+EMMCHLCK but didn't keep the lock, so cell locks go through the radio HAL from now on.")
            cellLockIgnored = true
            runSuspendCatching { call("mtk.cellUnlock") }
            return hal.lockCells(lock)
        }
        session.restartSimRadio()
        val anchorOnly = lock.lte && lock.nr
        val outcome = Text(if (anchorOnly) R.string.modem_lte_anchor_locked else R.string.modem_cell_locked)
        val pciNote = if (pcis.size > 1) Text(R.string.modem_note_mtk_one_pci, pcis[0]) else null
        return if (anchorOnly || pciNote !== null) listOfNotNull(outcome, pciNote).reduce(Text::followedBy) else null
    }

    private suspend fun cellLock(
        act: Int,
        arfcn: Long,
        pci: Int?,
    ) = call("mtk.cellLock") {
        put("act", act)
        put("arfcn", arfcn)
        pci?.let { put("pci", it) }
    }

    override suspend fun unlockCells(): Text? {
        if (current.cellLocksThroughHal) return hal.unlockCells()
        call("mtk.cellUnlock")
        session.restartSimRadio()
        return null
    }

    override suspend fun unlockAll(): Text? {
        val empty = JSONArray()
        val hasNr = current.groups.any { it.group == Group.NR }
        call("mtk.setBands") {
            put("gsm", empty)
            put("wcdma", empty)
            put("lte", empty)
            if (hasNr) put("nr", empty)
        }
        if (current.cellLocksThroughHal) return hal.liftCellLocks()
        val heldCellLock = current.anyCellLock
        runSuspendCatching { call("mtk.cellUnlock") }
        if (heldCellLock) session.restartSimRadio()
        return null
    }

    override suspend fun resendLocks() {
        resendBandLocks()
        hal.resend()
    }

    private var cellLockIgnored: Boolean
        get() = session.prefs.getBoolean(cellLockIgnoredKey(), false)
        set(value) = session.prefs.edit().putBoolean(cellLockIgnoredKey(), value).apply()

    private fun cellLockIgnoredKey() = "mtkCellLockIgnored.${current.device?.manufacturer}.${current.device?.model}"

    companion object : BackendProfile {
        override val chipset = Chipset.MEDIATEK

        override fun suits(device: DeviceInfo): Boolean {
            val hasAtPort = device.atNodes.any { it.startsWith("/dev/radio") || it == "/dev/ttyC0" }
            return device.chipset == Chipset.MEDIATEK && (device.rilPassthrough || hasAtPort)
        }

        override fun reaches(
            device: DeviceInfo,
            shellOnly: Boolean,
        ) = !shellOnly || device.rilPassthrough

        override fun features(state: ModemState) =
            BackendFeatures(
                cellHint = Text(if (state.cellLocksThroughHal) R.string.cell_hint_mtk_hal else R.string.cell_hint_mtk_at),
                plmnHint = Text(R.string.cell_plmn_hint_android),
                pciLock = !state.cellLocksThroughHal,
            )
    }
}
