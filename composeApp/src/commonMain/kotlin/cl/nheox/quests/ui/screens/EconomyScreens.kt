package cl.nheox.quests.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import cl.nheox.quests.ui.components.AddButton
import cl.nheox.quests.ui.components.PointsBadge
import cl.nheox.quests.ui.components.CatalogueSection
import cl.nheox.quests.ui.components.CatalogueRow
import cl.nheox.quests.ui.components.CatalogueHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/**
 * Rewards: what the spendable currency is actually for, laid out as the
 * catalogue (the owner's layout 2).
 *
 * Escalating cost is the interesting mechanic — a reward can get more expensive
 * every N claims, so a treat that is fine occasionally does not stay cheap when
 * it becomes a habit. That is a self-regulating economy rather than a
 * willpower test. The list is split by what can be claimed now and what is
 * still being saved up for, cheapest first in each, and a reward out of reach
 * says how far off it is.
 */
@Composable
public fun RewardsScreen(
    app: AppState,
    state: QuestsState,
    modifier: Modifier = Modifier,
) {
    val text = strings
    var adding by remember { mutableStateOf(false) }
    val balance = state.profile.rewardPointBalance
    val (ready, saving) = state.rewards.values
        .sortedWith(compareBy({ it.currentCost }, { it.name.lowercase() }))
        .partition { balance >= it.currentCost }

    Column(modifier.fillMaxSize()) {
        CatalogueHeader(text.navRewards, text.rewardsSubtitle) {
            PointsBadge(balance)
            AddButton(text.actionNewReward, onClick = { adding = !adding })
        }
        HLine()
        if (adding) {
            NewRewardForm(app, onDone = { adding = false })
            HLine()
        }

        if (state.rewards.isEmpty()) {
            EmptyState(text.rewardsEmpty, glyph = Glyph.GIFT)
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.xl),
            contentPadding = PaddingValues(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (ready.isNotEmpty()) {
                item(key = "ready") { CatalogueSection(text.sectionReadyToClaim) }
                items(ready, key = { it.id }) { reward -> RewardRow(app, reward, balance) }
            }
            if (saving.isNotEmpty()) {
                item(key = "saving") { CatalogueSection(text.sectionSavingUp) }
                items(saving, key = { it.id }) { reward -> RewardRow(app, reward, balance) }
            }
        }
    }
}

@Composable
private fun RewardRow(app: AppState, reward: Reward, balance: Int) {
    val text = strings
    val affordable = balance >= reward.currentCost
    CatalogueRow(
        glyph = Glyph.GIFT,
        tint = theme.progression,
        name = reward.name,
        description = text.rewardDescription(reward.claimCount, reward.costIncrement, reward.incrementEvery),
        available = affordable,
        price = text.pointsAmount(reward.currentCost),
        status = if (affordable) null else text.morePointsNeeded(reward.currentCost - balance),
        statusWarning = !affordable,
        action = {
            QuestButton(
                text = text.actionClaim,
                primary = affordable,
                enabled = affordable,
                compact = true,
                onClick = { if (!app.store.claimReward(reward.id)) app.notify(text.messageNotEnoughPoints) },
            )
        },
        secondary = {
            IconAction(Glyph.TRASH, text.actionDelete, size = 26.dp, onClick = { app.store.deleteReward(reward.id) })
        },
    )
}

/** Name, cost and the optional escalation, behind the header's add button. */
@Composable
private fun NewRewardForm(app: AppState, onDone: () -> Unit) {
    val text = strings
    var name by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf<Int?>(10) }
    var increment by remember { mutableStateOf<Int?>(null) }
    var every by remember { mutableStateOf<Int?>(null) }
    val valid = name.isNotBlank() && (cost ?: 0) > 0
    fun save() {
        if (!valid) return
        app.store.createReward(name = name, cost = cost ?: 10, costIncrement = increment, incrementEvery = every)
        onDone()
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Box(Modifier.weight(1f)) {
                QuestTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = text.fieldName,
                    onFocusChange = { app.typing = it },
                    onImeAction = { save() },
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
            QuestButton(text = text.actionAdd, primary = true, enabled = valid, onClick = { save() })
        }
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(text.fieldCostIncrement, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
            NumberField(value = increment, onValueChange = { increment = it }, step = 1, onFocusChange = { app.typing = it })
            Text(text.fieldIncrementEvery, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
            NumberField(value = every, onValueChange = { every = it }, step = 1, onFocusChange = { app.typing = it })
        }
    }
}

/**
 * The inventory: things owned rather than things to do, as the catalogue.
 * Consumables, which run out, come before keepsakes, which do not.
 */
@Composable
public fun InventoryScreen(
    app: AppState,
    state: QuestsState,
    modifier: Modifier = Modifier,
) {
    val text = strings
    var adding by remember { mutableStateOf(false) }
    val (consumables, keepsakes) = state.items.values
        .sortedBy { it.name.lowercase() }
        .partition { it.consumable }

    Column(modifier.fillMaxSize()) {
        CatalogueHeader(text.navInventory, text.inventorySubtitle) {
            AddButton(text.actionNewItem, onClick = { adding = !adding })
        }
        HLine()
        if (adding) {
            NewItemForm(app, onDone = { adding = false })
            HLine()
        }

        if (state.items.isEmpty()) {
            EmptyState(text.inventoryEmpty, glyph = Glyph.BOX)
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.xl),
            contentPadding = PaddingValues(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (consumables.isNotEmpty()) {
                item(key = "consumables") { CatalogueSection(text.sectionConsumables) }
                items(consumables, key = { it.id }) { item -> ItemRow(app, item) }
            }
            if (keepsakes.isNotEmpty()) {
                item(key = "keepsakes") { CatalogueSection(text.sectionKeepsakes) }
                items(keepsakes, key = { it.id }) { item -> ItemRow(app, item) }
            }
        }
    }
}

@Composable
private fun ItemRow(app: AppState, item: InventoryItem) {
    val text = strings
    val usable = !item.consumable || item.quantity > 0
    CatalogueRow(
        glyph = Glyph.BOX,
        tint = theme.accent,
        name = item.name,
        description = text.itemDescription(item.consumable),
        available = usable,
        price = null,
        status = when {
            !item.consumable -> null
            item.quantity == 0 -> text.itemOutOfStock
            else -> text.youHave(item.quantity)
        },
        statusWarning = item.consumable && item.quantity == 0,
        action = {
            QuestButton(
                text = text.actionUse,
                primary = usable,
                enabled = usable,
                compact = true,
                onClick = { app.store.useItem(item.id) },
            )
        },
        secondary = {
            IconAction(Glyph.PLUS, text.actionRestock, size = 26.dp, onClick = { app.store.adjustItemStock(item.id, 1) })
            IconAction(Glyph.TRASH, text.actionDelete, size = 26.dp, onClick = { app.store.deleteItem(item.id) })
        },
    )
}

@Composable
private fun NewItemForm(app: AppState, onDone: () -> Unit) {
    val text = strings
    var name by remember { mutableStateOf("") }
    var consumable by remember { mutableStateOf(true) }
    fun save() {
        if (name.isBlank()) return
        app.store.createItem(name, consumable = consumable)
        onDone()
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
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
        ToggleRow(text.fieldConsumable, consumable, { consumable = it })
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
    onCopy: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val text = strings
    var line by remember { mutableStateOf("") }
    val entries = state.globalNotes()
    val finished = entries.count { it.done || it.promotedToQuestId != null }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(text.scratchpadGlobal, style = MaterialTheme.typography.headlineSmall, color = theme.text)
            Pill(entries.count { !it.done }.toString(), color = theme.textMuted)
            Spacer(Modifier.weight(1f))
            // As a Markdown checklist, which pastes as one into most notes apps.
            QuestButton(text.actionCopyAll, compact = true, glyph = Glyph.COPY, enabled = entries.isNotEmpty(), onClick = {
                val markdown = entries.joinToString("\n") { entry ->
                    (if (entry.done || entry.promotedToQuestId != null) "- [x] " else "- [ ] ") + entry.text
                }
                if (onCopy(markdown)) app.notify(text.messageCopied)
            })
            QuestButton(text.actionClearFinished, compact = true, glyph = Glyph.TRASH, enabled = finished > 0, onClick = {
                app.notify(text.scratchpadCleared(app.store.clearFinishedNotes()))
            })
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
