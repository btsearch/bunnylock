@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.ui.theme.dynamicColorAvailable

@Composable
fun SettingsScreen(
    state: UiState,
    vm: MainViewModel,
    contentPadding: PaddingValues,
) {
    LazyColumn(contentPadding = contentPadding.withScreenMargins(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionHeader(stringResource(R.string.settings_backend))
            Segmented {
                state.modem.offeredBackends.forEach { backend ->
                    val selected = state.modem.backend == backend
                    val unavailable = !state.modem.allows(backend)
                    // With root, an unavailable backend lacks something on the phone itself, so root wouldn't help.
                    val needsRoot = unavailable && (state.modem.limited || state.modem.device === null)
                    item { shapes ->
                        val hint =
                            when {
                                needsRoot -> stringResource(R.string.settings_backend_needs_root)
                                state.modem.autoBackend == backend -> stringResource(R.string.settings_backend_detected)
                                else -> null
                            }
                        SegmentedListItem(
                            selected = selected,
                            onClick = { vm.setBackend(backend) },
                            enabled = !unavailable,
                            shapes = shapes,
                            colors = segmentColors(),
                            supportingContent = if (hint !== null) ({ Text(hint) }) else null,
                            trailingContent = if (selected) ({ Icon(Icons.Rounded.Check, contentDescription = null) }) else null,
                        ) { Text(stringResource(backend.label)) }
                    }
                }
                if (state.modem.features.persistSwitch) {
                    item {
                        SwitchItem(
                            it,
                            stringResource(R.string.settings_persist),
                            stringResource(R.string.settings_persist_hint),
                            checked = state.modem.persist,
                            onCheckedChange = vm::setPersist,
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.settings_bands))
            Segmented {
                item {
                    SwitchItem(
                        it,
                        stringResource(R.string.settings_exclusive_lock),
                        stringResource(R.string.settings_exclusive_lock_hint),
                        checked = state.exclusiveBandLock,
                        onCheckedChange = vm::setExclusiveBandLock,
                    )
                }
                if (state.modem.features.forgetNeedsChannels) {
                    item {
                        ActionItem(
                            it,
                            stringResource(R.string.settings_forget_needs_channels),
                            stringResource(R.string.settings_forget_needs_channels_hint),
                            Icons.Rounded.Refresh,
                            enabled = !state.busy,
                            onClick = vm::forgetNeedsChannels,
                        )
                    }
                }
                val keptOffLines =
                    state.modem.groups.mapNotNull { groupState ->
                        val group = groupState.group
                        groupState.keptOff.takeIf { it.isNotEmpty() }?.joinToString(", ", "${group.label}: ", transform = group::bandLabel)
                    }
                if (keptOffLines.isNotEmpty()) {
                    item {
                        ActionItem(
                            it,
                            stringResource(R.string.settings_forget_kept_off_bands),
                            stringResource(R.string.settings_forget_kept_off_bands_hint),
                            Icons.Rounded.Refresh,
                            enabled = !state.busy,
                            onClick = vm::forgetKeptOffBands,
                            detail = keptOffLines.joinToString("\n"),
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.settings_appearance))
            Text(
                stringResource(R.string.settings_theme),
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Segmented {
                ThemeMode.entries.forEach { mode ->
                    item { shapes ->
                        val selected = state.themeMode == mode
                        SegmentedListItem(
                            selected = selected,
                            onClick = { vm.setThemeMode(mode) },
                            shapes = shapes,
                            colors = segmentColors(),
                            trailingContent = if (selected) ({ Icon(Icons.Rounded.Check, contentDescription = null) }) else null,
                        ) { Text(stringResource(mode.label)) }
                    }
                }
                if (dynamicColorAvailable) {
                    item {
                        SwitchItem(
                            it,
                            stringResource(R.string.settings_dynamic_color),
                            stringResource(R.string.settings_dynamic_color_hint),
                            checked = state.dynamicColor,
                            onCheckedChange = vm::setDynamicColor,
                        )
                    }
                }
            }
        }

        item { LanguageSection() }

        item {
            SectionHeader(stringResource(R.string.settings_updates))
            Segmented {
                item {
                    SwitchItem(
                        it,
                        stringResource(R.string.settings_check_updates),
                        stringResource(R.string.settings_check_updates_hint),
                        checked = state.checkUpdates,
                        onCheckedChange = vm::setCheckUpdates,
                    )
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.settings_about))
            About()
        }
    }
}

private const val TAPS_FOR_FACT = 5

@Composable
private fun About() {
    val context = LocalContext.current
    val version =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        }
    val facts = stringArrayResource(R.array.bunny_facts)
    var taps by rememberSaveable { mutableIntStateOf(0) }
    val fact = if (taps >= TAPS_FOR_FACT) facts[(taps - TAPS_FOR_FACT) % facts.size] else null
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LockBadge(locked = fact !== null, size = 56.dp, spin = true)
            Column(Modifier.weight(1f)) {
                Wordmark(
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    modifier =
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { taps++ },
                )
                Text(stringResource(R.string.settings_author), style = MaterialTheme.typography.bodyMedium)
                if (version.isNotEmpty()) {
                    Text(
                        stringResource(R.string.settings_version, version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = fact !== null,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(BunnyIcons.Bunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Text(
                    stringResource(R.string.settings_bunny_fact, fact.orEmpty()),
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
