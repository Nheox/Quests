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
import cl.nheox.quests.domain.InventoryItem
import cl.nheox.quests.domain.Reward
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.components.EmptyState
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.IconAction
import cl.nheox.quests.ui.components.NumberField
import cl.nheox.quests.ui.components.Pill
import cl.nheox.quests.ui.components.QuestButton
import cl.nheox.quests.ui.components.QuestTextField
import cl.nheox.quests.ui.components.ToggleRow
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/**
 * Rewards: what the spendable currency is actually for.
 *
 * Escalating cost is the interesting mechanic — a reward can get more expensive
 * every N claims, so a treat that is fine occasionally does not stay cheap when
 * it becomes a habit. That is a self-regulating economy rather than a
 * willpower test.
 */
@Composable
public fun RewardsScreen(
    app: AppState,
    state: QuestsState,
    modifier: Modifier = Modifier,
) {
    val text = strings
    var name by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf<Int?>(10) }
    var increment by remember { mutableStateOf<Int?>(null) }
    var every by remember { mutableStateOf<Int?>(null) }
    val balance = state.profile.rewardPointBalance

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(text.navRewards, style = MaterialTheme.typography.headlineSmall, color = theme.text)
            Spacer(Modifier.weight(1f))
            Pill(text.pointsAmount(balance), color = theme.accent, leading = Glyph.COIN, bold = true)
        }
        HLine()

        Column(Modifier.fillMaxWidth().padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Box(Modifier.weight(1f)) {
                    QuestTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = text.fieldName,
                        onFocusChange = { app.typing = it },
                    )
                }
                NumberField(
                    value = cost,
                    onValueChange = { cost = it },
                    step = 5,
                    minimum = 1,
                    suffix = text.fieldCost,
                    onFocusChange = { app.typing = it },
                )
                QuestButton(
                    text = text.actionAdd,
                    primary = true,
                    enabled = name.isNotBlank() && (cost ?: 0) > 0,
                    onClick = {
                        app.store.createReward(
                            name = name,
                            cost = cost ?: 10,
                            costIncrement = increment,
                            incrementEvery = every,
                        )
                        name = ""
                        increment = null
                        every = null
                    },
                )
            }
            Spacer(Modifier.height(Space.xs))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(text.fieldCostIncrement, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
                NumberField(
                    value = increment,
                    onValueChange = { increment = it },
                    step = 1,
                    onFocusChange = { app.typing = it },
                )
                Text(text.fieldIncrementEvery, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
                NumberField(
                    value = every,
                    onValueChange = { every = it },
                    step = 1,
                    onFocusChange = { app.typing = it },
                )
            }
        }

        if (state.rewards.isEmpty()) {
            EmptyState(text.rewardsEmpty, glyph = Glyph.GIFT)
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Space.md)) {
            items(state.rewards.values.sortedBy { it.currentCost }, key = { it.id }) { reward ->
                RewardCard(app, reward, balance)
                Spacer(Modifier.height(Space.sm))
            }
        }
    }
}

@Composable
private fun RewardCard(app: AppState, reward: Reward, balance: Int) {
    val text = strings
    val affordable = balance >= reward.currentCost

    Row(
        Modifier
            .fillMaxWidth()
            .background(theme.card, RoundedCornerShape(Radii.md))
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        QuestIcon(Glyph.GIFT, size = 16.dp, tint = theme.progression)
        Column(Modifier.weight(1f)) {
            Text(reward.name, style = MaterialTheme.typography.titleMedium, color = theme.text)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (reward.claimCount > 0) {
                    Text(
                        "×${reward.claimCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textFaint,
                    )
                }
                if (reward.costIncrement != null && reward.incrementEvery != null) {
                    Text(
                        "+${reward.costIncrement} / ${reward.incrementEvery}",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textFaint,
                    )
                }
            }
        }
        Pill(
            text.pointsAmount(reward.currentCost),
            color = if (affordable) theme.accent else theme.textFaint,
            leading = Glyph.COIN,
        )
        QuestButton(
            text = text.actionClaim,
            primary = affordable,
            enabled = affordable,
            compact = true,
            onClick = {
                if (!app.store.claimReward(reward.id)) app.notify(text.messageNotEnoughPoints)
            },
        )
        IconAction(Glyph.TRASH, text.actionDelete, size = 26.dp, onClick = { app.store.deleteReward(reward.id) })
    }
}

/** The inventory: things owned rather than things to do. */
@Composable
public fun InventoryScreen(
    app: AppState,
    state: QuestsState,
    modifier: Modifier = Modifier,
) {
    val text = strings
    var name by remember { mutableStateOf("") }
    var consumable by remember { mutableStateOf(true) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.navInventory, style = MaterialTheme.typography.headlineSmall, color = theme.text)
        }
        HLine()

        Column(Modifier.fillMaxWidth().padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Box(Modifier.weight(1f)) {
                    QuestTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = text.fieldName,
                        onFocusChange = { app.typing = it },
                        onImeAction = {
                            if (name.isNotBlank()) {
                                app.store.createItem(name, consumable = consumable)
                                name = ""
                            }
                        },
                    )
                }
                QuestButton(text.actionAdd, primary = true, enabled = name.isNotBlank(), onClick = {
                    app.store.createItem(name, consumable = consumable)
                    name = ""
                })
            }
            ToggleRow(text.fieldConsumable, consumable, { consumable = it })
        }

        if (state.items.isEmpty()) {
            EmptyState(text.inventoryEmpty, glyph = Glyph.BOX)
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Space.md)) {
            items(state.items.values.sortedBy { it.name.lowercase() }, key = { it.id }) { item ->
                ItemCard(app, item)
                Spacer(Modifier.height(Space.sm))
            }
        }
    }
}

@Composable
private fun ItemCard(app: AppState, item: InventoryItem) {
    val text = strings
    Row(
        Modifier
            .fillMaxWidth()
            .background(theme.card, RoundedCornerShape(Radii.md))
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        QuestIcon(Glyph.BOX, size = 16.dp, tint = theme.accent)
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, color = theme.text)
            Text(
                if (item.consumable) text.fieldConsumable else text.fieldNone,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textFaint,
            )
        }
        Pill("×${item.quantity}", color = theme.textMuted)
        IconAction(Glyph.PLUS, text.actionRestock, size = 26.dp, onClick = { app.store.adjustItemStock(item.id, 1) })
        QuestButton(
            text = text.actionUse,
            compact = true,
            enabled = !item.consumable || item.quantity > 0,
            onClick = { app.store.useItem(item.id) },
        )
        IconAction(Glyph.TRASH, text.actionDelete, size = 26.dp, onClick = { app.store.deleteItem(item.id) })
    }
}

/**
 * The scratchpad: a place to put a thought before deciding what it is.
 *
 * Promotion into a real quest is the whole point. Forcing a difficulty rating
 * and a due date at capture time is how a capture tool stops getting used.
 */
@Composable
public fun ScratchpadScreen(
    app: AppState,
    state: QuestsState,
    modifier: Modifier = Modifier,
) {
    val text = strings
    var line by remember { mutableStateOf("") }
    val entries = state.globalNotes()

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(text.scratchpadGlobal, style = MaterialTheme.typography.headlineSmall, color = theme.text)
            Pill(entries.count { !it.done }.toString(), color = theme.textMuted)
        }
        HLine()

        Row(
            Modifier.fillMaxWidth().padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Box(Modifier.weight(1f)) {
                QuestTextField(
                    value = line,
                    onValueChange = { line = it },
                    placeholder = text.scratchpadPlaceholder,
                    onFocusChange = { app.typing = it },
                    onImeAction = {
                        if (line.isNotBlank()) {
                            app.store.addNote(line)
                            line = ""
                        }
                    },
                )
            }
            QuestButton(text.actionAdd, primary = true, enabled = line.isNotBlank(), onClick = {
                app.store.addNote(line)
                line = ""
            })
        }

        if (entries.isEmpty()) {
            EmptyState(text.messageNoQuests, glyph = Glyph.LIST)
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Space.md)) {
            items(entries, key = { it.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    IconAction(
                        glyph = if (entry.done) Glyph.CHECK else Glyph.MINUS,
                        description = text.actionDone,
                        size = 24.dp,
                        tint = if (entry.done) theme.success else theme.textFaint,
                        onClick = { app.store.toggleNote(entry.id) },
                    )
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (entry.done) theme.textFaint else theme.text,
                        textDecoration = if (entry.done) {
                            androidx.compose.ui.text.style.TextDecoration.LineThrough
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (entry.promotedToQuestId == null) {
                        QuestButton(text.actionPromoteNote, compact = true, glyph = Glyph.TARGET, onClick = {
                            app.store.promoteNote(entry.id)?.let { app.selectedId = it }
                        })
                    } else {
                        Pill(text.actionDone, color = theme.success)
                    }
                    IconAction(Glyph.TRASH, text.actionDelete, size = 24.dp, onClick = {
                        app.store.deleteNote(entry.id)
                    })
                }
            }
        }
    }
}
