@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.data.AppLanguage
import dev.ririxi.bunnylock.modem.Backend
import dev.ririxi.bunnylock.modem.DeviceInfo
import dev.ririxi.bunnylock.modem.andList
import dev.ririxi.bunnylock.modem.string
import dev.ririxi.bunnylock.ui.theme.dynamicColorAvailable

private val AT_COMMAND_PRESETS = listOf("ATI", "AT+EPBSE?", "AT+EPBSEH?", "AT+EMMCHLCK?", "AT+GOOGGETNV=\"NR.CONFIG.MODE\"")

@Composable
fun ToolsScreen(
    state: UiState,
    vm: MainViewModel,
    contentPadding: PaddingValues,
) {
    var atCommand by rememberSaveable { mutableStateOf("") }
    // The QMI console starts on NAS "get system info" (service 3, message 0x004D), a read-only request.
    var qmiService by rememberSaveable { mutableStateOf("3") }
    var qmiMessage by rememberSaveable { mutableStateOf("004D") }
    var qmiPayload by rememberSaveable { mutableStateOf("") }
    var checkedServicesOpen by rememberSaveable { mutableStateOf(false) }
    val arrowAngle by animateFloatAsState(if (checkedServicesOpen) 180f else 0f, label = "arrow")

    LazyColumn(contentPadding = contentPadding.withScreenMargins(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionHeader(stringResource(R.string.tools_backend))
            Segmented {
                Backend.entries.forEach { backend ->
                    val selected = state.modem.backend == backend
                    val needsRoot = !state.modem.allows(backend)
                    item { shapes ->
                        val hint =
                            when {
                                needsRoot -> stringResource(R.string.tools_backend_needs_root)
                                state.modem.autoBackend == backend -> stringResource(R.string.tools_backend_detected)
                                else -> null
                            }
                        SegmentedListItem(
                            selected = selected,
                            onClick = { vm.setBackend(backend) },
                            enabled = !needsRoot,
                            shapes = shapes,
                            colors = segmentColors(),
                            supportingContent = if (hint !== null) ({ Text(hint) }) else null,
                            trailingContent = if (selected) ({ Icon(Icons.Rounded.Check, contentDescription = null) }) else null,
                        ) { Text(stringResource(backend.label)) }
                    }
                }
                if (state.modem.backend == Backend.QUALCOMM) {
                    item {
                        SwitchItem(
                            it,
                            stringResource(R.string.tools_persist),
                            stringResource(R.string.tools_persist_hint),
                            checked = state.modem.persist,
                            onCheckedChange = vm::setPersist,
                        )
                    }
                }
            }
        }

        if (dynamicColorAvailable) {
            item {
                SectionHeader(stringResource(R.string.tools_appearance))
                Segmented {
                    item {
                        SwitchItem(
                            it,
                            stringResource(R.string.tools_dynamic_color),
                            stringResource(R.string.tools_dynamic_color_hint),
                            checked = state.dynamicColor,
                            onCheckedChange = vm::setDynamicColor,
                        )
                    }
                }
            }
        }

        item { LanguageSection() }

        item {
            SectionHeader(stringResource(R.string.tools_updates))
            Segmented {
                item {
                    SwitchItem(
                        it,
                        stringResource(R.string.tools_check_updates),
                        stringResource(R.string.tools_check_updates_hint),
                        checked = state.checkUpdates,
                        onCheckedChange = vm::setCheckUpdates,
                    )
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.tools_radio))
            Segmented {
                item {
                    ActionItem(
                        it,
                        stringResource(R.string.tools_restart_radio),
                        stringResource(R.string.tools_restart_radio_hint),
                        Icons.Rounded.Refresh,
                        enabled = !state.busy,
                        onClick = vm::radioCycle,
                    )
                }
                if (state.modem.backend == Backend.QUALCOMM) {
                    item {
                        ActionItem(
                            it,
                            stringResource(R.string.tools_reattach),
                            stringResource(R.string.tools_reattach_hint),
                            Icons.Rounded.Refresh,
                            enabled = !state.busy,
                            onClick = vm::reattach,
                        )
                    }
                }
                item {
                    ActionItem(
                        it,
                        stringResource(R.string.tools_reboot_modem),
                        stringResource(R.string.tools_reboot_modem_hint),
                        Icons.Rounded.Warning,
                        enabled = !state.busy,
                        onClick = vm::rebootModem,
                    )
                }
                if (state.modem.device?.samsungMenu == true) {
                    item {
                        ActionItem(
                            it,
                            stringResource(R.string.tools_samsung_menu),
                            stringResource(R.string.tools_samsung_menu_hint),
                            Icons.Rounded.Settings,
                            enabled = true,
                            onClick = vm::samsungMenu,
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.tools_at_console))
            Panel {
                OutlinedTextField(
                    state.modem.atNode,
                    vm::setAtNode,
                    label = { Text(stringResource(R.string.tools_at_port)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(atNodesHint(state.modem.device)) },
                )
                OutlinedTextField(
                    atCommand,
                    { atCommand = it },
                    label = { Text(stringResource(R.string.tools_at_command)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = { if (atCommand.isNotBlank()) vm.sendAt(atCommand.trim()) },
                            enabled = !state.busy && state.modem.atNode.isNotBlank(),
                        ) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.common_send)) }
                    },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AT_COMMAND_PRESETS.forEach { preset ->
                        FilledTonalButton(onClick = { atCommand = preset }, shapes = ButtonDefaults.shapes()) { Text(preset) }
                    }
                }
            }
        }

        if (state.modem.backend == Backend.QUALCOMM) {
            item {
                SectionHeader(stringResource(R.string.tools_qmi_console))
                Panel {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            qmiService,
                            { qmiService = it.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.tools_qmi_service)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            qmiMessage,
                            { qmiMessage = it.filter(Char::isLetterOrDigit) },
                            label = { Text(stringResource(R.string.tools_qmi_message)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        qmiPayload,
                        { qmiPayload = it },
                        label = { Text(stringResource(R.string.tools_qmi_payload)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(stringResource(R.string.tools_qmi_nas_hint)) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val service = qmiService.toIntOrNull()
                                    val message = qmiMessage.toIntOrNull(16)
                                    if (service !== null && message !== null) vm.sendQmi(service, message, qmiPayload)
                                },
                                enabled = !state.busy,
                            ) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.common_send)) }
                        },
                    )
                }
            }
        }

        if (state.toolOutput.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.tools_output), Modifier.weight(1f))
                    TextButton(onClick = vm::clearOutput) { Text(stringResource(R.string.tools_clear)) }
                }
                MonospacePanel(state.toolOutput)
            }
        }

        item {
            val device = state.modem.device
            SectionHeader(stringResource(R.string.common_device))
            Segmented {
                item {
                    ActionItem(
                        it,
                        stringResource(R.string.common_copy_diagnostics),
                        stringResource(R.string.tools_copy_diagnostics_hint),
                        Icons.Rounded.Share,
                        enabled = true,
                        onClick = vm::copyDiagnostics,
                    )
                }
                item { InfoItem(it, stringResource(R.string.tools_access), state.modem.access?.label.orEmpty()) }
                if (device !== null) {
                    item {
                        val status = device.mtkOemHookStatus?.let { text -> LocalContext.current.string(text) }
                        InfoItem(it, stringResource(R.string.tools_passthrough), status.orEmpty())
                    }
                    if (device.mtkOemHookChecked.isNotEmpty()) {
                        item { shapes ->
                            val checkedServicesText: @Composable () -> Unit = {
                                val context = LocalContext.current
                                Text(device.mtkOemHookChecked.joinToString("\n") { context.string(it) }, style = MaterialTheme.typography.bodySmall)
                            }
                            SegmentedListItem(
                                onClick = { checkedServicesOpen = !checkedServicesOpen },
                                shapes = shapes,
                                colors = segmentColors(),
                                supportingContent = if (checkedServicesOpen) checkedServicesText else null,
                                trailingContent = {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription =
                                            stringResource(if (checkedServicesOpen) R.string.tools_collapse else R.string.tools_expand),
                                        modifier = Modifier.rotate(arrowAngle),
                                    )
                                },
                            ) {
                                Text(
                                    stringResource(R.string.tools_services_checked, device.mtkOemHookChecked.size),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item {
                    val vendor = device?.vendorLabel?.let { label -> stringResource(label) }
                    InfoItem(it, stringResource(R.string.common_modem), vendor.orEmpty())
                }
                item { InfoItem(it, "SoC", device?.socModel.orEmpty()) }
                item { InfoItem(it, stringResource(R.string.common_platform), device?.platform.orEmpty()) }
                item { InfoItem(it, "RIL", device?.ril.orEmpty()) }
                item { InfoItem(it, stringResource(R.string.common_baseband), device?.baseband.orEmpty()) }
                item { InfoItem(it, "Android SDK", device?.sdk?.toString().orEmpty()) }
                item {
                    InfoItem(
                        it,
                        stringResource(R.string.tools_qmi_over_qrtr),
                        stringResource(if (device?.qrtr == true) R.string.tools_yes else R.string.tools_no),
                    )
                }
                item {
                    InfoItem(
                        it,
                        stringResource(R.string.tools_hal_channel_lock),
                        stringResource(if (device?.aospChannels == true) R.string.tools_present else R.string.tools_absent),
                    )
                }
            }
        }

        if (state.modem.log.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.tools_log))
                MonospacePanel(state.modem.log.joinToString("\n"))
            }
        }

        item {
            SectionHeader(stringResource(R.string.tools_about))
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
                Text(stringResource(R.string.tools_author), style = MaterialTheme.typography.bodyMedium)
                if (version.isNotEmpty()) {
                    Text(
                        stringResource(R.string.tools_version, version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AnimatedVisibility(visible = fact !== null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(BunnyIcons.Bunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Text(
                    stringResource(R.string.tools_bunny_fact, fact.orEmpty()),
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun atNodesHint(device: DeviceInfo?): String {
    val rilPorts = if (device?.mtkOemHook == true) listOf(stringResource(R.string.tools_at_ports_ril)) else emptyList()
    val nodes = device?.atNodes.orEmpty() + rilPorts
    return if (nodes.isEmpty()) {
        stringResource(R.string.tools_at_ports_none)
    } else {
        stringResource(R.string.tools_at_ports_found, LocalContext.current.andList(nodes))
    }
}

@Composable
private fun LanguageSection() {
    val activity = LocalActivity.current
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppLanguage.current(context)) }
    val shownInEnglish = LocalConfiguration.current.locales[0].language == "en"
    SectionHeader(stringResource(R.string.tools_language))
    Segmented {
        (listOf("") + AppLanguage.tags).forEach { tag ->
            item { shapes ->
                val selected = language == tag
                val hint =
                    when {
                        selected && !shownInEnglish -> stringResource(R.string.tools_language_helper_english)
                        tag.isEmpty() -> stringResource(R.string.tools_language_system_hint)
                        else -> null
                    }
                SegmentedListItem(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            language = tag
                            activity?.let { AppLanguage.set(it, tag) }
                        }
                    },
                    shapes = shapes,
                    colors = segmentColors(),
                    supportingContent = if (hint !== null) ({ Text(hint) }) else null,
                    trailingContent = if (selected) ({ Icon(Icons.Rounded.Check, contentDescription = null) }) else null,
                ) { Text(languageName(tag)) }
            }
        }
    }
}

@Composable
private fun languageName(tag: String): String =
    when (tag) {
        "" -> stringResource(R.string.tools_language_system)
        "en" -> stringResource(R.string.tools_language_en)
        "pl" -> stringResource(R.string.tools_language_pl)
        "zh-CN" -> stringResource(R.string.tools_language_zh_cn)
        else -> tag
    }

@Composable
private fun ActionItem(
    shapes: ListItemShapes,
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        shapes = shapes,
        colors = segmentColors(),
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = { Text(subtitle) },
    ) { Text(title) }
}

@Composable
private fun SwitchItem(
    shapes: ListItemShapes,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = shapes,
        colors = segmentColors(),
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) { Text(title) }
}

@Composable
private fun MonospacePanel(text: String) {
    Panel {
        SelectionContainer {
            Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
