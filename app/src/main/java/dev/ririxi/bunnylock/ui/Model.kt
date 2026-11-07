package dev.ririxi.bunnylock.ui

import dev.ririxi.bunnylock.core.Bands
import dev.ririxi.bunnylock.data.Release
import dev.ririxi.bunnylock.modem.BandGroupState
import dev.ririxi.bunnylock.modem.Group
import dev.ririxi.bunnylock.modem.LockRat
import dev.ririxi.bunnylock.modem.ModemState

val Group.favoritesKey: String
    get() =
        when (this) {
            Group.LTE -> "lte"
            Group.NR, Group.NR_SA, Group.NR_NSA -> "nr"
            Group.WCDMA -> "wcdma"
            Group.GSM -> "gsm"
        }

data class CellForm(
    val rat: LockRat = LockRat.LTE,
    val lteEarfcn: String = "",
    val ltePcis: String = "",
    val nrArfcn: String = "",
    val nrPcis: String = "",
    /** Subcarrier spacing in kHz. */
    val nrScs: Int = 30,
    val nrBand: Int? = null,
    /** The user chose [nrScs] by hand, so typing a channel no longer replaces it with the band's usual spacing. */
    val nrScsPicked: Boolean = false,
) {
    val nrCandidates: List<Int> get() = nrArfcn.toIntOrNull()?.let { Bands.nrBandsOf(it) } ?: emptyList()
}

data class UiState(
    val modem: ModemState = ModemState(),
    val busy: Boolean = false,
    val bandSelections: Map<Group, Set<Int>> = emptyMap(),
    val form: CellForm = CellForm(),
    val plmnMcc: String = "",
    val plmnMnc: String = "",
    val dynamicColor: Boolean = true,
    val checkUpdates: Boolean = true,
    /** Band locks turn off every network without a lock of its own. */
    val exclusiveBandLock: Boolean = false,
    val toolOutput: String = "",
    val update: Release? = null,
    val favoriteBands: Map<String, Set<Int>> = emptyMap(),
    val showAllBands: Set<Group> = emptySet(),
) {
    fun favoritesOf(group: Group): Set<Int> = favoriteBands[group.favoritesKey].orEmpty()

    val lockRat get() = if (modem.supports5g) form.rat else LockRat.LTE

    fun isOff(group: Group) = exclusiveBandLock && modem.isOff(group)

    fun canTurnOff(group: Group) = exclusiveBandLock && group.canTurnOff

    fun appliedBands(groupState: BandGroupState): Set<Int> = if (isOff(groupState.group)) emptySet() else groupState.allowed

    // Locked bands and picks outside the favorites stay visible, so a checked band is never hidden from Lock.
    fun visibleBands(groupState: BandGroupState): List<Int> {
        if (!showsFavorites(groupState)) return groupState.supported
        val favorites = favoritesOf(groupState.group)
        val lockedBands = if (groupState.locked) appliedBands(groupState) else emptySet()
        val keptVisible = bandSelections[groupState.group].orEmpty() + lockedBands
        return groupState.supported.filter { it in favorites || it in keptVisible }
    }

    fun startingBands(groupState: BandGroupState): Set<Int> {
        val applied = appliedBands(groupState)
        return if (showsFavorites(groupState) && !groupState.locked) applied intersect favoritesOf(groupState.group) else applied
    }

    fun selectedBands(groupState: BandGroupState): Set<Int> = bandSelections[groupState.group] ?: startingBands(groupState)

    fun isPending(groupState: BandGroupState): Boolean = selectedBands(groupState) != startingBands(groupState)

    val pendingGroups: List<Group> get() = modem.groups.filter(::isPending).map { it.group }

    val changedBandCount: Int
        get() =
            modem.groups.sumOf { groupState ->
                val selected = selectedBands(groupState)
                val starting = startingBands(groupState)
                (selected - starting).size + (starting - selected).size
            }

    fun hasFavorites(groupState: BandGroupState): Boolean = groupState.supported.any { it in favoritesOf(groupState.group) }

    private fun showsFavorites(groupState: BandGroupState): Boolean = groupState.group !in showAllBands && hasFavorites(groupState)
}
