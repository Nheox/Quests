package cl.nheox.quests.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.nheox.quests.domain.Balance
import cl.nheox.quests.domain.Stats
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.components.BarChart
import cl.nheox.quests.ui.components.BarDatum
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.Pill
import cl.nheox.quests.ui.components.RadarAxis
import cl.nheox.quests.ui.components.RadarChart
import cl.nheox.quests.ui.components.RadarLegend
import cl.nheox.quests.ui.components.RateBar
import cl.nheox.quests.ui.components.RateLine
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Palette
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme
import java.time.format.DateTimeFormatter

/**
 * Insight: the fear log, the trends and the radar.
 *
 * The fear log is the reason this screen exists. Everything else here is a
 * progress chart of the kind any tracker has; "you finish 80% of your low-fear
 * quests and 22% of your high-fear ones" is a sentence no to-do list can say,
 * and it is the one that actually changes behaviour.
 */
@Composable
public fun InsightScreen(
    app: AppState,
    state: QuestsState,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val summary = remember(state, app.clock.today()) { Stats.summarise(state, app.clock) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.statsTitle, style = MaterialTheme.typography.headlineSmall, color = theme.text)
        }
        HLine()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Space.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                StatTile(text.statsOpen, summary.openCount.toString(), Glyph.LIST, theme.text, Modifier.weight(1f))
                StatTile(
                    text.statsOverdue,
                    summary.overdueCount.toString(),
                    Glyph.CALENDAR,
                    if (summary.overdueCount > 0) theme.danger else theme.textMuted,
                    Modifier.weight(1f),
                )
                StatTile(
                    text.statsThisWeek,
                    summary.completedLast7Days.toString(),
                    Glyph.CHECK,
                    theme.success,
                    Modifier.weight(1f),
                )
                StatTile(
                    text.xp,
                    summary.xpLast7Days.toString(),
                    Glyph.SPARK,
                    theme.progression,
                    Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(Space.lg))
            FearLogCard(summary)

            Spacer(Modifier.height(Space.lg))
            Card(text.statsXpPerWeek) {
                BarChart(
                    data = summary.weeks.mapIndexed { index, week ->
                        BarDatum(
                            label = week.weekStart.format(weekLabel),
                            value = week.xp.toFloat(),
                            color = theme.progression,
                            emphasised = index == summary.weeks.lastIndex,
                        )
                    },
                )
            }

            Spacer(Modifier.height(Space.lg))
            Card(text.statsCompletionRate) {
                RateLine(values = summary.weeks.map { it.completionRate })
                Spacer(Modifier.height(Space.xs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0%", style = MaterialTheme.typography.labelSmall, color = theme.textFaint)
                    Text("100%", style = MaterialTheme.typography.labelSmall, color = theme.textFaint)
                }
            }

            Spacer(Modifier.height(Space.lg))
            RadarCard(state, summary)

            Spacer(Modifier.height(Space.lg))
            SkillGrowthCard(summary)

            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    glyph: Glyph,
    colour: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(theme.card, RoundedCornerShape(Radii.md))
            .padding(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            QuestIcon(glyph, size = 12.dp, tint = theme.textFaint)
            Text(label, style = MaterialTheme.typography.labelMedium, color = theme.textFaint, maxLines = 1)
        }
        Spacer(Modifier.height(Space.xs))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = colour)
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(theme.card, RoundedCornerShape(Radii.md))
            .padding(Space.md),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = theme.textMuted)
        Spacer(Modifier.height(Space.md))
        content()
    }
}

@Composable
private fun FearLogCard(summary: cl.nheox.quests.domain.StatsSummary) {
    val text = strings
    Column(
        Modifier
            .fillMaxWidth()
            .background(theme.card, RoundedCornerShape(Radii.md))
            .padding(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            QuestIcon(Glyph.FLAME, size = 15.dp, tint = theme.danger)
            Text(text.statsFearLog, style = MaterialTheme.typography.titleMedium, color = theme.text)
        }
        Text(
            text.statsFearLogBody,
            style = MaterialTheme.typography.bodySmall,
            color = theme.textFaint,
            modifier = Modifier.padding(top = Space.xxs, bottom = Space.md),
        )

        if (summary.fearLog.all { it.total == 0 }) {
            Text(text.statsNotEnoughData, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
            return@Column
        }

        summary.fearLog.forEach { stat ->
            val colour = Palette.of(stat.fear)
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(
                    text.fear(stat.fear),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textMuted,
                    modifier = Modifier.width(86.dp),
                    maxLines = 1,
                )
                Box(Modifier.weight(1f)) {
                    RateBar(stat.throughputRate, colour)
                }
                // Deliberately the throughput rate — completed out of everything
                // ever raised at this level — and not the rate over resolved
                // quests only. Avoidance does not show up as *failing* the
                // frightening ones; it shows up as never resolving them at all,
                // so a denominator of resolved quests would report "100%" for a
                // level whose quests are mostly still sitting there untouched.
                // That is precisely the pattern this feature exists to surface.
                Text(
                    text = stat.throughputRate?.let { text.percent(it) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (stat.throughputRate == null) theme.textFaint else colour,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(44.dp),
                )
                // The denominator is shown because a 100% rate over one quest is
                // not the same claim as a 100% rate over forty.
                Text(
                    text = "${stat.completed}/${stat.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textFaint,
                    modifier = Modifier.width(52.dp),
                )
                if (stat.openOverdue > 0) {
                    Pill("${stat.openOverdue} ${text.statsOverdue.lowercase()}", color = theme.warning)
                } else if (stat.open > 0) {
                    Pill("${stat.open} ${text.statsStillAvoiding}", color = theme.textFaint)
                }
            }
        }
    }
}

@Composable
private fun RadarCard(state: QuestsState, summary: cl.nheox.quests.domain.StatsSummary) {
    val text = strings
    // Normalised against the strongest skill, so the shape answers "where is my
    // effort going" rather than "how much XP is a lot".
    val top = summary.skills.take(8)
    val peak = (top.maxOfOrNull { it.totalXp } ?: 0L).coerceAtLeast(1L)
    val axes = top.map { skill ->
        RadarAxis(
            label = "${skill.name} · ${text.level} ${skill.level}",
            value = (skill.totalXp.toFloat() / peak.toFloat()),
            color = Palette.forSkill(skill.skillId),
        )
    }

    Card(text.statsRadar) {
        if (axes.size < 3) {
            Text(text.statsNotEnoughData, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
            return@Card
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(230.dp)) {
                RadarChart(axes)
            }
            Spacer(Modifier.width(Space.lg))
            RadarLegend(axes, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "${text.level} ${Balance.levelForXp(state.totalXp)} · ${state.totalXp} ${text.xp}",
            style = MaterialTheme.typography.bodySmall,
            color = theme.textFaint,
        )
    }
}

@Composable
private fun SkillGrowthCard(summary: cl.nheox.quests.domain.StatsSummary) {
    val text = strings
    Card(text.statsSkillGrowth) {
        if (summary.skills.isEmpty()) {
            Text(text.skillsEmpty, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
            return@Card
        }
        summary.skills.forEach { skill ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                QuestIcon(Glyph.SPARK, size = 12.dp, tint = Palette.forSkill(skill.skillId))
                Text(
                    skill.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Pill("${text.level} ${skill.level}", color = Palette.forSkill(skill.skillId))
                Text(
                    "+${skill.xpLast30Days}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.progression,
                    modifier = Modifier.width(56.dp),
                )
                if (skill.currentStreakDays > 0) {
                    Pill(
                        "${text.statsStreak} ${text.streakDays(skill.currentStreakDays)}",
                        color = theme.success,
                        leading = Glyph.BOLT,
                    )
                } else if (skill.longestStreakDays > 1) {
                    Pill(
                        "${text.statsLongestStreak} ${text.streakDays(skill.longestStreakDays)}",
                        color = theme.textFaint,
                    )
                }
            }
        }
    }
}

private val weekLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M")
