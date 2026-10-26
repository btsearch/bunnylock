@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.modem.Backend
import dev.ririxi.bunnylock.modem.BandGroupState
import dev.ririxi.bunnylock.modem.NrMode

@Composable
fun BandsScreen(
    state: UiState,
    vm: MainViewModel,
    contentPadding: PaddingValues,
) {
    LazyColumn(contentPadding = contentPadding.withScreenMargins(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionHeader(stringResource(R.string.bands_network_mode))
            val currentMode = state.modem.netMode
            Panel {
                ToggleGrid(
                    options = state.modem.netModes,
                    isChecked = { it == currentMode },
                    label = { stringResource(it.label) },
                    onToggle = vm::setNetMode,
                    columns = if (state.modem.supports5g) 3 else 2,
                    enabled = !state.busy,
                )
                state.modem.netModeHeldBack?.let { heldBack ->
                    Text(
                        heldBack,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
                    )
                }
            }
        }

        when {
            state.modem.supportsNrMode -> {
                item {
                    SectionHeader(stringResource(R.string.bands_5g_mode))
                    ConnectedChoice(
                        options = NrMode.entries,
                        selected = state.modem.nrMode,
                        label = { stringResource(it.label) },
                        onSelect = vm::setNrMode,
                        enabled = !state.busy,
                    )
                }
            }

            state.modem.supportsNrDc -> {
                item {
                    SectionHeader("5G NSA (EN-DC)")
                    ConnectedChoice(
                        options = listOf(true, false),
                        selected = state.modem.nrDc,
                        label = { nsaOn -> stringResource(if (nsaOn) R.string.bands_nsa_on else R.string.bands_nsa_off) },
                        onSelect = vm::setNrDc,
                        enabled = !state.busy,
                    )
                }
            }
        }

        if (state.modem.device?.samsungMenu == true) {
            item {
                SectionHeader("Samsung")
                Segmented {
                    item { shapes ->
                        SegmentedListItem(
                            onClick = vm::samsungMenu,
                            shapes = shapes,
                            colors = segmentColors(),
                            leadingContent = { Icon(BunnyIcons.Bands, contentDescription = null) },
                            supportingContent = { Text(stringResource(R.string.bands_samsung_menu_hint)) },
                        ) { Text(stringResource(R.string.bands_samsung_menu)) }
                    }
                }
            }
        }

        item {
            Text(
                backendHint(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        items(state.modem.groups, key = { it.group.name }) { bandGroup ->
            BandGroup(
                bandGroup,
                selected = state.selectedBands(bandGroup),
                favorites = state.favoritesOf(bandGroup.group),
                busy = state.busy,
                onToggle = { vm.toggleBand(bandGroup.group, it) },
                onToggleFavorite = { vm.toggleFavorite(bandGroup.group, it) },
                onSelectAll = { vm.selectAll(bandGroup.group, it) },
                onLock = { vm.applyGroup(bandGroup.group) },
                onUnlock = { vm.applyGroup(bandGroup.group, unlock = true) },
            )
        }
    }
}

@Composable
private fun backendHint(state: UiState): String =
    when (state.modem.backend) {
        Backend.QUALCOMM -> stringResource(if (state.modem.persist) R.string.bands_hint_qualcomm_persist else R.string.bands_hint_qualcomm_volatile)
        Backend.MEDIATEK -> {
            val atNode = state.modem.atNode
            if (atNode.isEmpty()) stringResource(R.string.bands_hint_mediatek_auto) else stringResource(R.string.bands_hint_mediatek, atNode)
        }

        Backend.PIXEL -> stringResource(R.string.bands_hint_pixel)
        Backend.ANDROID, null -> stringResource(R.string.bands_hint_android)
    }

@Composable
private fun selectionSummary(
    bandGroup: BandGroupState,
    selected: Set<Int>,
): String {
    val count = selected.size
    val total = bandGroup.supported.size
    val pending = selected != bandGroup.allowed
    val stray = bandGroup.stray
    return when {
        stray !== null -> {
            val band = bandGroup.group.bandLabel(stray)
            stringResource(if (pending) R.string.bands_summary_stray_pending else R.string.bands_summary_stray, count, total, band)
        }

        bandGroup.locked -> stringResource(if (pending) R.string.bands_summary_locked_pending else R.string.bands_summary_locked, count, total)
        else -> stringResource(if (pending) R.string.bands_summary_pending else R.string.bands_summary, count, total)
    }
}

@Composable
private fun BandGroup(
    bandGroup: BandGroupState,
    selected: Set<Int>,
    favorites: Set<Int>,
    busy: Boolean,
    onToggle: (Int) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
) {
    var pickingFavorites by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val supportedFavorites = bandGroup.supported.filter { it in favorites }
    val canFilter = supportedFavorites.isNotEmpty() && !pickingFavorites
    val shownBands = if (canFilter && !showAll) supportedFavorites else bandGroup.supported

    fun pickFavorites(picking: Boolean) {
        pickingFavorites = picking
        showAll = false
    }

    Segmented(Modifier.padding(top = 8.dp)) {
        item { shapes ->
            SegmentedListItem(
                shapes = shapes,
                colors = segmentColors(),
                supportingContent = {
                    Text(if (pickingFavorites) stringResource(R.string.bands_pick_favorites_hint) else selectionSummary(bandGroup, selected))
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconToggleButton(checked = pickingFavorites, onCheckedChange = ::pickFavorites) {
                            Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.bands_pick_favorites))
                        }
                        LockBadge(bandGroup.locked && bandGroup.stray === null, size = 40.dp, ignored = bandGroup.stray !== null)
                    }
                },
            ) {
                Text(bandGroup.group.label, style = MaterialTheme.typography.titleMediumEmphasized)
            }
        }
        item { shapes ->
            Panel(shape = shapes.shape) {
                ToggleGrid(
                    options = shownBands,
                    isChecked = { if (pickingFavorites) it in favorites else it in selected },
                    label = { bandGroup.group.bandLabel(it) },
                    onToggle = if (pickingFavorites) onToggleFavorite else onToggle,
                    columns = 4,
                    detail = { bandGroup.group.frequencyName(it) },
                    accent = pickingFavorites,
                )
                if (canFilter) {
                    TextButton(
                        onClick = { showAll = !showAll },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            if (showAll) {
                                stringResource(R.string.bands_show_favorites)
                            } else {
                                pluralStringResource(R.plurals.bands_show_all, bandGroup.supported.size, bandGroup.supported.size)
                            },
                        )
                    }
                }
            }
        }
        item { shapes ->
            Panel(shape = shapes.shape, padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (pickingFavorites) {
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { pickFavorites(false) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.bands_done)) }
                    } else {
                        TextButton(onClick = { onSelectAll(true) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.bands_all)) }
                        TextButton(onClick = { onSelectAll(false) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.bands_none)) }
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = onUnlock, enabled = !busy && bandGroup.locked, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.common_unlock))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onLock, enabled = !busy && selected.isNotEmpty(), shapes = ButtonDefaults.shapes()) {
                            Icon(BunnyIcons.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.common_lock))
                        }
                    }
                }
            }
        }
    }
}
