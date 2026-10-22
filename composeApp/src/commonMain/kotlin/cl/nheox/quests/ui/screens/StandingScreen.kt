package cl.nheox.quests.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.nheox.quests.domain.DayActivity
import cl.nheox.quests.domain.Energy
import cl.nheox.quests.domain.EnergyBand
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.QuestState
import cl.nheox.quests.domain.QuestView
import cl.nheox.quests.domain.Skill
import cl.nheox.quests.domain.Standing
import cl.nheox.quests.domain.StandingSummary
import cl.nheox.quests.domain.SuggestContext
import cl.nheox.quests.domain.Suggester
import cl.nheox.quests.domain.Suggestion
import cl.nheox.quests.domain.Views
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.Route
import cl.nheox.quests.ui.charts.ActivityHeatmap
import cl.nheox.quests.ui.components.CompleteBox
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.ProgressBar
import cl.nheox.quests.ui.components.QuestButton
import cl.nheox.quests.ui.components.clickableRow
import cl.nheox.quests.ui.components.focusRing
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Motion
import cl.nheox.quests.ui.theme.Palette
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme
import java.time.LocalDate

/** Past this the cards stop stretching and the dashboard centres in the window. */
private val MaxContentWidth = 1200.dp

/** Content this wide or wider takes three columns of cards; the heatmap needs the room. */
private val ThreeColumns = 960.dp

/** Content this wide or wider takes two columns; below it the cards stack. */
private val TwoColumns = 620.dp

private val CardPadding = Space.xl

/** At most this many quests on the Today card; the rest are one click away. */
private const val TODAY_ROWS = 5

/** The height the week's tallest bar reaches. */
private val BarArea = 120.dp

/**
 * Standing: the screen the app opens on (§7.5), laid out as the dashboard.
 *
 * One look answers "what is my situation?" — what is overdue, how today is
 * going, the week's XP, twelve weeks of activity, the skill about to level,
 * and what to do now — instead of a notification at a set hour that reports
 * the same numbers and is gone. It is a view like any other (Ctrl-0), and
 * Settings can open the app somewhere else.
 *
 * Each card leads with one big number in the colour of what it counts — red
 * for overdue, teal for today's progress, gold for XP — then a small chart or
 * list. Energy, points and total XP are not repeated here: they live in the
 * sidebar, which is on screen too. Cards sit on a grid of three, two or one
 * columns depending on the width, in the same order at every width, and the
 * cards in a row share one height so their edges line up.
 */
@Composable
public fun StandingScreen(
    app: AppState,
    state: QuestsState,
    onQuickAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val today = app.clock.today()
    val energy = app.energy(state)
    val summary = remember(state, today) { Standing.summarise(state, app.clock) }
    val suggestions = remember(state, energy, today) {
        Suggester.suggest(
            quests = state.quests.values,
            context = SuggestContext(
                energy = energy,
                // The dashboard does not ask how long there is; the full
                // suggester does. Unknown means "do not filter on time".
                minutesAvailable = null,
                today = today,
                skills = state.skills,
            ),
        )
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(text.navStanding, style = MaterialTheme.typography.headlineSmall, color = theme.text, maxLines = 1)
            Text(
                text.longDate(today),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HLine()

        if (app.quickAddOpen) {
            QuickAddBar(
                app,
                onSubmit = { line ->
                    onQuickAdd(line)
                    // Adding selects the new quest, which suits a list but not
                    // this screen: here the selection would be out of sight,
                    // and Space would complete it unseen.
                    app.selectedId = null
                },
            )
            HLine()
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Read once here: the layout scope's width is not reachable from
            // the nested scopes below, and the heatmap is sized from it so the
            // cards never have to measure themselves twice.
            val content = minOf(maxWidth, MaxContentWidth) - Space.xl * 2
            val columns = when {
                content >= ThreeColumns -> 3
                content >= TwoColumns -> 2
                else -> 1
            }
            val cardInner = (content - Space.lg * (columns - 1)) / columns - CardPadding * 2

            val cards: List<@Composable (Modifier) -> Unit> = listOf(
                { OverdueCard(app, summary, it) },
                { TodayCard(app, summary, it) },
                { WeekCard(summary, today, it) },
                { ActivityCard(summary, today, cardInner, it) },
                { ClosestSkillCard(app, summary.closestSkills, it) },
                { WhatNowCard(app, suggestions, Energy.band(energy), it) },
            )

            Box(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .widthIn(max = MaxContentWidth)
                        .fillMaxWidth()
                        .padding(Space.xl),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    cards.chunked(columns).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Space.lg),
                        ) {
                            row.forEach { card -> card(Modifier.weight(1f).fillMaxHeight()) }
                            // A short last row keeps the grid's column widths.
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One dashboard card: its name in small capitals, then its content. Separated
 * from the window by value alone (DESIGN §7).
 */
@Composable
private fun StandingCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .background(theme.card, RoundedCornerShape(Radii.lg))
            .padding(CardPadding),
    ) {
        Text(
            title.uppercase(),
            // The sans label role, not labelSmall, which is the monospaced one.
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
            color = theme.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Space.md))
        content()
    }
}

/**
 * The card's one big number, with the words that finish its sentence beside
 * it: "3 quests", "4 of 7 done", "182 up from 148 last week". The two share a
 * baseline, not a box edge (DESIGN §4), and figures are tabular so the number
 * does not shift as it changes.
 */
@Composable
private fun Headline(value: String, colour: Color, rest: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(
            value,
            style = BigNumber,
            color = colour,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            rest,
            style = MaterialTheme.typography.bodyLarge,
            color = theme.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

private val BigNumber: TextStyle
    @Composable get() = MaterialTheme.typography.displaySmall.copy(
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )

@Composable
private fun OverdueCard(app: AppState, summary: StandingSummary, modifier: Modifier) {
    val text = strings
    val count = summary.overdueCount
    StandingCard(text.navOverdue, modifier) {
        if (count == 0) {
            Headline("0", theme.textMuted, text.questsWord(0))
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                QuestIcon(Glyph.CHECK, size = 14.dp, tint = theme.success)
                Text(text.messageNoOverdue, style = MaterialTheme.typography.bodyMedium, color = theme.textMuted)
            }
            return@StandingCard
        }
        // Large and bold, so the danger red clears the 3:1 bar for large text
        // in both themes, where body-sized red would not (DESIGN §6).
        Headline(count.toString(), theme.danger, text.questsWord(count))
        Spacer(Modifier.height(Space.md))
        summary.worstOverdue.forEach { quest ->
            QuestLine(
                onOpen = {
                    app.goToView(QuestView.OVERDUE)
                    app.selectedId = quest.id
                },
            ) {
                Box(Modifier.size(6.dp).background(theme.danger, CircleShape))
                Text(
                    quest.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text.overdueFor(Standing.daysOverdue(quest, app.clock)),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = theme.textMuted,
                    maxLines = 1,
                )
            }
        }
        if (count > summary.worstOverdue.size) {
            MoreLink(text.moreCount(count - summary.worstOverdue.size)) { app.goToView(QuestView.OVERDUE) }
        }
    }
}

@Composable
private fun TodayCard(app: AppState, summary: StandingSummary, modifier: Modifier) {
    val text = strings
    val progress = summary.today
    StandingCard(text.navToday, modifier) {
        if (progress.total == 0) {
            Headline("0", theme.textMuted, text.todayOfTotal(0))
            Spacer(Modifier.height(Space.md))
            Text(text.messageNothingDueToday, style = MaterialTheme.typography.bodyMedium, color = theme.textMuted)
            return@StandingCard
        }
        Headline(progress.done.toString(), theme.accent, text.todayOfTotal(progress.total))
        Spacer(Modifier.height(Space.md))
        FillingBar(progress.fraction, theme.accent, height = 8.dp)
        Spacer(Modifier.height(Space.md))
        summary.todayQuests.take(TODAY_ROWS).forEach { quest -> TodayLine(app, quest) }
        if (summary.todayQuests.size > TODAY_ROWS) {
            MoreLink(text.moreCount(summary.todayQuests.size - TODAY_ROWS)) { app.goToView(QuestView.TODAY) }
        }
    }
}

/**
 * One line of today's checklist. An open quest is ticked off right here; a
 * finished one shows its tick in teal and its title muted, and clicking either
 * opens the quest in its list.
 */
@Composable
private fun TodayLine(app: AppState, quest: Quest) {
    QuestLine(
        onOpen = {
            app.goToView(Views.home(quest, app.clock))
            app.selectedId = quest.id
        },
    ) {
        if (quest.isDone) {
            // The same 20dp slot the open box takes, so the titles line up.
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                QuestIcon(Glyph.CHECK, size = 14.dp, tint = theme.accent)
            }
        } else {
            CompleteBox(state = QuestState.OPEN, onClick = { app.store.completeQuest(quest.id) })
        }
        Text(
            quest.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (quest.isDone) theme.textMuted else theme.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * XP this ISO week: the total, how it compares with last week, and a bar a day
 * with today's in full gold.
 *
 * A drop is told in the muted colour, never the danger one: a quieter week is
 * information, and a tracker that scolds for it gets closed.
 */
@Composable
private fun WeekCard(summary: StandingSummary, today: LocalDate, modifier: Modifier) {
    val text = strings
    val week = summary.week
    StandingCard(text.standingXpThisWeek, modifier) {
        Headline(week.thisWeek.toString(), theme.progression, text.weekVsLast(week.thisWeek, week.lastWeek))
        Spacer(Modifier.height(Space.lg))
        WeekBars(summary.weekDays, today)
    }
}

/** Seven bars on one scale, Monday to Sunday, and the day's initial under each. */
@Composable
private fun WeekBars(days: List<DayActivity>, today: LocalDate) {
    val text = strings
    val scale = (days.maxOfOrNull { it.xp } ?: 0).coerceAtLeast(1)
    val past = lerp(theme.card, theme.progression, 0.3f)
    val description = days.joinToString(", ") { "${text.weekdayShort(it.date.dayOfWeek)} ${it.xp} XP" }
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        days.forEach { day ->
            val isToday = day.date == today
            val future = day.date.isAfter(today)
            val shown by animateFloatAsState(day.xp.toFloat() / scale, Motion.tween(Motion.MEDIUM))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(BarArea), contentAlignment = Alignment.BottomCenter) {
                    // An empty or future day keeps a sliver of baseline, so the
                    // week still reads as seven days rather than as gaps.
                    val height = if (day.xp == 0) 2.dp else (BarArea * shown).coerceAtLeast(4.dp)
                    Box(
                        Modifier
                            .fillMaxWidth(0.72f)
                            .height(height)
                            .background(
                                when {
                                    future || day.xp == 0 -> theme.border
                                    isToday -> theme.progression
                                    else -> past
                                },
                                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                            ),
                    )
                }
                Spacer(Modifier.height(Space.sm))
                Text(
                    text.weekdayInitial(day.date.dayOfWeek),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) theme.text else theme.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(summary: StandingSummary, today: LocalDate, available: Dp, modifier: Modifier) {
    val text = strings
    StandingCard(text.standingLastTwelveWeeks, modifier) {
        ActivityHeatmap(
            days = summary.activity,
            today = today,
            availableWidth = available,
            description = text.heatmapDescription(
                xp = summary.activityXp,
                activeDays = summary.activeDays,
                todayXp = summary.activity.lastOrNull()?.xp ?: 0,
            ),
        )
        Spacer(Modifier.height(Space.md))
        Text(text.standingHeatmapCaption, style = MaterialTheme.typography.bodySmall, color = theme.textMuted)
    }
}

/**
 * The skill nearest its next level, featured, and the two behind it — each
 * bar in that skill's own colour, as everywhere else in the app.
 */
@Composable
private fun ClosestSkillCard(app: AppState, skills: List<Skill>, modifier: Modifier) {
    val text = strings
    StandingCard(text.standingClosestSkill, modifier) {
        val featured = skills.firstOrNull()
        if (featured == null) {
            Text(text.skillsEmpty, style = MaterialTheme.typography.bodyMedium, color = theme.textMuted)
            Spacer(Modifier.height(Space.md))
            QuestButton(text.navSkills, compact = true, onClick = { app.go(Route.Skills) })
            return@StandingCard
        }
        Text(
            featured.name,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = theme.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text.xpUntilLevel(featured.xpToNextLevel, featured.level + 1),
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = theme.textMuted,
            maxLines = 1,
        )
        Spacer(Modifier.height(Space.md))
        FillingBar(featured.progressToNextLevel, Palette.forSkill(featured.id), height = 8.dp)

        skills.drop(1).forEach { skill ->
            Spacer(Modifier.height(Space.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    skill.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text.levelLower(skill.level),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = theme.textMuted,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(Space.sm))
            FillingBar(skill.progressToNextLevel, Palette.forSkill(skill.id), height = 6.dp)
        }
    }
}

/**
 * "What should I do now?": the three picks for this energy, with the reason
 * they were picked said in a line above them. Start records that the quest is
 * being worked on — which is what the suggester's staleness check reads — and
 * opens it in its list.
 */
@Composable
private fun WhatNowCard(app: AppState, suggestions: List<Suggestion>, band: EnergyBand, modifier: Modifier) {
    val text = strings
    StandingCard(text.standingWhatNow, modifier) {
        if (suggestions.isEmpty()) {
            Text(text.standingNothingOpen, style = MaterialTheme.typography.bodyMedium, color = theme.textMuted)
            Spacer(Modifier.height(Space.md))
            QuestButton(text.actionNewQuest, compact = true, glyph = Glyph.PLUS, onClick = { app.openQuickAdd() })
            return@StandingCard
        }
        Text(text.energyCaption(band), style = MaterialTheme.typography.bodyMedium, color = theme.textMuted)
        Spacer(Modifier.height(Space.md))
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            suggestions.forEach { suggestion -> PickRow(app, suggestion.quest) }
        }
    }
}

@Composable
private fun PickRow(app: AppState, quest: Quest) {
    val text = strings
    val open = {
        app.goToView(Views.home(quest, app.clock))
        app.selectedId = quest.id
    }
    val interaction = remember(quest.id) { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (hovered) theme.hover else Color.Transparent, RoundedCornerShape(Radii.md))
            // One device for this boundary: a hairline, since the row shares
            // the card's value (DESIGN §7).
            .border(1.dp, theme.border, RoundedCornerShape(Radii.md))
            .focusRing(RoundedCornerShape(Radii.md))
            .clickableRow(interaction = interaction, onClick = open)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                quest.title,
                style = MaterialTheme.typography.titleSmall,
                color = theme.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text.questMeta(quest.estimatedMinutes, quest.projectedXp()),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = theme.textMuted,
                maxLines = 1,
            )
        }
        QuestButton(
            text.actionStart,
            compact = true,
            primary = true,
            onClick = {
                app.store.markWorked(quest.id)
                open()
            },
        )
    }
}

/**
 * A quest on this screen: a row that opens the quest when clicked, with a
 * hover tint and a focus ring like any list row.
 */
@Composable
private fun QuestLine(onOpen: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(if (hovered) theme.hover else Color.Transparent, RoundedCornerShape(Radii.sm))
            .focusRing(RoundedCornerShape(Radii.sm))
            .clickableRow(interaction = interaction, onClick = onOpen)
            .padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        content = content,
    )
}

/** "+3 more": the way from a card's short list into the whole one. */
@Composable
private fun MoreLink(label: String, onClick: () -> Unit) {
    QuestLine(onOpen = onClick) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = theme.textMuted, maxLines = 1)
    }
}

/** A progress bar that moves to its value rather than jumping there (§7.6). */
@Composable
private fun FillingBar(fraction: Float, colour: Color, height: Dp) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.tween(Motion.MEDIUM))
    ProgressBar(shown, color = colour, height = height)
}
