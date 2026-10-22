package cl.nheox.quests.export

import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Settings
import cl.nheox.quests.events.Vault
import cl.nheox.quests.projection.QuestsState
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Writes the generated files into the vault's `export/` folder.
 *
 * Both files are written atomically, via a temporary file and a rename. A
 * calendar client polling the feed while it is half-written would either fail to
 * parse it or — worse — import a truncated one and quietly drop the rest of the
 * user's reminders.
 */
public class Exporter(
    private val vault: Vault,
    private val clock: Clock,
) {
    public fun exportAll(state: QuestsState, settings: Settings = state.settings): Result {
        var ics: Path? = null
        var markdown: Path? = null
        val errors = mutableListOf<String>()

        if (settings.icsExportEnabled) {
            runCatching {
                ics = writeAtomically(vault.exportDir.resolve(ICS_NAME), IcsExport.render(state, clock))
            }.onFailure { errors += "calendar: ${it.message}" }
        }
        if (settings.markdownExportEnabled) {
            runCatching {
                markdown = writeAtomically(vault.exportDir.resolve(MARKDOWN_NAME), MarkdownExport.render(state, clock))
            }.onFailure { errors += "markdown: ${it.message}" }
        }
        return Result(ics, markdown, errors)
    }

    /**
     * Writes an exported branch to `export/branches/` and returns the path,
     * relative to the data folder, to tell the user where it went.
     */
    public fun writeBranch(fileName: String, markdown: String): String {
        val target = vault.exportDir.resolve(BRANCHES_DIR).resolve(fileName)
        writeAtomically(target, markdown)
        return vault.root.relativize(target).toString()
    }

    private fun writeAtomically(target: Path, content: String): Path {
        Files.createDirectories(target.parent)
        val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.write(
            temp,
            content.toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        return Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
    }

    public data class Result(
        val icsPath: Path?,
        val markdownPath: Path?,
        val errors: List<String> = emptyList(),
    )

    public companion object {
        public const val ICS_NAME: String = "quests.ics"
        public const val MARKDOWN_NAME: String = "quests.md"
        public const val BRANCHES_DIR: String = "branches"
    }
}
