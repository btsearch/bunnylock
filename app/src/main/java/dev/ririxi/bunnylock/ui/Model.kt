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
    /** Locking a group with no bands picked takes its network out of the network mode. */
    val noBandsTurnsOff: Boolean = false,
    val toolOutput: String = "",
    val update: Release? = null,
    val favoriteBands: Map<String, Set<Int>> = emptyMap(),
) {
    fun favoritesOf(group: Group): Set<Int> = favoriteBands[group.favoritesKey].orEmpty()

    val lockRat get() = if (modem.supports5g) form.rat else LockRat.LTE

    fun isOff(group: Group) = noBandsTurnsOff && modem.isOff(group)

    fun canTurnOff(group: Group) = noBandsTurnsOff && group.canTurnOff

    fun appliedBands(groupState: BandGroupState): Set<Int> = if (isOff(groupState.group)) emptySet() else groupState.allowed

    fun selectedBands(groupState: BandGroupState): Set<Int> = bandSelections[groupState.group] ?: appliedBands(groupState)
}
