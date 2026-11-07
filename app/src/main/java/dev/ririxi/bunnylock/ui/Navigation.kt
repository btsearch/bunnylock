@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TabRail(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    windowInsets: WindowInsets,
) {
    WideNavigationRail(windowInsets = windowInsets) {
        Tab.entries.forEach { tab ->
            WideNavigationRailItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                railExpanded = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingTabBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val shapes = ToggleButtonDefaults.shapesFor(ToggleButtonDefaults.MinHeight).copy(checkedShape = CircleShape)
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.selectableGroup(),
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            val label = stringResource(tab.label)
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onSelect(tab) },
                shapes = shapes,
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = scheme.onSurfaceVariant,
                        checkedContainerColor = scheme.secondaryContainer,
                        checkedContentColor = scheme.onSecondaryContainer,
                    ),
                contentPadding = PaddingValues(horizontal = if (isSelected) 12.dp else 8.dp),
                modifier =
                    Modifier
                        .then(if (isSelected) Modifier.weight(1f, fill = false) else Modifier)
                        .clearAndSetSemantics {
                            role = Role.Tab
                            this.selected = isSelected
                            contentDescription = label
                            onClick(label) {
                                onSelect(tab)
                                true
                            }
                        },
            ) {
                Icon(tab.icon, contentDescription = null)
                AnimatedVisibility(
                    visible = isSelected,
                    enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                    exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                ) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
