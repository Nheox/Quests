package cl.nheox.quests.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.nheox.quests.projection.LevelUp
import cl.nheox.quests.projection.UndoLabel
import cl.nheox.quests.projection.Undoable
import cl.nheox.quests.ui.components.QuestButton
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme
import kotlinx.coroutines.delay

/**
 * The undo snackbar, replacing the original app's confirmation dialogs (§7.6).
 *
 * A dialog asks "are you sure" before every destructive action and so taxes the
 * common case to protect the rare one. Undo inverts that: the action happens
 * immediately, and the escape hatch costs nothing because the event log already
 * makes the inverse trivially expressible.
 */
@Composable
public fun UndoBar(
    undoable: Undoable?,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = strings

    // Long enough to notice and act on, short enough not to sit in the way.
    LaunchedEffect(undoable) {
        if (undoable != null) {
            delay(7_000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = undoable != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val action = undoable ?: return@AnimatedVisibility
        val (glyph, label, detail) = when (action) {
            is Undoable.Completed -> Triple(Glyph.CHECK, text.messageQuestCompleted, action.title)
            is Undoable.Failed -> Triple(Glyph.CROSS, text.messageQuestFailed, action.title)
            is Undoable.Deleted -> Triple(Glyph.TRASH, text.messageQuestDeleted, action.title)
            is Undoable.Claimed -> Triple(Glyph.GIFT, text.messageRewardClaimed, action.name)
            is Undoable.Moved -> Triple(Glyph.TREE, text.questsMoved(action.moves.size), text.movedDetail(action.parentTitle))
        }

        Row(
            Modifier
                .padding(Space.md)
                .widthIn(min = 320.dp)
                .background(theme.card, RoundedCornerShape(Radii.md))
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            QuestIcon(
                glyph,
                size = 14.dp,
                tint = if (action.label == UndoLabel.COMPLETED) theme.success else theme.textMuted,
            )
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = theme.text)
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textFaint,
                        maxLines = 1,
                    )
                }
            }
            QuestButton(text.actionUndo, compact = true, glyph = Glyph.UNDO, onClick = onUndo)
        }
    }
}

/** Level-ups, announced once and then forgotten. */
@Composable
public fun LevelUpBar(
    levelUps: List<LevelUp>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = strings

    LaunchedEffect(levelUps) {
        if (levelUps.isNotEmpty()) {
            delay(6_000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = levelUps.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(horizontal = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            levelUps.takeLast(3).forEach { levelUp ->
                Row(
                    Modifier
                        .background(theme.progression.copy(alpha = 0.18f), RoundedCornerShape(Radii.md))
                        .padding(horizontal = Space.md, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    QuestIcon(Glyph.SPARK, size = 14.dp, tint = theme.progression)
                    Text(
                        text.levelUpMessage(levelUp.skillName, levelUp.toLevel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.progression,
                    )
                }
            }
        }
    }
}

/** Transient one-line feedback for things with no inverse to offer. */
@Composable
public fun MessageBar(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(4_000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .padding(Space.md)
                .background(theme.card, RoundedCornerShape(Radii.md))
                .padding(horizontal = Space.md, vertical = Space.sm),
        ) {
            Text(
                message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textMuted,
            )
        }
    }
}
