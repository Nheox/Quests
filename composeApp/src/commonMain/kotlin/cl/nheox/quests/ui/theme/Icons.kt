package cl.nheox.quests.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The icon set, drawn here rather than imported.
 *
 * §2 of the specification commits to original artwork, and the cheapest honest
 * way to keep that promise is to own the drawings outright: each glyph below is
 * a handful of strokes in a 24×24 grid, built from primitives. It also means
 * the app carries no icon font, no SVG parser and no third-party licence to
 * track in `NOTICE`.
 */
public enum class Glyph {
    CHECK,
    CROSS,
    PLUS,
    MINUS,
    PENCIL,
    TRASH,
    COPY,
    SEARCH,
    CHEVRON_RIGHT,
    CHEVRON_DOWN,
    CHEVRON_UP,
    ARROW_UP,
    ARROW_DOWN,
    DOTS,
    CALENDAR,
    CLOCK,
    REPEAT,
    BELL,
    FLAME,
    BOLT,
    GIFT,
    BOX,
    CHART,
    RADAR,
    GEAR,
    HELP,
    SUN,
    LIST,
    TREE,
    INBOX,
    COIN,
    SPARK,
    FOLDER,
    SHUFFLE,
    TARGET,
    COMMAND,
    UNDO,
    WEIGHT,
    PIN,
    SHIELD,
}

@Composable
public fun QuestIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = theme.textMuted,
    description: String? = null,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (description == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = description }
                },
            ),
    ) {
        drawGlyph(glyph, tint)
    }
}

/**
 * Draws a glyph into the current canvas, scaled from a 24×24 design grid.
 *
 * Stroke width is scaled with the icon rather than fixed, so a 12dp glyph in a
 * chip and a 24dp glyph in a toolbar read as the same family instead of one
 * looking anaemic.
 */
public fun DrawScope.drawGlyph(glyph: Glyph, tint: Color) {
    val unit = size.minDimension / 24f
    val stroke = Stroke(
        width = (size.minDimension / 12f).coerceAtLeast(1.2f),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )

    fun point(x: Float, y: Float) = Offset(x * unit, y * unit)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        drawLine(tint, point(x1, y1), point(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }

    fun polyline(vararg coords: Float) {
        val path = Path()
        path.moveTo(coords[0] * unit, coords[1] * unit)
        var index = 2
        while (index + 1 < coords.size) {
            path.lineTo(coords[index] * unit, coords[index + 1] * unit)
            index += 2
        }
        drawPath(path, tint, style = stroke)
    }

    fun closedPath(vararg coords: Float) {
        val path = Path()
        path.moveTo(coords[0] * unit, coords[1] * unit)
        var index = 2
        while (index + 1 < coords.size) {
            path.lineTo(coords[index] * unit, coords[index + 1] * unit)
            index += 2
        }
        path.close()
        drawPath(path, tint, style = stroke)
    }

    fun circle(cx: Float, cy: Float, r: Float, filled: Boolean = false) {
        if (filled) {
            drawCircle(tint, radius = r * unit, center = point(cx, cy))
        } else {
            drawCircle(tint, radius = r * unit, center = point(cx, cy), style = stroke)
        }
    }

    fun box(x: Float, y: Float, w: Float, h: Float) {
        drawRoundRect(
            color = tint,
            topLeft = point(x, y),
            size = Size(w * unit, h * unit),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit, 2f * unit),
            style = stroke,
        )
    }

    fun arc(x: Float, y: Float, w: Float, h: Float, start: Float, sweep: Float) {
        drawArc(
            color = tint,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = point(x, y),
            size = Size(w * unit, h * unit),
            style = stroke,
        )
    }

    when (glyph) {
        Glyph.CHECK -> polyline(5f, 13f, 10f, 18f, 19f, 6f)
        Glyph.CROSS -> {
            line(6f, 6f, 18f, 18f)
            line(18f, 6f, 6f, 18f)
        }

        Glyph.PLUS -> {
            line(12f, 5f, 12f, 19f)
            line(5f, 12f, 19f, 12f)
        }

        Glyph.MINUS -> line(5f, 12f, 19f, 12f)

        Glyph.PENCIL -> {
            polyline(4f, 20f, 5.5f, 15f, 16f, 4.5f, 19.5f, 8f, 9f, 18.5f, 4f, 20f)
            line(14f, 6.5f, 17.5f, 10f)
        }

        Glyph.TRASH -> {
            polyline(5f, 7f, 19f, 7f)
            polyline(9f, 7f, 9f, 4.5f, 15f, 4.5f, 15f, 7f)
            polyline(6.5f, 7f, 7.5f, 20f, 16.5f, 20f, 17.5f, 7f)
            line(10.5f, 10.5f, 10.5f, 16.5f)
            line(13.5f, 10.5f, 13.5f, 16.5f)
        }

        Glyph.COPY -> {
            box(4f, 4f, 11f, 11f)
            box(9f, 9f, 11f, 11f)
        }

        Glyph.SEARCH -> {
            circle(10.5f, 10.5f, 5.5f)
            line(14.5f, 14.5f, 19.5f, 19.5f)
        }

        Glyph.CHEVRON_RIGHT -> polyline(9f, 5f, 16f, 12f, 9f, 19f)
        Glyph.CHEVRON_DOWN -> polyline(5f, 9f, 12f, 16f, 19f, 9f)
        Glyph.CHEVRON_UP -> polyline(5f, 15f, 12f, 8f, 19f, 15f)

        Glyph.ARROW_UP -> {
            line(12f, 19f, 12f, 5f)
            polyline(6f, 11f, 12f, 5f, 18f, 11f)
        }

        Glyph.ARROW_DOWN -> {
            line(12f, 5f, 12f, 19f)
            polyline(6f, 13f, 12f, 19f, 18f, 13f)
        }

        Glyph.DOTS -> {
            circle(12f, 5.5f, 1.4f, filled = true)
            circle(12f, 12f, 1.4f, filled = true)
            circle(12f, 18.5f, 1.4f, filled = true)
        }

        Glyph.CALENDAR -> {
            box(4f, 6f, 16f, 14f)
            line(4f, 10.5f, 20f, 10.5f)
            line(8.5f, 3.5f, 8.5f, 7f)
            line(15.5f, 3.5f, 15.5f, 7f)
        }

        Glyph.CLOCK -> {
            circle(12f, 12f, 8f)
            polyline(12f, 7f, 12f, 12.5f, 16f, 14.5f)
        }

        Glyph.REPEAT -> {
            polyline(5f, 9f, 5f, 7f, 17f, 7f)
            polyline(14f, 4f, 17f, 7f, 14f, 10f)
            polyline(19f, 15f, 19f, 17f, 7f, 17f)
            polyline(10f, 14f, 7f, 17f, 10f, 20f)
        }

        Glyph.BELL -> {
            polyline(6f, 17f, 7.5f, 14f, 7.5f, 10.5f)
            arc(7.5f, 4f, 9f, 9f, 180f, 180f)
            polyline(16.5f, 10.5f, 16.5f, 14f, 18f, 17f, 6f, 17f)
            arc(10f, 17.5f, 4f, 4f, 0f, 180f)
        }

        // Fear: a flame, because "how much does this frighten me" deserves a
        // shape of its own rather than another exclamation mark.
        Glyph.FLAME -> {
            polyline(12f, 3f, 16.5f, 8.5f, 17.5f, 13f)
            arc(6f, 9f, 12f, 12f, 320f, 260f)
            polyline(12f, 11f, 14f, 14f, 13f, 17f)
        }

        Glyph.BOLT -> closedPath(13f, 3f, 6f, 13.5f, 11f, 13.5f, 10f, 21f, 18f, 10f, 12.5f, 10f)

        Glyph.GIFT -> {
            box(4f, 10f, 16f, 10f)
            line(4f, 14f, 20f, 14f)
            line(12f, 10f, 12f, 20f)
            arc(6.5f, 5f, 5.5f, 5.5f, 20f, 290f)
            arc(12f, 5f, 5.5f, 5.5f, 230f, 290f)
        }

        Glyph.BOX -> {
            polyline(12f, 3.5f, 20f, 7.5f, 20f, 16.5f, 12f, 20.5f, 4f, 16.5f, 4f, 7.5f, 12f, 3.5f)
            polyline(4f, 7.5f, 12f, 11.5f, 20f, 7.5f)
            line(12f, 11.5f, 12f, 20.5f)
        }

        Glyph.CHART -> {
            polyline(4f, 20f, 20f, 20f)
            line(7.5f, 20f, 7.5f, 13f)
            line(12f, 20f, 12f, 8f)
            line(16.5f, 20f, 16.5f, 15f)
        }

        Glyph.RADAR -> {
            closedPath(12f, 3f, 20f, 9f, 17f, 19f, 7f, 19f, 4f, 9f)
            closedPath(12f, 8f, 16f, 11f, 14.5f, 16f, 9.5f, 16f, 8f, 11f)
            line(12f, 3f, 12f, 8f)
        }

        Glyph.GEAR -> {
            circle(12f, 12f, 3.2f)
            for (step in 0 until 8) {
                val angle = Math.toRadians((step * 45).toDouble())
                val inner = 5.6f
                val outer = 8.4f
                val cx = 12f
                val cy = 12f
                line(
                    cx + (inner * kotlin.math.cos(angle)).toFloat(),
                    cy + (inner * kotlin.math.sin(angle)).toFloat(),
                    cx + (outer * kotlin.math.cos(angle)).toFloat(),
                    cy + (outer * kotlin.math.sin(angle)).toFloat(),
                )
            }
        }

        Glyph.HELP -> {
            circle(12f, 12f, 8.5f)
            arc(8.5f, 6.5f, 7f, 6f, 160f, 240f)
            line(12f, 12.5f, 12f, 14.5f)
            circle(12f, 17.2f, 1.1f, filled = true)
        }

        Glyph.SUN -> {
            circle(12f, 12f, 4.2f)
            for (step in 0 until 8) {
                val angle = Math.toRadians((step * 45).toDouble())
                line(
                    12f + (6.5f * kotlin.math.cos(angle)).toFloat(),
                    12f + (6.5f * kotlin.math.sin(angle)).toFloat(),
                    12f + (8.8f * kotlin.math.cos(angle)).toFloat(),
                    12f + (8.8f * kotlin.math.sin(angle)).toFloat(),
                )
            }
        }

        Glyph.LIST -> {
            line(4f, 7f, 20f, 7f)
            line(4f, 12f, 20f, 12f)
            line(4f, 17f, 20f, 17f)
        }

        Glyph.TREE -> {
            line(5f, 5f, 5f, 18f)
            polyline(5f, 9f, 11f, 9f)
            polyline(5f, 14f, 11f, 14f)
            line(11f, 6.5f, 20f, 6.5f)
            line(13f, 9f, 20f, 9f)
            line(13f, 14f, 20f, 14f)
        }

        Glyph.INBOX -> {
            polyline(4f, 12f, 4f, 19f, 20f, 19f, 20f, 12f)
            polyline(4f, 12f, 7f, 5f, 17f, 5f, 20f, 12f)
            polyline(4f, 12f, 9f, 12f, 10.5f, 15f, 13.5f, 15f, 15f, 12f, 20f, 12f)
        }

        Glyph.COIN -> {
            circle(12f, 12f, 8f)
            circle(12f, 12f, 4.2f)
        }

        // Skills: a four-pointed spark. Reads as "growth" at 12dp, which a
        // graduation cap or a dumbbell would not.
        Glyph.SPARK -> {
            polyline(12f, 3f, 13.6f, 10.4f, 21f, 12f, 13.6f, 13.6f, 12f, 21f, 10.4f, 13.6f, 3f, 12f, 10.4f, 10.4f, 12f, 3f)
        }

        Glyph.FOLDER -> {
            polyline(4f, 19f, 4f, 6f, 10f, 6f, 12f, 9f, 20f, 9f, 20f, 19f, 4f, 19f)
        }

        Glyph.SHUFFLE -> {
            polyline(4f, 7f, 9f, 7f, 20f, 18f)
            polyline(17f, 18f, 20f, 18f, 20f, 15f)
            polyline(4f, 18f, 9f, 18f, 13f, 14f)
            polyline(16f, 10f, 20f, 6f, 20f, 9f)
            line(20f, 6f, 17f, 6f)
        }

        Glyph.TARGET -> {
            circle(12f, 12f, 8.5f)
            circle(12f, 12f, 5f)
            circle(12f, 12f, 1.6f, filled = true)
        }

        Glyph.COMMAND -> {
            box(9f, 9f, 6f, 6f)
            arc(4.5f, 4.5f, 4.5f, 4.5f, 0f, 270f)
            arc(15f, 4.5f, 4.5f, 4.5f, 270f, 270f)
            arc(4.5f, 15f, 4.5f, 4.5f, 90f, 270f)
            arc(15f, 15f, 4.5f, 4.5f, 180f, 270f)
        }

        Glyph.UNDO -> {
            polyline(8f, 6f, 4f, 10f, 8f, 14f)
            arc(4f, 10f, 15f, 10f, 180f, 160f)
        }

        // Difficulty: a weight, so difficulty reads as heaviness and fear reads
        // as heat. Two ratings on one row need two metaphors.
        Glyph.WEIGHT -> {
            line(3f, 9f, 3f, 15f)
            line(21f, 9f, 21f, 15f)
            line(6f, 7f, 6f, 17f)
            line(18f, 7f, 18f, 17f)
            line(6f, 12f, 18f, 12f)
        }

        // A pushpin: cap, tapering body, needle. Marks a quest the user has
        // lifted out of the automatic order.
        Glyph.PIN -> {
            line(8.5f, 3.5f, 15.5f, 3.5f)
            polyline(10f, 3.5f, 10f, 9f, 7f, 13.5f, 17f, 13.5f, 14f, 9f, 14f, 3.5f)
            line(12f, 13.5f, 12f, 20.5f)
        }

        // Standing: a shield bearing a rank chevron — where you stand, in the
        // vocabulary of the game the app is.
        Glyph.SHIELD -> {
            closedPath(12f, 3f, 19.5f, 6f, 19.5f, 12f, 12f, 21f, 4.5f, 12f, 4.5f, 6f)
            polyline(8.5f, 10.5f, 12f, 13.5f, 15.5f, 10.5f)
        }
    }
}
