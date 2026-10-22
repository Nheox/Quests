package cl.nheox.quests.ui.components

import androidx.compose.ui.text.drawText
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.theme
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Charts drawn by hand on a [Canvas], with no chart library.
 *
 * Three charts, each under a hundred lines, against a dependency that would
 * bring its own theming, its own animation opinions and its own release cadence
 * — §8.1 says no chart library unless it earns its place, and on this evidence
 * it does not.
 */

public data class RadarAxis(
    val label: String,
    /** Already normalised to 0..1 by the caller, which owns the scale. */
    val value: Float,
    val color: Color,
)

/**
 * A radar across selected skills.
 *
 * Normalisation is the caller's job on purpose: whether an axis means "level
 * relative to your highest skill" or "XP relative to a target" is a question
 * about what the user wants to see, not about how to draw a polygon.
 */
@Composable
public fun RadarChart(
    axes: List<RadarAxis>,
    modifier: Modifier = Modifier,
    rings: Int = 4,
    labelled: Boolean = false,
) {
    if (axes.size < 3) {
        // A radar with two axes is a line and tells you nothing; say so rather
        // than drawing something misleading.
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                cl.nheox.quests.ui.theme.strings.statsNotEnoughData,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textFaint,
            )
        }
        return
    }

    val grid = theme.border
    val gridStrong = theme.borderStrong
    val fill = theme.accent
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = theme.textMuted)

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        // Labelled, the names need room outside the outer ring.
        Canvas(Modifier.fillMaxSize().padding(if (labelled) RadarLabelRoom else Space.xl)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            val step = (2.0 * Math.PI / axes.size).toFloat()
            // Start at twelve o'clock, so the first axis is where a reader looks.
            val offsetAngle = (-Math.PI / 2.0).toFloat()

            for (ring in 1..rings) {
                val ringRadius = radius * ring / rings
                val path = Path()
                axes.indices.forEach { index ->
                    val angle = offsetAngle + step * index
                    val point = Offset(
                        centre.x + ringRadius * cos(angle),
                        centre.y + ringRadius * sin(angle),
                    )
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                path.close()
                drawPath(path, if (ring == rings) gridStrong else grid, style = Stroke(width = 1f))
            }

            axes.indices.forEach { index ->
                val angle = offsetAngle + step * index
                drawLine(
                    grid,
                    centre,
                    Offset(centre.x + radius * cos(angle), centre.y + radius * sin(angle)),
                    strokeWidth = 1f,
                )
            }

            val shape = Path()
            axes.forEachIndexed { index, axis ->
                val angle = offsetAngle + step * index
                // A floor on the plotted radius keeps a zero-value axis visible
                // as a point rather than collapsing the polygon into the centre.
                val value = axis.value.coerceIn(0f, 1f) * 0.94f + 0.06f
                val point = Offset(
                    centre.x + radius * value * cos(angle),
                    centre.y + radius * value * sin(angle),
                )
                if (index == 0) shape.moveTo(point.x, point.y) else shape.lineTo(point.x, point.y)
            }
            shape.close()
            drawPath(shape, fill.copy(alpha = 0.22f))
            drawPath(shape, fill, style = Stroke(width = 2f))

            if (labelled) {
                // Each name outside its vertex, aligned away from the centre:
                // to the right of a right-hand vertex, centred over the top one.
                val gap = 8.dp.toPx()
                val maxWidth = RadarLabelRoom.toPx() * 1.6f
                axes.forEachIndexed { index, axis ->
                    val angle = offsetAngle + step * index
                    val dx = cos(angle)
                    val dy = sin(angle)
                    val layout = measurer.measure(
                        androidx.compose.ui.text.AnnotatedString(axis.label),
                        style = labelStyle,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidth.toInt()),
                    )
                    val anchor = Offset(centre.x + (radius + gap) * dx, centre.y + (radius + gap) * dy)
                    val w = layout.size.width.toFloat()
                    val h = layout.size.height.toFloat()
                    val x = when {
                        dx > 0.2f -> anchor.x
                        dx < -0.2f -> anchor.x - w
                        else -> anchor.x - w / 2f
                    }
                    val y = when {
                        dy > 0.2f -> anchor.y
                        dy < -0.2f -> anchor.y - h
                        else -> anchor.y - h / 2f
                    }
                    drawText(layout, topLeft = Offset(x, y))
                }
            }

            axes.forEachIndexed { index, axis ->
                val angle = offsetAngle + step * index
                val value = axis.value.coerceIn(0f, 1f) * 0.94f + 0.06f
                drawCircle(
                    axis.color,
                    radius = 3.5f,
                    center = Offset(
                        centre.x + radius * value * cos(angle),
                        centre.y + radius * value * sin(angle),
                    ),
                )
            }
        }
    }
}

/** Room around a labelled radar for the names at its vertices. */
private val RadarLabelRoom = 64.dp

/** The legend for the radar, where the axis names actually fit. */
@Composable
public fun RadarLegend(axes: List<RadarAxis>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        axes.forEach { axis ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Canvas(Modifier.size(8.dp)) {
                    drawCircle(axis.color)
                }
                Text(axis.label, style = MaterialTheme.typography.bodySmall, color = theme.textMuted, maxLines = 1)
            }
        }
    }
}

public data class BarDatum(
    val label: String,
    val value: Float,
    val color: Color? = null,
    val emphasised: Boolean = false,
)

/**
 * A column chart for weekly totals.
 *
 * Empty buckets are drawn as a flat baseline rather than skipped, because a
 * chart that omits the weeks you did nothing turns a fortnight off into an
 * unbroken line — which would make the trend chart lie in exactly the
 * direction nobody needs.
 */
@Composable
public fun BarChart(
    data: List<BarDatum>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 132.dp,
    showLabels: Boolean = true,
) {
    if (data.isEmpty()) return
    val maximum = max(data.maxOf { it.value }, 1f)
    val defaultColour = theme.accent
    val baseline = theme.border
    val labelColour = theme.textFaint

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val gap = 4f
            val slot = (size.width - gap * (data.size - 1)) / data.size
            val usable = size.height - 2f
            data.forEachIndexed { index, datum ->
                val fraction = (datum.value / maximum).coerceIn(0f, 1f)
                val barHeight = max(usable * fraction, if (datum.value > 0f) 2f else 1f)
                drawRoundRect(
                    color = (datum.color ?: defaultColour).copy(alpha = if (datum.emphasised) 1f else 0.78f),
                    topLeft = Offset(index * (slot + gap), usable - barHeight),
                    size = Size(slot, barHeight),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
            drawLine(
                baseline,
                Offset(0f, size.height - 1f),
                Offset(size.width, size.height - 1f),
                strokeWidth = 1f,
            )
        }
        if (showLabels) {
            Row(Modifier.fillMaxWidth().padding(top = Space.xs)) {
                data.forEachIndexed { index, datum ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        // Every other label only: twelve stacked week labels in a
                        // 380dp pane is unreadable mush.
                        if (index % 2 == 0 || data.size <= 6) {
                            Text(
                                datum.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColour,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A sparkline for a rate over time, with the 0..1 band implied. */
@Composable
public fun RateLine(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 72.dp,
    color: Color? = null,
) {
    if (values.size < 2) return
    val stroke = color ?: theme.progression
    val grid = theme.border

    Canvas(modifier.fillMaxWidth().height(height)) {
        val stepX = if (values.size <= 1) 0f else size.width / (values.size - 1)
        listOf(0f, 0.5f, 1f).forEach { level ->
            val y = size.height * (1f - level)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        // Each run of consecutive known values is drawn as its own segment, so a
        // week with nothing resolved leaves a visible gap instead of an
        // invented straight line across it.
        var path: Path? = null
        values.forEachIndexed { index, value ->
            if (value == null) {
                path?.let { drawPath(it, stroke, style = Stroke(width = 2f, cap = StrokeCap.Round)) }
                path = null
                return@forEachIndexed
            }
            val point = Offset(index * stepX, size.height * (1f - value.coerceIn(0f, 1f)))
            val current = path
            if (current == null) {
                path = Path().apply { moveTo(point.x, point.y) }
            } else {
                current.lineTo(point.x, point.y)
            }
            drawCircle(stroke, radius = 2.5f, center = point)
        }
        path?.let { drawPath(it, stroke, style = Stroke(width = 2f, cap = StrokeCap.Round)) }
    }
}

@Composable
public fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    height: androidx.compose.ui.unit.Dp = 6.dp,
) {
    val fill = color ?: theme.progression
    val track = theme.border
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(track, cornerRadius = radius)
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) {
            drawRoundRect(fill, size = Size(max(width, size.height), size.height), cornerRadius = radius)
        }
    }
}

/**
 * The energy bar.
 *
 * Coloured by band rather than by a continuous gradient, because the number is
 * only ever used as a band — by the suggester and by the person glancing at it.
 */
@Composable
public fun EnergyBar(
    energy: Int,
    modifier: Modifier = Modifier,
) {
    val band = cl.nheox.quests.domain.Energy.band(energy)
    val colour = when (band) {
        cl.nheox.quests.domain.EnergyBand.HIGH -> theme.success
        cl.nheox.quests.domain.EnergyBand.MEDIUM -> theme.accent
        cl.nheox.quests.domain.EnergyBand.LOW -> theme.warning
        cl.nheox.quests.domain.EnergyBand.SPENT -> theme.danger
    }
    Box(modifier) {
        ProgressBar(energy / 100f, color = colour, height = 6.dp)
    }
}

/** A quiet horizontal meter used by the fear log, labelled with its own rate. */
@Composable
public fun RateBar(
    fraction: Float?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val track = theme.border
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val radius = CornerRadius(Radii.sm.toPx(), Radii.sm.toPx())
        drawRoundRect(track, cornerRadius = radius)
        if (fraction != null) {
            val width = size.width * fraction.coerceIn(0f, 1f)
            if (width > 0f) {
                drawRoundRect(color, size = Size(max(width, 3f), size.height), cornerRadius = radius)
            }
        }
    }
}
