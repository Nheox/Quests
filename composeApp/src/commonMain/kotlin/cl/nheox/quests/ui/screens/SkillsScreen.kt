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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.nheox.quests.domain.Skill
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.components.EmptyState
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.IconAction
import cl.nheox.quests.ui.components.Pill
import cl.nheox.quests.ui.components.ProgressBar
import cl.nheox.quests.ui.components.QuestButton
import cl.nheox.quests.ui.components.QuestTextField
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Palette
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/** Skills, with levels derived from stored XP and never the other way round. */
@Composable
public fun SkillsScreen(
    app: AppState,
    state: QuestsState,
    modifier: Modifier = Modifier,
) {
    val text = strings
    var newName by remember { mutableStateOf("") }
    val skills = state.skills.values.sortedWith(compareByDescending<Skill> { it.totalXp }.thenBy { it.name.lowercase() })

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(text.navSkills, style = MaterialTheme.typography.headlineSmall, color = theme.text)
            Pill(skills.size.toString(), color = theme.textMuted)
            Spacer(Modifier.weight(1f))
            Pill(text.xpAmount(state.totalXp.toInt()), color = theme.progression, leading = Glyph.SPARK, bold = true)
        }
        HLine()

        Row(
            Modifier.fillMaxWidth().padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Box(Modifier.weight(1f)) {
                QuestTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = text.fieldName,
                    onFocusChange = { app.typing = it },
                    onImeAction = {
                        if (newName.isNotBlank()) {
                            app.store.createSkill(newName)
                            newName = ""
                        }
                    },
                )
            }
            QuestButton(text.actionAdd, primary = true, enabled = newName.isNotBlank(), onClick = {
                app.store.createSkill(newName)
                newName = ""
            })
        }

        if (skills.isEmpty()) {
            EmptyState(text.skillsEmpty, glyph = Glyph.SPARK)
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Space.md)) {
            items(skills, key = { it.id }) { skill ->
                SkillCard(app, state, skill)
                Spacer(Modifier.height(Space.sm))
            }
        }
    }
}

@Composable
private fun SkillCard(app: AppState, state: QuestsState, skill: Skill) {
    val text = strings
    val colour = Palette.forSkill(skill.id)
    val linked = state.quests.values.count { skill.id in it.skillIds && it.isOpen }

    Column(
        Modifier
            .fillMaxWidth()
            .background(theme.card, RoundedCornerShape(Radii.md))
            .padding(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            QuestIcon(Glyph.SPARK, size = 16.dp, tint = colour)
            Column(Modifier.weight(1f)) {
                Text(skill.name, style = MaterialTheme.typography.titleMedium, color = theme.text)
                if (!skill.category.isNullOrBlank()) {
                    Text(skill.category, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
                }
            }
            Pill("${text.level} ${skill.level}", color = colour, bold = true)
            IconAction(Glyph.TRASH, text.actionDelete, size = 26.dp, onClick = { app.store.deleteSkill(skill.id) })
        }

        Spacer(Modifier.height(Space.sm))
        ProgressBar(skill.progressToNextLevel, color = colour)
        Spacer(Modifier.height(Space.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(
                "${skill.totalXp} ${text.xp}",
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMuted,
            )
            Text(
                "${skill.xpToNextLevel} ${text.xpToNext}",
                style = MaterialTheme.typography.bodySmall,
                color = theme.textFaint,
            )
            Spacer(Modifier.weight(1f))
            if (linked > 0) {
                Text(
                    text.countOpen(linked),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textFaint,
                )
            }
        }
    }
}
