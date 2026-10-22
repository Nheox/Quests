package cl.nheox.quests.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/*
 * The catalogue (the owner's layout 2): things got with points, each with one
 * button. Rewards and the inventory use it today, the shop will.
 */

/**
 * A catalogue screen's header: its name, one line saying what it is for, and
 * whatever belongs at the right — the points the user has, the add button.
 */
@Composable
public fun CatalogueHeader(
    title: String,
    subtitle: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = theme.text, maxLines = 1)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** "● You have 148 points": the purse, in teal, at the right of a catalogue header. */
@Composable
public fun PointsBadge(points: Int) {
    Row(
        Modifier
            .background(theme.accent.copy(alpha = 0.16f), RoundedCornerShape(Radii.pill))
            .padding(horizontal = Space.md, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(Modifier.size(6.dp).background(theme.accent, CircleShape))
        Text(
            strings.youHavePoints(points),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = theme.accent,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** A section of a catalogue, in small capitals, without a rule. */
@Composable
public fun CatalogueSection(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = theme.textFaint,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = Space.lg, bottom = Space.sm),
    )
}

/**
 * One thing in a catalogue: a ring in its colour, its name and a line about
 * it, its price, a word on whether it can be had, and its one button.
 *
 * [available] false dims the name and the price, so what can be had now
 * stands out without a second colour; the status line says why not, in words.
 * [secondary] holds the quieter actions, in a reserved slot at the end.
 */
@Composable
public fun CatalogueRow(
    glyph: Glyph,
    tint: Color,
    name: String,
    description: String,
    available: Boolean,
    price: String?,
    status: String?,
    statusWarning: Boolean,
    action: @Composable () -> Unit,
    secondary: @Composable RowScope.() -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(Radii.md)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(theme.card, shape)
            .hoverable(interaction)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Box(
            Modifier.size(36.dp).background(theme.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(20.dp).border(2.dp, if (available) tint else tint.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                QuestIcon(glyph, size = 10.dp, tint = if (available) tint else tint.copy(alpha = 0.6f))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = if (available) theme.text else theme.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (price != null) {
            Row(
                Modifier.widthIn(min = 112.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                QuestIcon(Glyph.SPARK, size = 12.dp, tint = if (available) theme.progression else theme.textFaint)
                Text(
                    price,
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    color = if (available) theme.progression else theme.textMuted,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        Text(
            status.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = if (statusWarning) theme.warning else theme.textMuted,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(StatusWidth),
        )
        Box(Modifier.widthIn(min = 88.dp), contentAlignment = Alignment.Center) { action() }
        // Always there, so the keyboard can reach them; quiet until the
        // pointer is on the row, so the row does not read as a toolbar.
        Row(
            Modifier.width(SecondaryWidth).alpha(if (hovered) 1f else 0.45f),
            horizontalArrangement = Arrangement.spacedBy(Space.xxs, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            content = secondary,
        )
    }
}

/** Room for "112 more points needed", the longest status there is. */
private val StatusWidth = 168.dp

/** Room for two icon actions. */
private val SecondaryWidth = 56.dp

/**
 * The add button: the one filled control in a header, in teal, so the way to
 * add is always the most visible thing on a list or a catalogue.
 */
@Composable
public fun AddButton(description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(Radii.md)
    Box(
        Modifier
            .size(32.dp)
            .background(theme.accent.copy(alpha = if (hovered) 0.32f else 0.22f), shape)
            .focusRing(shape)
            .clickableRow(interaction = interaction, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        QuestIcon(Glyph.PLUS, size = 18.dp, tint = theme.accent)
    }
}
