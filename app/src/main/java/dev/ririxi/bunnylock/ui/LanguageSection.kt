@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.data.AppLanguage
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

@Composable
fun LanguageSection() {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val names = remember { AppLanguage.nativeNames(context) }
    val suggested = remember { AppLanguage.suggested() }
    var current by remember { mutableStateOf(AppLanguage.current(context)) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val systemName = names.getValue(suggested.firstOrNull() ?: AppLanguage.defaultTag)
    val systemHint = stringResource(R.string.settings_language_system_hint, systemName)
    SectionHeader(stringResource(R.string.settings_language))
    Segmented {
        item { shapes ->
            SegmentedListItem(
                onClick = { picking = true },
                shapes = shapes,
                colors = segmentColors(),
                supportingContent = if (current.isEmpty()) ({ Text(systemHint) }) else null,
            ) { Text(if (current.isEmpty()) stringResource(R.string.settings_language_system) else names[current] ?: current) }
        }
    }
    if (picking) {
        LanguageSheet(names, suggested, current, systemHint, onDismiss = { picking = false }) { tag ->
            picking = false
            if (tag != current) {
                current = tag
                activity?.let { AppLanguage.set(it, tag) }
            }
        }
    }
}

private class Language(
    val tag: String,
    val nativeName: String,
    val translatedName: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(
    names: Map<String, String>,
    suggested: List<String>,
    current: String,
    systemHint: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    val shownTag = AppLanguage.shownTag(LocalConfiguration.current.locales[0])
    val languages =
        remember(names, shownTag) {
            names.mapValues { (tag, name) ->
                Language(tag, name, if (tag == shownTag) null else AppLanguage.displayName(tag, inLanguage = shownTag))
            }
        }
    val others = remember(languages, suggested) { (languages - suggested).values.sortedWith(compareBy(Collator.getInstance(Locale.ROOT), Language::nativeName)) }
    var picked by remember { mutableStateOf(false) }
    val pick: (String) -> Unit = { tag ->
        if (!picked) {
            picked = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(tag) }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item {
                Text(
                    stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                )
            }
            item {
                LanguageRow(
                    ListItemDefaults.segmentedShapes(0, 1),
                    stringResource(R.string.settings_language_system),
                    systemHint,
                    selected = current.isEmpty(),
                ) { pick("") }
            }
            languageGroup(R.string.settings_language_suggested, suggested.map(languages::getValue), current, pick)
            languageGroup(R.string.settings_language_all, others, current, pick)
            if (shownTag != AppLanguage.defaultTag) {
                item {
                    Text(
                        stringResource(R.string.settings_language_helper_english),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.languageGroup(
    @StringRes title: Int,
    languages: List<Language>,
    current: String,
    onPick: (String) -> Unit,
) {
    if (languages.isEmpty()) return
    item { SectionHeader(stringResource(title)) }
    itemsIndexed(languages, key = { _, language -> language.tag }) { index, language ->
        LanguageRow(
            ListItemDefaults.segmentedShapes(index, languages.size),
            language.nativeName,
            language.translatedName,
            selected = language.tag == current,
        ) { onPick(language.tag) }
    }
}

@Composable
private fun LanguageRow(
    shapes: ListItemShapes,
    name: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        colors = segmentColors(),
        supportingContent = if (detail !== null) ({ Text(detail) }) else null,
        trailingContent = if (selected) ({ Icon(Icons.Rounded.Check, contentDescription = null) }) else null,
    ) { Text(name) }
}
