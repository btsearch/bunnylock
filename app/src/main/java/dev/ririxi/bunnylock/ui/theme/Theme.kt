@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.ririxi.bunnylock.R

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF415F91),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6E3FF),
        onPrimaryContainer = Color(0xFF001B3E),
        secondary = Color(0xFF565F71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDAE2F9),
        onSecondaryContainer = Color(0xFF131C2B),
        tertiary = Color(0xFF705575),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFAD8FD),
        onTertiaryContainer = Color(0xFF28132E),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF9F9FF),
        onBackground = Color(0xFF191C20),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF191C20),
        surfaceVariant = Color(0xFFE0E2EC),
        onSurfaceVariant = Color(0xFF44474E),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C6D0),
        inverseSurface = Color(0xFF2E3036),
        inverseOnSurface = Color(0xFFF0F0F7),
        inversePrimary = Color(0xFFAAC7FF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF3F3FA),
        surfaceContainer = Color(0xFFEDEDF4),
        surfaceContainerHigh = Color(0xFFE7E8EE),
        surfaceContainerHighest = Color(0xFFE2E2E9),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFAAC7FF),
        onPrimary = Color(0xFF0A305F),
        primaryContainer = Color(0xFF284777),
        onPrimaryContainer = Color(0xFFD6E3FF),
        secondary = Color(0xFFBEC6DC),
        onSecondary = Color(0xFF283141),
        secondaryContainer = Color(0xFF3E4759),
        onSecondaryContainer = Color(0xFFDAE2F9),
        tertiary = Color(0xFFDDBCE0),
        onTertiary = Color(0xFF3F2844),
        tertiaryContainer = Color(0xFF573E5C),
        onTertiaryContainer = Color(0xFFFAD8FD),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF111318),
        onBackground = Color(0xFFE2E2E9),
        surface = Color(0xFF111318),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF44474E),
        onSurfaceVariant = Color(0xFFC4C6D0),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44474E),
        inverseSurface = Color(0xFFE2E2E9),
        inverseOnSurface = Color(0xFF2E3036),
        inversePrimary = Color(0xFF415F91),
        surfaceContainerLowest = Color(0xFF0C0E13),
        surfaceContainerLow = Color(0xFF191C20),
        surfaceContainer = Color(0xFF1D2024),
        surfaceContainerHigh = Color(0xFF282A2F),
        surfaceContainerHighest = Color(0xFF33353A),
    )

private val LockGreenOnLight = Color(0xFF1FA34A)
private val LockGreenOnDark = Color(0xFF5BE584)

val LocalLockGreen = staticCompositionLocalOf { LockGreenOnLight }

private val SignalScaleOnLight = listOf(LockGreenOnLight, Color(0xFF6E8E00), Color(0xFFB86E00), Color(0xFFCC3B2C))
private val SignalScaleOnDark = listOf(LockGreenOnDark, Color(0xFFC3E35A), Color(0xFFFFB95C), Color(0xFFFF8A7E))

val LocalSignalScale = staticCompositionLocalOf { SignalScaleOnLight }

val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

internal val GoogleSansFlex =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold, FontWeight.ExtraBold).map { weight ->
            Font(R.font.google_sans_flex_rounded, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
        },
    )

private fun TextStyle.withGoogleSansFlex() = copy(fontFamily = GoogleSansFlex)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val BunnyTypography =
    Typography().run {
        copy(
            displayLarge = displayLarge.withGoogleSansFlex(),
            displayMedium = displayMedium.withGoogleSansFlex(),
            displaySmall = displaySmall.withGoogleSansFlex(),
            headlineLarge = headlineLarge.withGoogleSansFlex(),
            headlineMedium = headlineMedium.withGoogleSansFlex(),
            headlineSmall = headlineSmall.withGoogleSansFlex(),
            titleLarge = titleLarge.withGoogleSansFlex(),
            titleMedium = titleMedium.withGoogleSansFlex(),
            titleSmall = titleSmall.withGoogleSansFlex(),
            displayLargeEmphasized = displayLargeEmphasized.withGoogleSansFlex(),
            displayMediumEmphasized = displayMediumEmphasized.withGoogleSansFlex(),
            displaySmallEmphasized = displaySmallEmphasized.withGoogleSansFlex(),
            headlineLargeEmphasized = headlineLargeEmphasized.withGoogleSansFlex(),
            headlineMediumEmphasized = headlineMediumEmphasized.withGoogleSansFlex(),
            headlineSmallEmphasized = headlineSmallEmphasized.withGoogleSansFlex(),
            titleLargeEmphasized = titleLargeEmphasized.withGoogleSansFlex(),
            titleMediumEmphasized = titleMediumEmphasized.withGoogleSansFlex(),
            titleSmallEmphasized = titleSmallEmphasized.withGoogleSansFlex(),
        )
    }

@Composable
fun BunnyTheme(
    dynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme =
        when {
            dynamicColor && dynamicColorAvailable && darkTheme -> dynamicDarkColorScheme(context)
            dynamicColor && dynamicColorAvailable -> dynamicLightColorScheme(context)
            darkTheme -> DarkColors
            else -> LightColors
        }
    CompositionLocalProvider(
        LocalLockGreen provides if (darkTheme) LockGreenOnDark else LockGreenOnLight,
        LocalSignalScale provides if (darkTheme) SignalScaleOnDark else SignalScaleOnLight,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = BunnyTypography,
            content = content,
        )
    }
}
