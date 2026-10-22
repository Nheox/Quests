package cl.nheox.quests.export

import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Priority
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.QuestState
import cl.nheox.quests.projection.QuestsState
import java.text.Normalizer
import java.time.format.DateTimeFormatter

/**
 * One quest and everything under it, as a Markdown outline (§7.1, "export a
 * branch"): a thesis chapter by chapter, a trip with its errands, to paste
 * into a document or send to someone.
 *
 * Finished sub-quests are kept, ticked, because a branch is often exported to
 * show how far it has come. Each level is in the order the Plan shows it: open
 * quests by priority, then the finished ones, oldest first.
 */
public object BranchExport {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** The branch under [rootId], or null if there is no such quest. */
    public fun render(state: QuestsState, rootId: String, clock: Clock): String? {
        val root = state.quests[rootId] ?: return null
        return buildString {
            appendLine("# ${MarkdownExport.escape(root.title)}")
            appendLine()
            val facts = MarkdownExport.facts(root, state)
            if (facts.isNotEmpty()) {
                appendLine("_${facts.joinToString(" · ")}_")
                appendLine()
            }
            if (root.notes.isNotBlank()) {
                appendLine(root.notes.trim())
                appendLine()
            }
            val seen = mutableSetOf(root.id)
            fun walk(parent: Quest, depth: Int) {
                children(state, parent.id, clock).forEach { child ->
                    if (!seen.add(child.id)) return@forEach
                    item(child, state, depth)
                    walk(child, depth + 1)
                }
            }
            walk(root, 0)
            appendLine()
            appendLine("_Exported from Quests on ${dateFormat.format(clock.today())}._")
        }
    }

    /**
     * "write-the-thesis-k3m9qz.md": the title, made safe for any file system,
     * and the end of the quest's id, so two quests with one title never
     * overwrite each other's export while the same quest always replaces its
     * own.
     */
    public fun fileName(quest: Quest): String {
        val slug = Normalizer.normalize(quest.title, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(MAX_SLUG)
            .trimEnd('-')
            .ifEmpty { "quest" }
        return "$slug-${quest.id.takeLast(6).lowercase()}.md"
    }

    private fun children(state: QuestsState, id: String, clock: Clock): List<Quest> {
        val (open, finished) = state.children(id).partition { it.isOpen }
        return Priority.order(open, clock) + finished.sortedWith(compareBy({ it.completedAt ?: it.updatedAt }, { it.id }))
    }

    private fun StringBuilder.item(quest: Quest, state: QuestsState, depth: Int) {
        val indent = "  ".repeat(depth)
        val box = when (quest.state) {
            QuestState.OPEN -> "[ ]"
            QuestState.DONE -> "[x]"
            QuestState.FAILED -> "[-]"
        }
        append(indent).append("- ").append(box).append(' ').append(MarkdownExport.escape(quest.title))
        val facts = MarkdownExport.facts(quest, state)
        if (facts.isNotEmpty()) append(" — _").append(facts.joinToString(" · ")).append('_')
        appendLine()
        // Notes sit under their item, indented to its text, so they stay part
        // of it in any Markdown reader.
        quest.notes.trim().takeIf { it.isNotEmpty() }?.lines()?.forEach { line ->
            append(indent).append("  ").appendLine(line)
        }
    }

    private const val MAX_SLUG = 60
}
