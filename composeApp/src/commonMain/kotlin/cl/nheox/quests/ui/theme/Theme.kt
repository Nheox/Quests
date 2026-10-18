package cl.nheox.quests.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import cl.nheox.quests.domain.Clock
import cl.nheox.quests.i18n.Strings
import cl.nheox.quests.i18n.StringsEn

/**
 * The semantic colours the UI actually names.
 *
 * The screens never reach into [Palette] for a raw value; they ask for the
 * thing's role. That is what lets a light theme exist at all without auditing
 * every call site, and it is what keeps a "muted label" the same grey
 * everywhere.
 */
@Immutable
public data class QuestColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val hover: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val accent: Color,
    val accentDim: Color,
    val progression: Color,
    val progressionDim: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val isDark: Boolean,
) {
    public val onAccent: Color get() = if (isDark) Palette.ink else Color.White
}

public val DarkColors: QuestColors = QuestColors(
    background = Palette.ink,
    surface = Palette.inkRaised,
    card = Palette.inkCard,
    hover = Palette.inkHover,
    border = Palette.inkBorder,
    borderStrong = Palette.inkBorderStrong,
    text = Palette.textOnInk,
    textMuted = Palette.textOnInkMuted,
    textFaint = Palette.textOnInkFaint,
    accent = Palette.teal,
    accentDim = Palette.tealDim,
    progression = Palette.gold,
    progressionDim = Palette.goldDim,
    success = Palette.success,
    danger = Palette.danger,
    warning = Palette.warning,
    isDark = true,
)

public val LightColors: QuestColors = QuestColors(
    background = Palette.paper,
    surface = Palette.paperRaised,
    card = Palette.paperCard,
    hover = Palette.paperHover,
    border = Palette.paperBorder,
    borderStrong = Palette.paperBorderStrong,
    text = Palette.textOnPaper,
    textMuted = Palette.textOnPaperMuted,
    textFaint = Palette.textOnPaperFaint,
    accent = Color(0xFF17847C),
    accentDim = Color(0xFF9ED8D3),
    progression = Color(0xFFA9761C),
    progressionDim = Color(0xFFE6CE9A),
    success = Color(0xFF2F8354),
    danger = Color(0xFFB8373A),
    warning = Color(0xFFA9661F),
    isDark = false,
)

public val LocalQuestColors: ProvidableCompositionLocal<QuestColors> = staticCompositionLocalOf { DarkColors }
public val LocalStrings: ProvidableCompositionLocal<Strings> = staticCompositionLocalOf<Strings> { StringsEn }
public val LocalClock: ProvidableCompositionLocal<Clock> = staticCompositionLocalOf { Clock.System }

/**
 * Type scale.
 *
 * Deliberately kept to a system font stack rather than a bundled typeface: a
 * 40 MB JVM package does not need to carry a webfont to look deliberate, and
 * the platform's own UI font is the one the user's accessibility settings
 * already know how to scale.
 */
public val QuestTypography: Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.4.sp,
    ),
)

public val QuestShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.sm),
    small = RoundedCornerShape(Radii.md),
    medium = RoundedCornerShape(Radii.md),
    large = RoundedCornerShape(Radii.lg),
    extraLarge = RoundedCornerShape(Radii.lg),
)

@Composable
public fun QuestsTheme(
    dark: Boolean = isSystemInDarkTheme(),
    strings: Strings = StringsEn,
    clock: Clock = Clock.System,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.progression,
            onSecondary = Palette.ink,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.card,
            onSurfaceVariant = colors.textMuted,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.danger,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            secondary = colors.progression,
            onSecondary = Color.White,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.card,
            onSurfaceVariant = colors.textMuted,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.danger,
            onError = Color.White,
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalQuestColors provides colors,
        LocalStrings provides strings,
        LocalClock provides clock,
    ) {
        MaterialTheme(colorScheme = scheme, typography = QuestTypography, shapes = QuestShapes, content = content)
    }
}

/** Shorthand so screens read `theme.textMuted` rather than a composition local. */
public val theme: QuestColors
    @Composable get() = LocalQuestColors.current

public val strings: Strings
    @Composable get() = LocalStrings.current
