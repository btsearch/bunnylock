@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.data.Carrier
import dev.ririxi.bunnylock.data.Cell
import dev.ririxi.bunnylock.data.Rat
import dev.ririxi.bunnylock.data.Signal
import dev.ririxi.bunnylock.modem.ModemState
import dev.ririxi.bunnylock.modem.formatPlmn
import dev.ririxi.bunnylock.ui.theme.LocalLockGreen
import dev.ririxi.bunnylock.ui.theme.LocalSignalScale

private val RSRP_STEPS = listOf(-80, -90, -100)
private val PLAIN_DBM_STEPS = listOf(-70, -85, -100)
private val RSRQ_STEPS = listOf(-10, -15, -20)
private val SINR_STEPS = listOf(20, 13, 0)

@Composable
fun HomeScreen(
    state: UiState,
    vm: MainViewModel,
    contentPadding: PaddingValues,
    goTo: (Tab) -> Unit,
) {
    LazyColumn(contentPadding = contentPadding.withScreenMargins(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Hero(state, onUnlock = vm::unlockEverything, onBands = { goTo(Tab.BANDS) }, onCell = { goTo(Tab.CELL) }) }

        val carriers = state.modem.carriers
        item {
            SectionHeader(stringResource(R.string.home_connection))
            if (carriers.isEmpty()) Panel { SleepyBunny(stringResource(R.string.home_no_serving_cell)) } else ConnectionCard(state.modem)
        }

        if (carriers.isNotEmpty()) {
            item {
                val anchoredOnLte = carriers.any { it.primary && it.rat == Rat.LTE }
                SectionHeader(pluralStringResource(R.plurals.home_carriers_header, carriers.size))
                Segmented(Modifier.animateContentSize()) {
                    carriers.forEach { carrier -> item { shapes -> CarrierItem(shapes, carrier, anchoredOnLte) } }
                }
            }
        }

        item {
            val device = state.modem.device
            val chipset = device?.let { stringResource(it.vendorLabel) }.orEmpty()
            val deviceName = "${device?.manufacturer.orEmpty()} ${device?.model.orEmpty()}".trim()
            val platform =
                listOfNotNull(
                    device?.socModel?.ifEmpty { null },
                    device?.platform?.ifEmpty { null },
                ).joinToString(", ")
            SectionHeader(stringResource(R.string.common_modem))
            Segmented {
                item { InfoItem(it, stringResource(R.string.home_chipset), chipset) }
                item { InfoItem(it, stringResource(R.string.common_device), deviceName) }
                item { InfoItem(it, stringResource(R.string.common_platform), platform) }
                item { InfoItem(it, stringResource(R.string.common_baseband), device?.baseband.orEmpty()) }
            }
        }
    }
}

@Composable
private fun Hero(
    state: UiState,
    onUnlock: () -> Unit,
    onBands: () -> Unit,
    onCell: () -> Unit,
) {
    val locked = state.modem.anyBandLock || state.modem.anyCellLock || state.modem.anyPlmnLock
    val strayNote =
        state.modem.strayBand?.let { stringResource(R.string.home_stray_band, it) }
            ?: state.modem.strayChannel?.let { stringResource(R.string.home_stray_channel, it) }
    val lockHeld = locked && strayNote === null
    val green = LocalLockGreen.current
    val containerColor by animateColorAsState(
        if (lockHeld) {
            green.copy(alpha = 0.2f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "hero",
    )
    Surface(color = containerColor, shape = RoundedCornerShape(36.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            when {
                                strayNote !== null -> R.string.home_state_ignored
                                locked -> R.string.home_state_locked
                                else -> R.string.home_state_free
                            },
                        ),
                        style = MaterialTheme.typography.displaySmallEmphasized.copy(fontWeight = FontWeight.Bold),
                        color =
                            when {
                                strayNote !== null -> MaterialTheme.colorScheme.error
                                locked -> green
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                    )
                    Text(
                        strayNote ?: stringResource(pinnedSummary(state)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BouncyLockBadge(locked = locked, ignored = strayNote !== null, userAction = state.busy, size = 88.dp)
            }

            val lockRows = activeLockRows(state)
            if (lockRows.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lockRows.forEach { (label, value) ->
                        Row {
                            Text(label, style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.width(96.dp))
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val buttonHeight = ButtonDefaults.MediumContainerHeight
                if (locked) {
                    Button(
                        onClick = onUnlock,
                        enabled = !state.busy,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(buttonHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                    ) {
                        Icon(
                            BunnyIcons.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.common_unlock_all), style = ButtonDefaults.textStyleFor(buttonHeight), maxLines = 1)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onBands, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.common_bands))
                    }
                    FilledTonalButton(onClick = onCell, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.common_cell))
                    }
                }
            }
        }
    }
}

@StringRes
private fun pinnedSummary(state: UiState): Int {
    val bands = state.modem.anyBandLock
    val cell = state.modem.anyCellLock
    val network = state.modem.anyPlmnLock
    return when {
        bands && cell && network -> R.string.home_pinned_all
        bands && cell -> R.string.home_pinned_bands_cell
        bands && network -> R.string.home_pinned_bands_network
        cell && network -> R.string.home_pinned_cell_network
        bands -> R.string.home_pinned_bands
        cell -> R.string.home_pinned_cell
        network -> R.string.home_pinned_network
        else -> R.string.home_pinned_nothing
    }
}

@Composable
private fun activeLockRows(state: UiState): List<Pair<String, String>> =
    buildList {
        state.modem.groups.filter { it.locked }.forEach { bandGroup ->
            val bands = bandGroup.current!!.sorted().joinToString(", ") { bandGroup.group.bandLabel(it) }
            add(bandGroup.group.label to bands)
        }
        state.modem.plmnLock?.let { add(stringResource(R.string.common_network) to it) }
        state.modem.lteLock?.let { add(stringResource(R.string.home_row_lte_cell) to it) }
        state.modem.nrLock?.let { add(stringResource(R.string.home_row_nr_cell) to it) }
        state.modem.legacyCellLock?.let { add(stringResource(R.string.home_row_legacy_cell) to it) }
    }

private fun channelName(rat: Rat) =
    when (rat) {
        Rat.LTE -> "EARFCN"
        Rat.WCDMA -> "UARFCN"
        else -> "ARFCN"
    }

private fun megahertz(khz: Int): String = if (khz % 1000 == 0) "${khz / 1000}" else "%.1f".format(khz / 1000.0)

@Composable
private fun techSummary(carriers: List<Carrier>): String {
    val lteCount = carriers.count { it.rat == Rat.LTE }
    val technologies =
        listOfNotNull(
            when {
                lteCount > 1 -> "LTE-A"
                lteCount == 1 -> "LTE"
                else -> null
            },
            when {
                carriers.none { it.rat == Rat.NR } -> null
                lteCount > 0 -> "5G NSA"
                else -> "5G SA"
            },
            "3G".takeIf { carriers.any { it.rat == Rat.WCDMA } },
            "2G".takeIf { carriers.any { it.rat == Rat.GSM } },
        )
    val count = pluralStringResource(R.plurals.home_carrier_count, carriers.size, carriers.size)
    return stringResource(R.string.home_tech_summary, technologies.joinToString(" + "), count)
}

private fun totalBandwidth(carriers: List<Carrier>): String? {
    val known = carriers.mapNotNull { it.bandwidthKhz }
    if (known.isEmpty()) return null
    val more = if (known.size < carriers.size) "+" else ""
    return "${megahertz(known.sum())}$more MHz"
}

@Composable
private fun signalColor(
    value: Int,
    steps: List<Int>,
): Color {
    val step = steps.indexOfFirst { value >= it }
    return LocalSignalScale.current[if (step < 0) steps.size else step]
}

// 2G and 3G report RSSI, which runs higher than RSRP.
private fun dbmSteps(rat: Rat) = if (rat == Rat.LTE || rat == Rat.NR) RSRP_STEPS else PLAIN_DBM_STEPS

@Composable
private fun ratColor(rat: Rat): Color =
    when (rat) {
        Rat.NR -> MaterialTheme.colorScheme.primary
        Rat.LTE -> MaterialTheme.colorScheme.tertiary
        Rat.WCDMA -> MaterialTheme.colorScheme.secondary
        Rat.GSM -> MaterialTheme.colorScheme.outline
    }

@Composable
private fun ConnectionCard(modem: ModemState) {
    val carriers = modem.carriers
    val network = modem.registeredName ?: modem.registeredPlmn?.let(::formatPlmn) ?: stringResource(R.string.home_unknown_network)
    val primaryRat = carriers.first().rat
    val signals = modem.signals.filter { it.rsrp !== null || it.rsrq !== null || it.sinr !== null }.sortedBy { it.rat != primaryRat }
    Panel(Modifier.animateContentSize(), padding = PaddingValues(20.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(network, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(techSummary(carriers), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            totalBandwidth(carriers)?.let { total ->
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedContent(
                        targetState = total,
                        transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                        label = "total bandwidth",
                    ) { shown -> Text(shown, style = MaterialTheme.typography.headlineSmallEmphasized) }
                    Text(
                        stringResource(R.string.home_total_bandwidth),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (carriers.all { it.bandwidthKhz !== null }) BandwidthBar(carriers)
        signals.forEach { signal -> SignalRow(signal, showRat = signals.size > 1) }
    }
}

@Composable
private fun BandwidthBar(carriers: List<Carrier>) {
    Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val motion = MaterialTheme.motionScheme
        carriers.forEach { carrier ->
            val width = carrier.bandwidthKhz ?: return@forEach
            key(carrier.rat, carrier.arfcn, carrier.pci) {
                val shownWidth = remember { Animatable(0f) }
                LaunchedEffect(width) { shownWidth.animateTo(width.toFloat(), motion.defaultSpatialSpec()) }
                val color by animateColorAsState(ratColor(carrier.rat).copy(alpha = if (carrier.primary) 1f else 0.55f), label = "carrier")
                Box(
                    Modifier
                        .weight(shownWidth.value.coerceAtLeast(1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun SignalRow(
    signal: Signal,
    showRat: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showRat) RatBadge(signal.rat, size = 32.dp)
        signal.rsrp?.let { Metric("RSRP", "$it dBm", signalColor(it, RSRP_STEPS)) }
        signal.rsrq?.let { Metric("RSRQ", "$it dB", signalColor(it, RSRQ_STEPS)) }
        signal.sinr?.let { Metric("SINR", "$it dB", signalColor(it, SINR_STEPS)) }
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    color: Color,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMediumEmphasized, color = animateColorAsState(color, label = label).value)
    }
}

@Composable
private fun Tag(
    text: String,
    container: Color,
    content: Color,
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun SignalPill(
    dbm: Int,
    rat: Rat,
) {
    val color by animateColorAsState(signalColor(dbm, dbmSteps(rat)), label = "signal")
    Surface(color = color.copy(alpha = 0.16f), contentColor = color, shape = RoundedCornerShape(50)) {
        Text("$dbm dBm", style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun RoleTag(
    carrier: Carrier,
    anchoredOnLte: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    when {
        carrier.primary -> Tag(stringResource(R.string.home_tag_primary), colors.secondaryContainer, colors.onSecondaryContainer)
        carrier.rat == Rat.NR && anchoredOnLte -> Tag("5G NSA", colors.primaryContainer, colors.onPrimaryContainer)
        else -> Tag(stringResource(R.string.home_tag_secondary), colors.surfaceContainerHighest, colors.onSurfaceVariant)
    }
}

@Composable
private fun CellTag(cell: Cell) {
    val colors = MaterialTheme.colorScheme
    when {
        cell.note == "NSA SCG" -> Tag("5G NSA", colors.primaryContainer, colors.onPrimaryContainer)
        cell.registered -> Tag(stringResource(R.string.home_tag_serving), colors.secondaryContainer, colors.onSecondaryContainer)
    }
}

private fun radioDetails(
    rat: Rat,
    arfcn: Long?,
    pci: Int?,
    bandwidthKhz: Int?,
): String =
    listOfNotNull(
        arfcn?.let { "${channelName(rat)} $it" },
        pci?.let { "PCI $it" },
        bandwidthKhz?.let { "${megahertz(it)} MHz" },
    ).joinToString(" · ")

@Composable
private fun RadioRow(
    shapes: ListItemShapes,
    rat: Rat,
    title: String,
    frequencyName: String?,
    details: String,
    rsrp: Int?,
    tag: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val leading: @Composable () -> Unit = { RatBadge(rat) }
    val trailing: @Composable () -> Unit = { rsrp?.let { SignalPill(it, rat) } }
    val supporting: (@Composable () -> Unit)? = if (details.isEmpty()) null else ({ Text(details) })
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLargeEmphasized)
            frequencyName?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            tag()
        }
    }
    if (onClick !== null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            colors = segmentColors(),
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            content = content,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            colors = segmentColors(),
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            content = content,
        )
    }
}

@Composable
private fun CarrierItem(
    shapes: ListItemShapes,
    carrier: Carrier,
    anchoredOnLte: Boolean,
) = RadioRow(
    shapes,
    carrier.rat,
    if (carrier.band !== null) carrier.bandLabel else stringResource(R.string.common_unknown_band),
    carrier.frequencyName,
    radioDetails(carrier.rat, carrier.arfcn, carrier.pci, carrier.bandwidthKhz),
    carrier.rsrp,
    tag = { RoleTag(carrier, anchoredOnLte) },
)

@Composable
fun CellItem(
    shapes: ListItemShapes,
    cell: Cell,
    onClick: (() -> Unit)? = null,
) = RadioRow(
    shapes,
    cell.rat,
    if (cell.band !== null) cell.bandLabel else stringResource(R.string.common_unknown_band),
    cell.frequencyName,
    radioDetails(cell.rat, cell.arfcn, cell.pci.takeIf { it >= 0 }, cell.bandwidthKhz),
    cell.rsrp,
    tag = { CellTag(cell) },
    onClick = onClick,
)
