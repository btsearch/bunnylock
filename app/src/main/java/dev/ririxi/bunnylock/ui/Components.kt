@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.DefaultFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.data.Rat
import dev.ririxi.bunnylock.ui.theme.LocalLockGreen

private val WordmarkFont =
    FontFamily(
        Font(
            resId = R.font.google_sans_flex_rounded,
            weight = FontWeight.ExtraBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(FontWeight.ExtraBold.weight)),
        ),
    )

/** Inherits the surrounding text style so it scales with the flexible app bar. */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val green = LocalLockGreen.current
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append("bunny") }
            withStyle(SpanStyle(color = green)) { append("lock") }
        },
        modifier = modifier,
        style = style.copy(fontFamily = WordmarkFont, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    )
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
}

val PanelColor: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer

@Composable
fun segmentColors(): ListItemColors = ListItemDefaults.segmentedColors(containerColor = PanelColor)

class SegmentScope {
    internal val items = ArrayList<@Composable (ListItemShapes) -> Unit>()

    fun item(content: @Composable (ListItemShapes) -> Unit) {
        items += content
    }
}

/**
 * A group of list rows with a small gap between them. The rows are collected first because each one's
 * [ListItemShapes] depend on its position: only the outer corners of the group are fully rounded.
 */
@Composable
fun Segmented(
    modifier: Modifier = Modifier,
    build: SegmentScope.() -> Unit,
) {
    val scope = SegmentScope().apply(build)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        scope.items.forEachIndexed { index, content -> content(ListItemDefaults.segmentedShapes(index, scope.items.size)) }
    }
}

/** Inside [Segmented], pass the item's `shape` so the corners match its position in the group. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = shape, color = PanelColor) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun InfoItem(
    shapes: ListItemShapes,
    label: String,
    value: String,
) {
    val shown = value.ifEmpty { stringResource(R.string.common_unknown) }
    val valueText: @Composable () -> Unit = {
        Text(shown, style = MaterialTheme.typography.bodyMediumEmphasized, color = MaterialTheme.colorScheme.onSurface)
    }
    // The trailing slot is measured first, so a long value there would squeeze the label to one letter per line.
    val stacked = shown.length > MAX_TRAILING_VALUE_LENGTH
    SegmentedListItem(
        shapes = shapes,
        colors = segmentColors(),
        supportingContent = if (stacked) valueText else null,
        trailingContent = if (stacked) null else valueText,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val MAX_TRAILING_VALUE_LENGTH = 22

@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun <T> ToggleGrid(
    options: List<T>,
    isChecked: (T) -> Boolean,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    enabled: Boolean = true,
    detail: (T) -> String? = { null },
    accent: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val colors =
        if (accent) {
            ToggleButtonDefaults.colors(
                containerColor = scheme.surfaceContainerHighest,
                checkedContainerColor = scheme.tertiary,
                checkedContentColor = scheme.onTertiary,
            )
        } else {
            ToggleButtonDefaults.colors(containerColor = scheme.surfaceContainerHighest)
        }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    ToggleButton(
                        checked = isChecked(option),
                        onCheckedChange = { onToggle(option) },
                        enabled = enabled,
                        colors = colors,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            detail(option)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalContentColor.current.copy(alpha = 0.6f),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val BADGE_FULL_TURN_MILLIS = 14_000
private const val BADGE_WOBBLE_MILLIS = 1_400
private const val BADGE_WOBBLE_DEGREES = 10f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockBadge(
    locked: Boolean,
    size: Dp = 48.dp,
    spin: Boolean = false,
    ignored: Boolean = false,
) {
    val green = LocalLockGreen.current
    val colors = MaterialTheme.colorScheme
    val style =
        when {
            ignored -> LockBadgeStyle(MaterialShapes.SoftBurst, colors.error.copy(alpha = 0.2f), colors.error, BunnyIcons.LockAlert, R.string.shell_lock_badge_ignored)
            locked -> LockBadgeStyle(MaterialShapes.Cookie9Sided, green.copy(alpha = 0.22f), green, BunnyIcons.Lock, R.string.shell_lock_badge_locked)
            else -> LockBadgeStyle(MaterialShapes.Cookie6Sided, colors.surfaceContainerHighest, colors.onSurfaceVariant, BunnyIcons.LockOpen, R.string.shell_lock_badge_unlocked)
        }
    val containerColor by animateColorAsState(style.container, label = "badge")
    val angle =
        when {
            !spin -> {
                0f
            }

            ignored -> {
                rememberInfiniteTransition(label = "wobble")
                    .animateFloat(
                        initialValue = -BADGE_WOBBLE_DEGREES,
                        targetValue = BADGE_WOBBLE_DEGREES,
                        animationSpec = infiniteRepeatable(tween(BADGE_WOBBLE_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "angle",
                    ).value
            }

            locked -> {
                rememberInfiniteTransition(label = "spin")
                    .animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(BADGE_FULL_TURN_MILLIS, easing = LinearEasing), RepeatMode.Restart),
                        label = "angle",
                    ).value
            }

            else -> {
                0f
            }
        }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .rotate(angle)
                .clip(style.shape.toShape())
                .background(containerColor),
        )
        Icon(style.icon, contentDescription = stringResource(style.description), tint = style.content, modifier = Modifier.size(size * 0.45f))
    }
}

private class LockBadgeStyle(
    val shape: RoundedPolygon,
    val container: Color,
    val content: Color,
    val icon: ImageVector,
    @StringRes val description: Int,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RatBadge(
    rat: Rat,
    size: Dp = 40.dp,
) {
    val colors = MaterialTheme.colorScheme
    val style =
        when (rat) {
            Rat.NR -> RatBadgeStyle(MaterialShapes.Cookie9Sided, colors.primaryContainer, colors.onPrimaryContainer, "5G")
            Rat.LTE -> RatBadgeStyle(MaterialShapes.Clover4Leaf, colors.tertiaryContainer, colors.onTertiaryContainer, "4G")
            Rat.WCDMA -> RatBadgeStyle(MaterialShapes.Pentagon, colors.secondaryContainer, colors.onSecondaryContainer, "3G")
            Rat.GSM -> RatBadgeStyle(MaterialShapes.Circle, colors.surfaceContainerHighest, colors.onSurfaceVariant, "2G")
        }
    Box(Modifier.size(size).clip(style.shape.toShape()).background(style.container), contentAlignment = Alignment.Center) {
        Text(style.label, color = style.content, style = MaterialTheme.typography.labelLargeEmphasized)
    }
}

private class RatBadgeStyle(
    val shape: RoundedPolygon,
    val container: Color,
    val content: Color,
    val label: String,
)

/** Icons missing from material-icons-core. */
object BunnyIcons {
    private fun icon(
        name: String,
        fillType: PathFillType = DefaultFillType,
        block: PathBuilder.() -> Unit,
    ) = ImageVector
        .Builder(name, 24.dp, 24.dp, 24f, 24f)
        .path(fill = SolidColor(Color.Black), pathFillType = fillType, pathBuilder = block)
        .build()

    val Lock: ImageVector by lazy {
        icon("Lock") {
            moveTo(18f, 8f)
            horizontalLineToRelative(-1f)
            verticalLineTo(6f)
            curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
            reflectiveCurveTo(7f, 3.24f, 7f, 6f)
            verticalLineToRelative(2f)
            horizontalLineTo(6f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(10f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(12f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(10f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(12f, 17f)
            curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
            reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
            reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
            reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
            close()
            moveTo(15.1f, 8f)
            horizontalLineTo(8.9f)
            verticalLineTo(6f)
            curveToRelative(0f, -1.71f, 1.39f, -3.1f, 3.1f, -3.1f)
            curveToRelative(1.71f, 0f, 3.1f, 1.39f, 3.1f, 3.1f)
            verticalLineToRelative(2f)
            close()
        }
    }

    val LockAlert: ImageVector by lazy {
        icon("LockAlert", PathFillType.EvenOdd) {
            moveTo(18f, 8f)
            horizontalLineToRelative(-1f)
            verticalLineTo(6f)
            curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
            reflectiveCurveTo(7f, 3.24f, 7f, 6f)
            verticalLineToRelative(2f)
            horizontalLineTo(6f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(10f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(12f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(10f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(15.1f, 8f)
            horizontalLineTo(8.9f)
            verticalLineTo(6f)
            curveToRelative(0f, -1.71f, 1.39f, -3.1f, 3.1f, -3.1f)
            curveToRelative(1.71f, 0f, 3.1f, 1.39f, 3.1f, 3.1f)
            verticalLineToRelative(2f)
            close()
            moveTo(11f, 11.7f)
            arcToRelative(1f, 1f, 0f, false, true, 2f, 0f)
            verticalLineToRelative(3.5f)
            arcToRelative(1f, 1f, 0f, false, true, -2f, 0f)
            close()
            moveTo(10.9f, 18.3f)
            arcToRelative(1.1f, 1.1f, 0f, true, true, 2.2f, 0f)
            arcToRelative(1.1f, 1.1f, 0f, true, true, -2.2f, 0f)
            close()
        }
    }

    val LockOpen: ImageVector by lazy {
        icon("LockOpen") {
            moveTo(12f, 17f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
            close()
            moveTo(18f, 8f)
            horizontalLineToRelative(-1f)
            verticalLineTo(6f)
            curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
            reflectiveCurveTo(7f, 3.24f, 7f, 6f)
            horizontalLineToRelative(1.9f)
            curveToRelative(0f, -1.71f, 1.39f, -3.1f, 3.1f, -3.1f)
            curveToRelative(1.71f, 0f, 3.1f, 1.39f, 3.1f, 3.1f)
            verticalLineToRelative(2f)
            horizontalLineTo(6f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(10f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(12f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(10f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(18f, 20f)
            horizontalLineTo(6f)
            verticalLineTo(10f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(10f)
            close()
        }
    }

    val Bunny: ImageVector by lazy {
        icon("Bunny", PathFillType.EvenOdd) {
            moveTo(7f, 5.5f)
            arcToRelative(2f, 4f, 0f, true, true, 4f, 0f)
            arcToRelative(2f, 4f, 0f, true, true, -4f, 0f)
            close()
            moveTo(13f, 5.5f)
            arcToRelative(2f, 4f, 0f, true, true, 4f, 0f)
            arcToRelative(2f, 4f, 0f, true, true, -4f, 0f)
            close()
            moveTo(6f, 15.5f)
            arcToRelative(6f, 6f, 0f, true, true, 12f, 0f)
            arcToRelative(6f, 6f, 0f, true, true, -12f, 0f)
            close()
            moveTo(8.8f, 15f)
            arcToRelative(1f, 1f, 0f, true, true, 2f, 0f)
            arcToRelative(1f, 1f, 0f, true, true, -2f, 0f)
            close()
            moveTo(13.2f, 15f)
            arcToRelative(1f, 1f, 0f, true, true, 2f, 0f)
            arcToRelative(1f, 1f, 0f, true, true, -2f, 0f)
            close()
        }
    }

    val Bands: ImageVector by lazy {
        icon("Bands") {
            moveTo(3f, 17f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(-2f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(10f)
            verticalLineTo(5f)
            horizontalLineTo(3f)
            close()
            moveTo(13f, 21f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(2f)
            close()
            moveTo(7f, 9f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineTo(9f)
            horizontalLineTo(7f)
            close()
            moveTo(21f, 13f)
            verticalLineToRelative(-2f)
            horizontalLineTo(11f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(10f)
            close()
            moveTo(15f, 9f)
            horizontalLineToRelative(2f)
            verticalLineTo(7f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineToRelative(-4f)
            verticalLineTo(3f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(6f)
            close()
        }
    }

    val Tower: ImageVector by lazy {
        icon("Tower") {
            moveTo(12f, 11f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            curveToRelative(0f, 0.74f, 0.4f, 1.38f, 1f, 1.72f)
            verticalLineTo(22f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-7.28f)
            curveToRelative(0.6f, -0.35f, 1f, -0.98f, 1f, -1.72f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(6.34f, 7.34f)
            lineTo(4.93f, 5.93f)
            curveTo(3.12f, 7.74f, 2f, 10.24f, 2f, 13f)
            reflectiveCurveToRelative(1.12f, 5.26f, 2.93f, 7.07f)
            lineToRelative(1.41f, -1.41f)
            curveTo(4.89f, 17.22f, 4f, 15.22f, 4f, 13f)
            reflectiveCurveToRelative(0.89f, -4.22f, 2.34f, -5.66f)
            close()
            moveTo(19.07f, 5.93f)
            lineToRelative(-1.41f, 1.41f)
            curveTo(19.11f, 8.78f, 20f, 10.78f, 20f, 13f)
            reflectiveCurveToRelative(-0.89f, 4.22f, -2.34f, 5.66f)
            lineToRelative(1.41f, 1.41f)
            curveTo(20.88f, 18.26f, 22f, 15.76f, 22f, 13f)
            reflectiveCurveToRelative(-1.12f, -5.26f, -2.93f, -7.07f)
            close()
            moveTo(9.17f, 10.17f)
            lineTo(7.76f, 8.76f)
            curveTo(6.67f, 9.85f, 6f, 11.35f, 6f, 13f)
            reflectiveCurveToRelative(0.67f, 3.15f, 1.76f, 4.24f)
            lineToRelative(1.41f, -1.41f)
            curveTo(8.45f, 15.1f, 8f, 14.1f, 8f, 13f)
            reflectiveCurveToRelative(0.45f, -2.1f, 1.17f, -2.83f)
            close()
            moveTo(16.24f, 8.76f)
            lineToRelative(-1.41f, 1.41f)
            curveTo(15.55f, 10.9f, 16f, 11.9f, 16f, 13f)
            reflectiveCurveToRelative(-0.45f, 2.1f, -1.17f, 2.83f)
            lineToRelative(1.41f, 1.41f)
            curveTo(17.33f, 16.15f, 18f, 14.65f, 18f, 13f)
            reflectiveCurveToRelative(-0.67f, -3.15f, -1.76f, -4.24f)
            close()
        }
    }
}
