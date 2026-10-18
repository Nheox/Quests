package cl.nheox.quests.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.Urgency

/**
 * The design system's raw values.
 *
 * Dark-first, and built for a desktop window rather than a scaled-up phone
 * screen — which is the stated reason this app exists at all (§7.6). Nothing
 * here is Material's default palette: the point was to stop looking like an
 * action-bar-era Android app.
 *
 * The ramps for difficulty, fear and urgency are the load-bearing part. Three
 * independent ratings on one row will turn into mush unless each has its own
 * visual language, so: difficulty is weight (a cool grey-to-violet ramp), fear
 * is temperature (cool to hot), and urgency is saturation (muted to alarming).
 * They stay distinguishable even side by side, and they stay distinguishable in
 * greyscale, which is the cheap test for whether a colour-blind user can read
 * them.
 */
public object Palette {
    // Ink: the window itself.
    public val ink: Color = Color(0xFF0E1116)
    public val inkRaised: Color = Color(0xFF151A21)
    public val inkCard: Color = Color(0xFF1B222B)
    public val inkHover: Color = Color(0xFF222B36)
    public val inkBorder: Color = Color(0xFF2A3440)
    public val inkBorderStrong: Color = Color(0xFF3A4654)

    public val paper: Color = Color(0xFFF7F8FA)
    public val paperRaised: Color = Color(0xFFFFFFFF)
    public val paperCard: Color = Color(0xFFFFFFFF)
    public val paperHover: Color = Color(0xFFEDEFF3)
    public val paperBorder: Color = Color(0xFFDCE1E8)
    public val paperBorderStrong: Color = Color(0xFFC2CBD6)

    public val textOnInk: Color = Color(0xFFE7EDF4)
    public val textOnInkMuted: Color = Color(0xFFA2B2C2)
    public val textOnInkFaint: Color = Color(0xFF6E8092)
    public val textOnPaper: Color = Color(0xFF131A22)
    public val textOnPaperMuted: Color = Color(0xFF4C5B6B)
    public val textOnPaperFaint: Color = Color(0xFF74828F)

    /** Progression: the colour of XP, levels and anything earned. */
    public val gold: Color = Color(0xFFE0A94A)
    public val goldDim: Color = Color(0xFF8A6A2E)

    /** Action: the colour of anything the user can set in motion. */
    public val teal: Color = Color(0xFF3FB8AF)
    public val tealDim: Color = Color(0xFF266F6A)

    public val success: Color = Color(0xFF46A96E)
    public val danger: Color = Color(0xFFD9484B)
    public val warning: Color = Color(0xFFD9893A)
    public val violet: Color = Color(0xFF8E7BE8)

    /** Difficulty reads as weight: seven steps, cool grey through violet. */
    public val difficultyRamp: List<Color> = listOf(
        Color(0xFF6E8092),
        Color(0xFF5F86A6),
        Color(0xFF5390BC),
        Color(0xFF6E8AD4),
        Color(0xFF8E7BE8),
        Color(0xFFA969E0),
        Color(0xFFC75BD1),
    )

    /** Fear reads as temperature: calm slate through alarm red. */
    public val fearRamp: List<Color> = listOf(
        Color(0xFF6E8092),
        Color(0xFF4A93C4),
        Color(0xFFC9A227),
        Color(0xFFD97A2B),
        Color(0xFFD9484B),
    )

    /** Urgency reads as saturation: barely there through unmissable. */
    public val urgencyRamp: List<Color> = listOf(
        Color(0xFF55646F),
        Color(0xFF6C8395),
        Color(0xFF4A93C4),
        Color(0xFFD9893A),
        Color(0xFFD9484B),
    )

    public fun of(difficulty: Difficulty): Color = difficultyRamp[difficulty.index]

    public fun of(fear: Fear): Color = fearRamp[fear.index]

    public fun of(urgency: Urgency): Color = urgencyRamp[urgency.index]

    /** A stable colour per skill, so a skill looks the same in every chart. */
    public val skillWheel: List<Color> = listOf(
        Color(0xFF3FB8AF),
        Color(0xFFE0A94A),
        Color(0xFF8E7BE8),
        Color(0xFF4A93C4),
        Color(0xFFD9893A),
        Color(0xFF46A96E),
        Color(0xFFC75BD1),
        Color(0xFFD9484B),
    )

    public fun forSkill(id: String): Color =
        skillWheel[(id.hashCode().let { if (it == Int.MIN_VALUE) 0 else it }.let { kotlin.math.abs(it) }) % skillWheel.size]
}

/** A four-pixel rhythm. Everything is a multiple of it, so nothing drifts. */
public object Space {
    public val xxs: Dp = 2.dp
    public val xs: Dp = 4.dp
    public val sm: Dp = 8.dp
    public val md: Dp = 12.dp
    public val lg: Dp = 16.dp
    public val xl: Dp = 24.dp
    public val xxl: Dp = 32.dp
    public val xxxl: Dp = 48.dp

    /** Width of the navigation rail, wide enough for a label and a count. */
    public val railWidth: Dp = 216.dp

    /** The detail pane. Wide enough to edit in without the list collapsing. */
    public val detailWidth: Dp = 380.dp
    public val rowHeight: Dp = 44.dp
    public val toolbarHeight: Dp = 52.dp
}

public object Radii {
    public val sm: Dp = 4.dp
    public val md: Dp = 8.dp
    public val lg: Dp = 12.dp
    public val pill: Dp = 999.dp
}
