package cl.nheox.quests.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.nheox.quests.ui.theme.Motion
import cl.nheox.quests.ui.theme.theme

/**
 * A level ring: the track, and the arc of progress through the current level,
 * starting at twelve o'clock.
 *
 * The same ring the app icon is built from, so a skill's level on screen and
 * the mark on the Dock say one thing. Gold by default, because it is progress.
 * The arc moves to a new value rather than jumping (§7.6: progress is felt,
 * not announced), and [content] — usually the level number — sits inside it.
 */
@Composable
public fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 64.dp,
    stroke: Dp = 6.dp,
    color: Color = theme.progression,
    track: Color = theme.border,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.tween(Motion.MEDIUM))
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2f
            val arcSize = Size(size.width - width, size.height - width)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width),
            )
            if (shown > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * shown,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}
