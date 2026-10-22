package cl.nheox.quests.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.nheox.quests.domain.Skill
import cl.nheox.quests.domain.Stats
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.components.AddButton
import cl.nheox.quests.ui.components.EmptyState
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.IconAction
import cl.nheox.quests.ui.components.PickerOption
import cl.nheox.quests.ui.components.PickerPanel
import cl.nheox.quests.ui.components.PickerPopup
import cl.nheox.quests.ui.components.ProgressBar
import cl.nheox.quests.ui.components.QuestButton
import cl.nheox.quests.ui.components.QuestTextField
import cl.nheox.quests.ui.components.RadarAxis
import cl.nheox.quests.ui.components.RadarChart
import cl.nheox.quests.ui.components.clickableRow
import cl.nheox.quests.ui.components.focusRing
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Palette
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme
import java.time.LocalDate

/** How the skill list is ordered within each category. */
private enum class SkillSort { LEVEL, NAME, RECENT }

/** The radar's default: the strongest few, more axes than this read as noise. */
private const val RADAR_DEFAULT = 8

/** The Balance panel beside the list, and the width below which it moves under it. */
private val BalanceWidth = 360.dp
private val SideBySide = 860.dp

/**
 * Skills, as the progress list (the owner's layout 3): every skill a row with
 * its level and a bar for how close it is to the next, grouped by category,
 * and the Balance radar beside them.
 *
 * Levels are derived from stored XP and never the other way round (§4). The
 * bar and its figures are progress through the current level — "31 / 340 XP"
 * — so the numbers always say what the bar shows.
 */
@Composable
public fun SkillsScreen(
    app: AppState,
    state: QuestsState,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val today = app.clock.today()
    var adding by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(SkillSort.LEVEL) }
    val lastWorked = remember(state, today) {
        Stats.skillGrowth(state, app.clock).associate { it.skillId to it.lastActive }
    }
    val groups = remember(state, sort, lastWorked) { groupSkills(state.skills.values, sort, lastWorked, text.skillsUncategorised) }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(text.navSkills, style = MaterialTheme.typography.headlineSmall, color = theme.text, maxLines = 1)
            Spacer(Modifier.weight(1f))
            SortButton(sort, onSort = { sort = it })
            AddButton(text.actionNewSkill, onClick = { adding = !adding })
        }
        HLine()
        if (adding) {
            NewSkillForm(app, onDone = { adding = false })
            HLine()
        }

        if (state.skills.isEmpty()) {
            EmptyState(text.skillsEmpty, glyph = Glyph.SPARK)
            return@Column
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= SideBySide
            val list: @Composable (Modifier) -> Unit = { listModifier ->
                LazyColumn(
                    listModifier.padding(horizontal = Space.xl),
                    contentPadding = PaddingValues(bottom = Space.xl),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    groups.forEach { (category, skills) ->
                        val isCollapsed = collapsed[category] == true
                        item(key = "group-$category") {
                            GroupHeader(category, skills.size, isCollapsed) { collapsed[category] = !isCollapsed }
                        }
                        if (!isCollapsed) {
                            items(skills, key = { it.id }) { skill ->
                                SkillRow(app, skill, lastWorked[skill.id], today)
                            }
                        }
                    }
                    if (!wide) item(key = "balance") { BalancePanel(app, state, Modifier.fillMaxWidth().padding(top = Space.lg)) }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    list(Modifier.weight(1f).fillMaxHeight())
                    Box(Modifier.width(1.dp).fillMaxHeight().background(theme.border))
                    BalancePanel(app, state, Modifier.width(BalanceWidth).fillMaxHeight())
                }
            } else {
                list(Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Skills by category — categories by name, the unnamed ones last as "Other" —
 * and within each, by the chosen order. Pure, so the screen only draws it.
 */
private fun groupSkills(
    skills: Collection<Skill>,
    sort: SkillSort,
    lastWorked: Map<String, LocalDate?>,
    uncategorised: String,
): List<Pair<String, List<Skill>>> {
    val order: Comparator<Skill> = when (sort) {
        SkillSort.LEVEL -> compareByDescending<Skill> { it.totalXp }.thenBy { it.name.lowercase() }
        SkillSort.NAME -> compareBy { it.name.lowercase() }
        SkillSort.RECENT -> compareByDescending<Skill> { lastWorked[it.id] ?: LocalDate.MIN }.thenBy { it.name.lowercase() }
    }
    return skills
        .groupBy { it.category?.trim()?.takeIf(String::isNotEmpty) }
        .entries
        .sortedWith(compareBy({ it.key == null }, { it.key?.lowercase() }))
        .map { (category, members) -> (category ?: uncategorised) to members.sortedWith(order) }
}

@Composable
private fun SortButton(sort: SkillSort, onSort: (SkillSort) -> Unit) {
    val text = strings
    var open by remember { mutableStateOf(false) }
    fun label(value: SkillSort) = when (value) {
        SkillSort.LEVEL -> text.skillSortLevel
        SkillSort.NAME -> text.skillSortName
        SkillSort.RECENT -> text.skillSortRecent
    }
    Box {
        QuestButton(text.sortedBy(label(sort)), compact = true, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = theme.card) {
            SkillSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), color = if (option == sort) theme.accent else theme.text) },
                    onClick = {
                        onSort(option)
                        open = false
                    },
                )
            }
        }
    }
}

/** "▾ RESEARCH 3": a category, folded or open. */
@Composable
private fun GroupHeader(category: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .padding(top = Space.md)
            .focusRing(RoundedCornerShape(Radii.sm))
            .clickableRow(onClick = onToggle)
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        QuestIcon(if (collapsed) Glyph.CHEVRON_RIGHT else Glyph.CHEVRON_DOWN, size = 14.dp, tint = theme.textFaint)
        Text(
            category.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = theme.textFaint,
            letterSpacing = 1.sp,
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = theme.textFaint,
        )
    }
}

/**
 * One skill: a ring in its colour, its name and when it was last worked on,
 * its XP against the next level with a bar, and its level. A skill with no
 * XP says so instead of drawing an empty bar.
 */
@Composable
private fun SkillRow(app: AppState, skill: Skill, lastWorked: LocalDate?, today: LocalDate) {
    val text = strings
    val colour = Palette.forSkill(skill.id)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(theme.card, RoundedCornerShape(Radii.md))
            .hoverable(interaction)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Box(Modifier.size(36.dp).background(theme.background, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(20.dp).border(2.dp, colour, CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Text(
                skill.name,
                style = MaterialTheme.typography.titleMedium,
                color = theme.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                lastWorked?.let { text.lastWorkedOn(it, today) } ?: text.skillNotStarted,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMuted,
                maxLines = 1,
            )
        }
        if (skill.totalXp == 0L) {
            Text(
                text.skillNoXp,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMuted,
                modifier = Modifier.width(ProgressWidth + LevelWidth + Space.lg),
            )
        } else {
            Column(Modifier.width(ProgressWidth)) {
                Text(
                    // Within the level, so the numbers say what the bar shows.
                    text.xpProgress(skill.xpIntoLevel, skill.xpForNextLevel),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = theme.textMuted,
                    maxLines = 1,
                )
                Spacer(Modifier.height(Space.xs))
                ProgressBar(skill.progressToNextLevel, color = colour)
            }
            Box(Modifier.width(LevelWidth), contentAlignment = Alignment.CenterEnd) {
                Text(
                    "${text.level} ${skill.level}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = colour,
                    maxLines = 1,
                    modifier = Modifier
                        .background(colour.copy(alpha = 0.16f), RoundedCornerShape(Radii.pill))
                        .padding(horizontal = Space.md, vertical = Space.xs),
                )
            }
        }
        // Always there for the keyboard, quiet until the row is pointed at.
        Box(Modifier.alpha(if (hovered) 1f else 0.45f)) {
            IconAction(Glyph.TRASH, text.actionDelete, size = 26.dp, onClick = { app.store.deleteSkill(skill.id) })
        }
    }
}

private val ProgressWidth = 220.dp
private val LevelWidth = 96.dp

/**
 * Balance: the radar across the chosen skills — the strongest few unless
 * others were chosen — each axis normalised against the strongest, so the
 * shape answers "where is my effort going" rather than "how much XP is a lot".
 */
@Composable
private fun BalancePanel(app: AppState, state: QuestsState, modifier: Modifier) {
    val text = strings
    var choosing by remember { mutableStateOf(false) }
    val strongest = state.skills.values.sortedByDescending { it.totalXp }
    val chosenIds = app.radarSkills ?: strongest.take(RADAR_DEFAULT).map { it.id }.toSet()
    val chosen = strongest.filter { it.id in chosenIds }
    val peak = (chosen.maxOfOrNull { it.totalXp } ?: 0L).coerceAtLeast(1L)

    Column(modifier.padding(Space.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text.skillsBalance, style = MaterialTheme.typography.titleLarge, color = theme.text, modifier = Modifier.weight(1f))
            Box {
                QuestButton(text.skillsChoose, compact = true, onClick = { choosing = true })
                PickerPopup(open = choosing, onDismiss = { choosing = false }, below = 32.dp) {
                    PickerPanel(
                        options = strongest.sortedBy { it.name.lowercase() }
                            .map { PickerOption(it.id, it.name, ring = Palette.forSkill(it.id)) },
                        selected = chosenIds,
                        onPick = { id -> app.radarSkills = if (id in chosenIds) chosenIds - id else chosenIds + id },
                        onDismiss = { choosing = false },
                        searchPlaceholder = text.pickerSearchSkills,
                        onTypingChange = { app.typing = it },
                        closeOnPick = false,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.lg))
        RadarChart(
            axes = chosen.map { skill ->
                RadarAxis(
                    label = skill.name,
                    value = skill.totalXp.toFloat() / peak.toFloat(),
                    color = Palette.forSkill(skill.id),
                )
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            labelled = true,
        )
        Spacer(Modifier.height(Space.md))
        Text(
            text.skillsBalanceCaption,
            style = MaterialTheme.typography.bodySmall,
            color = theme.textMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun NewSkillForm(app: AppState, onDone: () -> Unit) {
    val text = strings
    var name by remember { mutableStateOf("") }
    fun save() {
        if (name.isBlank()) return
        app.store.createSkill(name)
        onDone()
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(Modifier.weight(1f)) {
            QuestTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = text.fieldName,
                onFocusChange = { app.typing = it },
                onImeAction = { save() },
            )
        }
        QuestButton(text.actionAdd, primary = true, enabled = name.isNotBlank(), onClick = { save() })
    }
}
