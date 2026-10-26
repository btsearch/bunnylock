@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.core.Bands
import dev.ririxi.bunnylock.data.Cell
import dev.ririxi.bunnylock.data.Rat
import dev.ririxi.bunnylock.modem.Backend
import dev.ririxi.bunnylock.modem.Group
import dev.ririxi.bunnylock.modem.LockRat
import dev.ririxi.bunnylock.modem.formatPlmn

private val NumberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)

@Composable
fun CellScreen(
    state: UiState,
    vm: MainViewModel,
    contentPadding: PaddingValues,
) {
    LazyColumn(contentPadding = contentPadding.withScreenMargins(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionHeader(stringResource(R.string.cell_network_header))
            Segmented {
                item {
                    LockStatusItem(
                        it,
                        locked = state.modem.anyPlmnLock,
                        title = plmnLockTitle(state),
                        description = registrationText(state),
                    )
                }
                item { Panel(shape = it.shape) { PlmnForm(state, vm) } }
            }
        }

        item {
            SectionHeader(stringResource(R.string.cell_current_lock))
            Segmented {
                item {
                    LockStatusItem(
                        it,
                        locked = state.modem.anyCellLock && state.modem.strayChannel === null,
                        ignored = state.modem.strayChannel !== null,
                        title = cellLockTitle(state),
                        description = cellLockDescription(state),
                    )
                }
                state.modem.lteLock?.let { lock -> item { InfoItem(it, "LTE", lock) } }
                state.modem.nrLock?.let { lock -> item { InfoItem(it, "NR", lock) } }
                state.modem.legacyCellLock?.let { lock -> item { InfoItem(it, "2G/3G", lock) } }
                if (state.modem.anyCellLock) {
                    item { shapes ->
                        SegmentedListItem(
                            onClick = vm::unlockCells,
                            enabled = !state.busy,
                            shapes = shapes,
                            colors = segmentColors(),
                            leadingContent = {
                                Icon(BunnyIcons.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                        ) { Text(stringResource(R.string.cell_clear_lock), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.cell_lock_to))
            CellLockForm(state, vm)
        }

        item {
            val lockableCells =
                state.modem.cells
                    .filter { it.rat == Rat.LTE || it.rat == Rat.NR }
                    .sortedWith(compareByDescending<Cell> { it.registered }.thenByDescending { it.rat.ordinal }.thenBy { it.arfcn }.thenBy { it.pci })
            SectionHeader(stringResource(R.string.cell_visible))
            if (lockableCells.isEmpty()) {
                Panel { SleepyBunny(stringResource(R.string.cell_none_found), stringResource(R.string.cell_none_found_hint)) }
            } else {
                Segmented { lockableCells.forEach { cell -> item { shapes -> CellItem(shapes, cell) { vm.fillFrom(cell) } } } }
            }
        }
    }
}

@Composable
private fun LockStatusItem(
    shapes: ListItemShapes,
    locked: Boolean,
    title: String,
    description: String,
    ignored: Boolean = false,
) {
    SegmentedListItem(
        shapes = shapes,
        colors = segmentColors(),
        leadingContent = { LockBadge(locked, size = 44.dp, ignored = ignored) },
        supportingContent = { Text(description) },
    ) { Text(title, style = MaterialTheme.typography.titleMediumEmphasized) }
}

@Composable
private fun plmnLockTitle(state: UiState): String {
    val lock = state.modem.plmnLock ?: return stringResource(R.string.cell_plmn_automatic)
    // The lock is a PLMN such as "262 01", or a ready-made phrase when the locked network isn't known.
    return if (lock.firstOrNull()?.isDigit() == true) stringResource(R.string.cell_plmn_locked_to, lock) else lock
}

@Composable
private fun registrationText(state: UiState): String {
    val plmn = state.modem.registeredPlmn ?: return stringResource(R.string.cell_not_registered)
    val name = state.modem.registeredName ?: return stringResource(R.string.cell_connected, formatPlmn(plmn))
    return stringResource(R.string.cell_connected_named, formatPlmn(plmn), name)
}

@Composable
private fun cellLockTitle(state: UiState): String =
    when {
        state.modem.strayChannel !== null -> stringResource(R.string.cell_lock_ignored)
        state.modem.anyCellLock -> stringResource(R.string.cell_lock_locked)
        else -> stringResource(R.string.cell_lock_none)
    }

@Composable
private fun cellLockDescription(state: UiState): String {
    val stray = state.modem.strayChannel
    return when {
        stray !== null -> stringResource(R.string.cell_lock_stray, stray)
        state.modem.anyCellLock -> stringResource(R.string.cell_lock_pinned)
        else -> stringResource(R.string.cell_lock_free)
    }
}

@Composable
private fun PlmnForm(
    state: UiState,
    vm: MainViewModel,
) {
    val plmnComplete = state.plmnMcc.length == 3 && state.plmnMnc.length >= 2
    val plmnPicks = state.modem.plmnPicks(LocalContext.current)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            state.plmnMcc,
            { vm.setPlmnForm(mcc = it) },
            label = { Text("MCC") },
            singleLine = true,
            keyboardOptions = NumberKeyboard,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            state.plmnMnc,
            { vm.setPlmnForm(mnc = it) },
            label = { Text("MNC") },
            singleLine = true,
            keyboardOptions = NumberKeyboard,
            modifier = Modifier.weight(1f),
        )
    }
    if (plmnPicks.isNotEmpty()) {
        ToggleGrid(
            options = plmnPicks,
            isChecked = { (plmn, _) -> plmn == state.plmnMcc + state.plmnMnc },
            label = { (plmn, name) -> "${formatPlmn(plmn)} $name" },
            onToggle = { (plmn, _) -> vm.pickPlmn(plmn) },
            columns = 2,
        )
    }
    Text(
        stringResource(if (state.modem.backend == Backend.QUALCOMM) R.string.cell_plmn_hint_qualcomm else R.string.cell_plmn_hint_android),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = vm::unlockPlmn,
            enabled = !state.busy && state.modem.anyPlmnLock,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.cell_plmn_automatic)) }
        Button(
            onClick = vm::lockPlmn,
            enabled = !state.busy && plmnComplete,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.weight(1f),
        ) {
            Icon(BunnyIcons.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.common_lock))
        }
    }
}

@Composable
private fun cellLockHint(state: UiState): String =
    stringResource(
        when {
            state.modem.backend == Backend.QUALCOMM -> R.string.cell_hint_qualcomm
            state.modem.mtkAtCellLock -> R.string.cell_hint_mtk_at
            state.modem.backend == Backend.MEDIATEK -> R.string.cell_hint_mtk_hal
            else -> R.string.cell_hint_hal
        },
    )

@Composable
private fun lteChannelHint(earfcn: Int): String {
    val band = Bands.lteBandOf(earfcn)
    val megahertz = Bands.lteFreqMhz(earfcn)
    if (band === null || megahertz === null) return stringResource(R.string.common_unknown_band)
    return stringResource(R.string.cell_lte_channel, band, megahertz)
}

private fun pciInput(text: String): String = text.filter { it.isDigit() || it == ',' || it == ' ' }

/**
 * Applies a typed NR-ARFCN: the chosen band is kept while it still contains the channel, otherwise the first
 * band that does is picked. The subcarrier spacing follows that band's default unless the user picked one by hand.
 */
private fun CellForm.withNrArfcn(text: String): CellForm {
    val digits = text.filter(Char::isDigit)
    val candidates = digits.toIntOrNull()?.let { Bands.nrBandsOf(it) } ?: emptyList()
    val band = nrBand?.takeIf { it in candidates } ?: candidates.firstOrNull()
    val spacing = if (band === null || nrScsPicked) nrScs else Bands.nrDefaultScs(band)
    return copy(nrArfcn = digits, nrBand = band, nrScs = spacing)
}

@Composable
private fun CellLockForm(
    state: UiState,
    vm: MainViewModel,
) {
    val form = state.form
    val locksLte = state.lockRat != LockRat.NR
    val locksNr = state.lockRat != LockRat.LTE
    Panel {
        if (state.modem.supports5g) {
            ConnectedChoice(
                options = LockRat.entries,
                selected = form.rat,
                label = { stringResource(it.label) },
                onSelect = { selected -> vm.updateForm { copy(rat = selected) } },
            )
        }

        if (locksLte) LteLockFields(state, vm)
        if (locksNr) NrLockFields(state, vm)

        Text(cellLockHint(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val buttonHeight = ButtonDefaults.MediumContainerHeight
        Button(
            onClick = vm::lockCells,
            enabled = !state.busy,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
            modifier = Modifier.fillMaxWidth().height(buttonHeight).padding(top = 4.dp),
        ) {
            Icon(BunnyIcons.Lock, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.common_lock), style = ButtonDefaults.textStyleFor(buttonHeight))
        }
    }
}

@Composable
private fun LteLockFields(
    state: UiState,
    vm: MainViewModel,
) {
    val form = state.form
    Text(if (state.lockRat == LockRat.NSA) stringResource(R.string.cell_lte_anchor) else "LTE", style = MaterialTheme.typography.titleSmallEmphasized)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            form.lteEarfcn,
            { text -> vm.updateForm { copy(lteEarfcn = text.filter(Char::isDigit)) } },
            label = { Text("EARFCN") },
            singleLine = true,
            keyboardOptions = NumberKeyboard,
            modifier = Modifier.weight(1f),
            supportingText = { form.lteEarfcn.toIntOrNull()?.let { Text(lteChannelHint(it)) } },
        )
        OutlinedTextField(
            form.ltePcis,
            { text -> vm.updateForm { copy(ltePcis = pciInput(text)) } },
            label = { Text(stringResource(R.string.cell_pcis)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            enabled = state.modem.supportsPciLock,
        )
    }
}

@Composable
private fun NrLockFields(
    state: UiState,
    vm: MainViewModel,
) {
    val form = state.form
    Text(if (state.lockRat == LockRat.NSA) stringResource(R.string.cell_nr_scg) else "5G NR", style = MaterialTheme.typography.titleSmallEmphasized)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            form.nrArfcn,
            { text -> vm.updateForm { withNrArfcn(text) } },
            label = { Text("NR-ARFCN") },
            singleLine = true,
            keyboardOptions = NumberKeyboard,
            modifier = Modifier.weight(1f),
            supportingText = { form.nrArfcn.toIntOrNull()?.let { Text("%.2f MHz".format(Bands.nrFreqMhz(it))) } },
        )
        OutlinedTextField(
            form.nrPcis,
            { text -> vm.updateForm { copy(nrPcis = pciInput(text)) } },
            label = { Text(stringResource(R.string.cell_pcis)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            enabled = state.modem.supportsPciLock,
        )
    }
    if (form.nrCandidates.size > 1) {
        Text(stringResource(R.string.cell_band), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ToggleGrid(
            options = form.nrCandidates,
            isChecked = { it == form.nrBand },
            label = { "n$it" },
            onToggle = { band -> vm.updateForm { copy(nrBand = band, nrScs = Bands.nrDefaultScs(band), nrScsPicked = false) } },
            columns = 4,
            detail = { Group.NR.frequencyName(it) },
        )
    }
    if (state.modem.backend == Backend.QUALCOMM) {
        Text(stringResource(R.string.cell_scs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ConnectedChoice(
            options = listOf(15, 30, 60, 120),
            selected = form.nrScs,
            label = { it.toString() },
            onSelect = { spacing -> vm.updateForm { copy(nrScs = spacing, nrScsPicked = true) } },
        )
    }
}
