package cl.nheox.quests.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import cl.nheox.quests.ui.Shortcuts
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.KeyCap
import cl.nheox.quests.ui.components.SectionHeader
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/**
 * Help, in the app rather than in a wiki.
 *
 * The quick-add syntax table is the part that matters: it is the one piece of
 * this app with a syntax to learn, and a syntax nobody can look up is a syntax
 * nobody uses.
 */
@Composable
public fun HelpScreen(modifier: Modifier = Modifier) {
    val text = strings

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.helpTitle, style = MaterialTheme.typography.headlineSmall, color = theme.text)
        }
        HLine()

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.md)) {
            SectionHeader(text.helpShortcuts)
            Shortcuts.reference().forEach { (keys, description) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Row(
                        Modifier.width(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
                    ) {
                        keys.split('+').forEach { KeyCap(it) }
                    }
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = theme.textMuted)
                }
            }

            SectionHeader(text.helpQuickAdd)
            Text(text.helpQuickAddBody, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
            Spacer(Modifier.height(Space.sm))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(theme.card, RoundedCornerShape(Radii.md))
                    .padding(Space.md),
            ) {
                Text(
                    "write methods tomorrow 3pm #thesis !! ~2h",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.progression,
                )
                Spacer(Modifier.height(Space.md))
                QUICK_ADD_SYNTAX.forEach { (token, meaning) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Space.xxs),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        Text(
                            token,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.accent,
                            modifier = Modifier.width(150.dp),
                        )
                        Text(meaning, style = MaterialTheme.typography.bodySmall, color = theme.textMuted)
                    }
                }
            }

            SectionHeader(text.helpHotkeyTitle)
            Text(HOTKEY_HELP, style = MaterialTheme.typography.bodyMedium, color = theme.textMuted)

            SectionHeader(text.helpCalendarTitle)
            Text(CALENDAR_HELP, style = MaterialTheme.typography.bodyMedium, color = theme.textMuted)

            SectionHeader(text.helpSyncTitle)
            Text(SYNC_HELP, style = MaterialTheme.typography.bodyMedium, color = theme.textMuted)

            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/**
 * The quick-add grammar, documented where it is used.
 *
 * Kept as data rather than prose so the help screen and docs/SPEC.md cannot
 * drift apart without somebody noticing.
 */
internal val QUICK_ADD_SYNTAX: List<Pair<String, String>> = listOf(
    "#thesis  #\"lab work\"" to "link a skill, creating it if it is new",
    "* to *******" to "difficulty, one star (trivial) to seven (extreme)",
    "^ to ^^^^" to "fear, mild to severe",
    "!  !!" to "urgency: high, critical",
    "d:high  f:strong  u:low" to "the same three, spelled out",
    "~2h  ~90m  ~1h30m  ~45" to "estimated duration",
    "today  tomorrow  mon…sun" to "due date",
    "next week  +3d  +2w" to "relative due date",
    "2026-10-20  20/10" to "absolute due date, day first",
    "3pm  15:00  at 9:30am" to "due time; implies today if no date given",
    "every day  every 2 weeks" to "repetition",
    "daily  weekly  monthly" to "repetition, shorthand",
)

private val HOTKEY_HELP: String = """
    Quests does not grab a global key itself. On Wayland an application cannot
    reliably do that — GNOME stopped honouring XWayland key grabs, the
    replacement portal expects a native Wayland client, and the JVM has no
    Wayland backend yet. wlroots compositors have no portal backend for it at
    all.

    So the compositor owns the shortcut instead. Bind a key of your choice to
    the command `quests --capture`, and the already-running Quests will open its
    capture window. This works identically on X11 and Wayland, on GNOME, KDE and
    Sway.

    On GNOME, Settings can install the binding for you. Everywhere else, add it
    in your desktop's own keyboard settings.
""".trimIndent()

private val CALENDAR_HELP: String = """
    Desktop notifications only reach you when the machine is awake and you are
    sitting at it. If you keep your phone out of reach while you work, a 3pm
    desktop reminder reaches nobody.

    So Quests writes a calendar feed instead: export/quests.ics inside your
    folder, containing every quest with a due date and its reminders. Subscribe
    your phone or your system calendar to that file, and the alerts arrive
    through an app you already trust to interrupt you. Quests never has to run
    on your phone, and never has to talk to a network.
""".trimIndent()

private val SYNC_HELP: String = """
    Everything is plain text in the folder you chose. Each device writes only
    its own events-<device>.jsonl, so two machines can never write the same file
    and no sync tool can produce a conflict copy. On launch, Quests reads every
    log it can see and replays the union.

    Sync the folder with whatever you already use: Syncthing, Git, Nextcloud,
    iCloud Drive, Dropbox. Quests itself contains no network code.
""".trimIndent()
