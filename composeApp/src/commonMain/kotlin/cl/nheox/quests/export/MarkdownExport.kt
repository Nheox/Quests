package cl.nheox.quests.export

import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Dates
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.QuestState
import cl.nheox.quests.projection.QuestsState
import java.time.format.DateTimeFormatter

/**
 * A human-readable snapshot of the whole state, written next to the logs.
 *
 * The JSONL logs are the source of truth and they are readable, but they are
 * not *browsable*: answering "what am I supposed to be doing" from an event log
 * means replaying it in your head. This file is what makes the promise in §3.3
 * real — the user can open their quest list on a machine that has never had
 * Quests installed, from a phone, from a text editor, from `cat`.
 */
public object MarkdownExport {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    public fun render(state: QuestsState, clock: Clock): String = buildString {
        val today = clock.today()
        appendLine("# Quests")
        appendLine()
        appendLine("_Generated ${dateFormat.format(today)}. The JSONL logs beside this file are the source of truth._")
        appendLine()

        val open = state.openQuests
        val overdue = open.filter { Dates.isOverdue(it.dueDate, it.dueTime, clock) }
        val dueToday = open.filter { it.dueDate == today && it !in overdue }
        val upcoming = open.filter { it.dueDate != null && it.dueDate.isAfter(today) }
        val undated = open.filter { it.dueDate == null }

        appendLine("## Summary")
        appendLine()
        appendLine("| | |")
        appendLine("|---|---|")
        appendLine("| Open quests | ${open.size} |")
        appendLine("| Overdue | ${overdue.size} |")
        appendLine("| Due today | ${dueToday.size} |")
        appendLine("| Completed, all time | ${state.completions.size} |")
        appendLine("| Failed, all time | ${state.failures.size} |")
        appendLine("| Reward points | ${state.profile.rewardPointBalance} |")
        appendLine("| Total XP | ${state.totalXp} |")
        appendLine()

        section("Overdue", overdue, state)
        section("Today", dueToday, state)
        section("Upcoming", upcoming, state)
        section("No due date", undated, state)

        if (state.skills.isNotEmpty()) {
            appendLine("## Skills")
            appendLine()
            appendLine("| Skill | Level | XP | To next level |")
            appendLine("|---|---:|---:|---:|")
            state.skills.values.sortedByDescending { it.totalXp }.forEach { skill ->
                appendLine("| ${escape(skill.name)} | ${skill.level} | ${skill.totalXp} | ${skill.xpToNextLevel} |")
            }
            appendLine()
        }

        if (state.rewards.isNotEmpty()) {
            appendLine("## Rewards")
            appendLine()
            appendLine("| Reward | Next cost | Claimed |")
            appendLine("|---|---:|---:|")
            state.rewards.values.sortedBy { it.name.lowercase() }.forEach { reward ->
                appendLine("| ${escape(reward.name)} | ${reward.currentCost} | ${reward.claimCount} |")
            }
            appendLine()
        }

        if (state.items.isNotEmpty()) {
            appendLine("## Inventory")
            appendLine()
            state.items.values.sortedBy { it.name.lowercase() }.forEach { item ->
                val kind = if (item.consumable) "consumable" else "permanent"
                appendLine("- ${escape(item.name)} ×${item.quantity} _($kind)_")
            }
            appendLine()
        }

        val recent = state.completions.sortedByDescending { it.at }.take(20)
        if (recent.isNotEmpty()) {
            appendLine("## Recently completed")
            appendLine()
            recent.forEach { record ->
                val when0 = dateFormat.format(record.at.atZone(clock.zone()).toLocalDate())
                appendLine("- `$when0` ${escape(record.title)} — ${record.xp} XP")
            }
            appendLine()
        }
    }

    private fun StringBuilder.section(title: String, quests: List<Quest>, state: QuestsState) {
        if (quests.isEmpty()) return
        appendLine("## $title")
        appendLine()
        quests.sortedWith(compareBy({ it.dueDate }, { it.dueTime }, { it.title.lowercase() })).forEach { quest ->
            appendLine(line(quest, state))
        }
        appendLine()
    }

    private fun line(quest: Quest, state: QuestsState): String = buildString {
        append(
            when (quest.state) {
                QuestState.OPEN -> "- [ ] "
                QuestState.DONE -> "- [x] "
                QuestState.FAILED -> "- [-] "
            },
        )
        append(escape(quest.title))
        val facts = mutableListOf<String>()
        quest.dueDate?.let { date ->
            facts += "due " + dateFormat.format(date) + (quest.dueTime?.let { " ${it}" } ?: "")
        }
        facts += quest.difficulty.name.lowercase().replace('_', ' ')
        if (quest.fear.index > 0) facts += "fear: ${quest.fear.name.lowercase()}"
        quest.estimatedMinutes?.let { facts += Dates.formatMinutes(it) }
        quest.rewardBonus?.takeIf { it > 0 }?.let { facts += "+$it bonus points" }
        val skills = state.skillsOf(quest)
        if (skills.isNotEmpty()) facts += skills.joinToString(", ") { "#" + it.name.replace(' ', '-') }
        if (facts.isNotEmpty()) {
            append("  \n      _")
            append(facts.joinToString(" · "))
            append("_")
        }
    }

    /** Keeps a quest title with a pipe or an underscore from breaking a table. */
    private fun escape(text: String): String = text
        .replace("|", "\\|")
        .replace("*", "\\*")
        .replace("_", "\\_")
}
