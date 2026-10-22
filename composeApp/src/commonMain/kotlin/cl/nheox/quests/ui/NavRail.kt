package cl.nheox.quests.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.nheox.quests.BuildInfo
import cl.nheox.quests.domain.QuestView
import cl.nheox.quests.domain.Views
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.components.EnergyBar
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.Pill
import cl.nheox.quests.ui.components.SectionHeader
import cl.nheox.quests.ui.components.clickableRow
import cl.nheox.quests.ui.components.focusRing
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/**
 * The navigation rail.
 *
 * Counts next to each view are the point of it: the rail is also the dashboard,
 * so "three overdue" is visible without opening the list. Views that are empty
 * and optional hide their count rather than showing a zero, because a column of
 * zeroes reads as failure.
 */
@Composable
public fun NavRail(
    app: AppState,
    state: QuestsState,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val energy = app.energy(state)

    Column(
        modifier
            .width(Space.railWidth)
            .fillMaxHeight()
            .background(theme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.sm),
    ) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            QuestIcon(Glyph.TARGET, size = 18.dp, tint = theme.progression)
            Text(text.appName, style = MaterialTheme.typography.titleMedium, color = theme.text)
        }

        HLine()
        Spacer(Modifier.height(Space.sm))

        // Profile strip: energy and the purse, the two numbers worth watching.
        Column(
            Modifier
                .fillMaxWidth()
                .background(theme.card, RoundedCornerShape(Radii.md))
                .padding(Space.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(text.energy, style = MaterialTheme.typography.labelMedium, color = theme.textFaint)
                Spacer(Modifier.weight(1f))
                Text(
                    "$energy · ${text.energyBand(cl.nheox.quests.domain.Energy.band(energy))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textMuted,
                )
            }
            Spacer(Modifier.height(Space.xs))
            EnergyBar(energy)
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                QuestIcon(Glyph.COIN, size = 12.dp, tint = theme.accent)
                Text(
                    text.pointsAmount(state.profile.rewardPointBalance),
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.accent,
                )
                Spacer(Modifier.weight(1f))
                QuestIcon(Glyph.SPARK, size = 12.dp, tint = theme.progression)
                Text(
                    state.totalXp.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.progression,
                )
            }
        }

        // The launch view leads, above the lists: it is where the app opens,
        // and the place to come back to for the whole picture.
        Spacer(Modifier.height(Space.sm))
        RailItem(text.navStanding, Glyph.SHIELD, app.route == Route.Standing, null) { app.go(Route.Standing) }

        SectionHeader(text.sectionQuests)
        PRIMARY_VIEWS.forEach { view ->
            val count = Views.filter(view, state.quests.values, app.clock).size
            RailItem(
                label = text.view(view),
                glyph = glyphFor(view),
                selected = app.currentView == view,
                count = count,
                emphasis = view == QuestView.OVERDUE && count > 0,
                onClick = { app.goToView(view) },
            )
        }

        SectionHeader(text.sectionProgress)
        RailItem(text.navSkills, Glyph.SPARK, app.route == Route.Skills, state.skills.size) {
            app.go(Route.Skills)
        }
        RailItem(text.navRewards, Glyph.GIFT, app.route == Route.Rewards, state.rewards.size) {
            app.go(Route.Rewards)
        }
        RailItem(text.navInventory, Glyph.BOX, app.route == Route.Inventory, state.items.size) {
            app.go(Route.Inventory)
        }
        RailItem(
            label = text.navScratchpad,
            glyph = Glyph.LIST,
            selected = app.route == Route.Scratchpad,
            count = state.globalNotes().count { !it.done },
        ) { app.go(Route.Scratchpad) }
        RailItem(text.navStats, Glyph.CHART, app.route == Route.Insight, null) { app.go(Route.Insight) }

        SectionHeader(text.sectionApp)
        RailItem(text.navCompleted, Glyph.CHECK, app.currentView == QuestView.COMPLETED, null) {
            app.goToView(QuestView.COMPLETED)
        }
        RailItem(text.navFailed, Glyph.CROSS, app.currentView == QuestView.FAILED, null) {
            app.goToView(QuestView.FAILED)
        }
        RailItem(text.navSettings, Glyph.GEAR, app.route == Route.Settings, null) { app.go(Route.Settings) }
        RailItem(text.navHelp, Glyph.HELP, app.route == Route.Help, null) { app.go(Route.Help) }

        Spacer(Modifier.height(Space.lg))
        Text(
            "${app.store.deviceId} · ${BuildInfo.VERSION}",
            style = MaterialTheme.typography.labelSmall,
            color = theme.textFaint,
            modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.sm),
        )
    }
}

private val PRIMARY_VIEWS = listOf(
    QuestView.TODAY,
    QuestView.TOMORROW,
    QuestView.OVERDUE,
    QuestView.NEXT,
    QuestView.PLAN,
    QuestView.ALL,
    QuestView.NO_DUE_DATE,
)

private fun glyphFor(view: QuestView): Glyph = when (view) {
    QuestView.TODAY -> Glyph.SUN
    QuestView.TOMORROW -> Glyph.CHEVRON_RIGHT
    QuestView.OVERDUE -> Glyph.BELL
    QuestView.NEXT -> Glyph.CALENDAR
    QuestView.PLAN -> Glyph.TREE
    QuestView.ALL -> Glyph.LIST
    QuestView.COMPLETED -> Glyph.CHECK
    QuestView.LAST_SEVEN_DAYS -> Glyph.CLOCK
    QuestView.FAILED -> Glyph.CROSS
    QuestView.NO_DUE_DATE -> Glyph.INBOX
}

@Composable
private fun RailItem(
    label: String,
    glyph: Glyph,
    selected: Boolean,
    count: Int?,
    emphasis: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) theme.accent.copy(alpha = 0.16f) else Color.Transparent,
                RoundedCornerShape(Radii.md),
            )
            .focusRing()
            .clickableRow(onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        QuestIcon(glyph, size = 14.dp, tint = if (selected) theme.accent else theme.textFaint)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) theme.text else theme.textMuted,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (count != null && count > 0) {
            Pill(
                count.toString(),
                color = if (emphasis) theme.danger else theme.textFaint,
                bold = emphasis,
            )
        }
    }
}
