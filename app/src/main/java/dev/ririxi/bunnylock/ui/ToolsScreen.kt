@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.modem.DeviceInfo
import dev.ririxi.bunnylock.modem.ImsBlock
import dev.ririxi.bunnylock.modem.ImsFeature
import dev.ririxi.bunnylock.modem.ImsSetting
import dev.ririxi.bunnylock.modem.andList
import dev.ririxi.bunnylock.modem.string

private const val LOG_MIME_TYPE = "text/x-log"

private val AT_COMMAND_PRESETS = listOf("ATI", "AT+EPBSE?", "AT+EPBSEH?", "AT+EMMCHLCK?", "AT+SPLBAND=0", "AT+GOOGGETNV=\"NR.CONFIG.MODE\"")

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
    val saveLog = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(LOG_MIME_TYPE)) { uri -> uri?.let(vm::saveLog) }

    LaunchedEffect(Unit) { vm.refreshIms() }

    LazyColumn(contentPadding = contentPadding.withScreenMargins(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val carrierAggregation = state.modem.carrierAggregation
        if (carrierAggregation !== null) {
            item {
                SectionHeader(simSectionHeader(state, R.string.tools_network, R.string.tools_network_sim))
                Segmented {
                    item {
                        SwitchItem(
                            it,
                            stringResource(R.string.tools_ca),
                            stringResource(R.string.tools_ca_hint),
                            checked = carrierAggregation,
                            onCheckedChange = vm::setCarrierAggregation,
                            enabled = !state.busy && !state.modem.refreshing,
                        )
                    }
                }
            }
        }

        item { CallingSection(state, vm) }

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
                if (state.modem.features.reattach) {
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

        if (state.modem.features.qmiConsole) {
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
                        InfoItem(it, stringResource(R.string.tools_passthrough), textOf(device.rilPassthroughStatus))
                    }
                    if (device.rilPassthroughChecked.isNotEmpty()) {
                        item { shapes ->
                            val checkedServicesText: @Composable () -> Unit = {
                                val context = LocalContext.current
                                Text(device.rilPassthroughChecked.joinToString("\n") { context.string(it) }, style = MaterialTheme.typography.bodySmall)
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
                                    stringResource(R.string.tools_services_checked, device.rilPassthroughChecked.size),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item {
                    val chipset = device?.let { info -> stringResource(info.chipset.label) }.orEmpty()
                    InfoItem(it, stringResource(R.string.common_modem), chipset)
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
                Row(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.tools_log), Modifier.weight(1f))
                    val saveLogDescription = stringResource(R.string.tools_save_log_description)
                    TextButton(
                        onClick = { saveLog.launch(vm.logFileName()) },
                        modifier = Modifier.semantics { contentDescription = saveLogDescription },
                    ) { Text(stringResource(R.string.tools_save_log)) }
                }
                MonospacePanel(state.modem.log.joinToString("\n"))
            }
        }
    }
}

@Composable
private fun CallingSection(
    state: UiState,
    vm: MainViewModel,
) {
    SectionHeader(simSectionHeader(state, R.string.tools_calling, R.string.tools_calling_sim))
    Segmented {
        ImsFeature.entries.forEach { feature ->
            item { shapes ->
                val setting = state.modem.ims[feature]
                SwitchItem(
                    shapes,
                    feature.label,
                    imsHint(feature, setting),
                    checked = (setting as? ImsSetting.Readable)?.enabled == true,
                    onCheckedChange = { vm.setIms(feature, it) },
                    enabled = !state.busy && !state.modem.refreshing && setting is ImsSetting.Readable,
                )
            }
        }
    }
}

@Composable
private fun simSectionHeader(
    state: UiState,
    @StringRes title: Int,
    @StringRes titleWithSim: Int,
): String = if (state.modem.sims.size > 1) stringResource(titleWithSim, state.modem.sim) else stringResource(title)

@Composable
private fun imsHint(
    feature: ImsFeature,
    setting: ImsSetting?,
): String =
    when {
        setting === null -> stringResource(R.string.tools_ims_unknown)
        setting is ImsSetting.Unreadable -> stringResource(R.string.tools_ims_error, setting.error)
        setting is ImsSetting.Readable && setting.blocked == ImsBlock.NOT_OFFERED -> stringResource(R.string.tools_ims_not_offered)
        setting is ImsSetting.Readable && setting.blocked == ImsBlock.LOCKED -> stringResource(R.string.tools_ims_locked)
        feature == ImsFeature.VOLTE -> stringResource(R.string.tools_volte_hint)
        else -> stringResource(R.string.tools_vowifi_hint)
    }

@Composable
private fun atNodesHint(device: DeviceInfo?): String {
    val rilPorts = if (device?.rilPassthrough == true) listOf(stringResource(R.string.tools_at_ports_ril)) else emptyList()
    val nodes = device?.atNodes.orEmpty() + rilPorts
    return if (nodes.isEmpty()) {
        stringResource(R.string.tools_at_ports_none)
    } else {
        stringResource(R.string.tools_at_ports_found, LocalContext.current.andList(nodes))
    }
}

@Composable
private fun MonospacePanel(text: String) {
    Panel {
        SelectionContainer {
            Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
