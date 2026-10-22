package cl.nheox.quests

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.nheox.quests.i18n.Strings
import cl.nheox.quests.platform.AppPaths
import cl.nheox.quests.platform.DataFolder
import cl.nheox.quests.platform.FolderStatus
import cl.nheox.quests.ui.components.QuestButton
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme
import kotlinx.coroutines.launch
import java.nio.file.Path

/** Why a folder confirmed on first run did not open. */
internal enum class FirstRunFailure {
    /** Creating it, or writing a test file into it, failed. */
    CANNOT_CREATE,

    /** The choice could not be written to the config directory. */
    CANNOT_REMEMBER,

    /** The folder was writable but replaying it failed. */
    CANNOT_OPEN,
}

/**
 * First run: one question, where the data lives (§1.2), asked before anything
 * is loaded or written.
 *
 * Two ways to answer, because there are two kinds of user. Most want the
 * default and should get it in one click. The rest want the folder inside a
 * sync tool's folder, and the native chooser is where they can both find that
 * folder and create a new one inside it.
 *
 * The selected path is always on screen, with a line saying what is in it —
 * above all whether quests are already there — so choosing an existing synced
 * folder on a second computer is visibly the right move rather than a leap of
 * faith. The line is re-read whenever the window regains focus, which covers
 * the user who goes off to plug in a drive or create the folder in Finder.
 *
 * Every slot whose content depends on state — the status line, the error line
 * — is always laid out, so nothing jumps when a folder is picked or a problem
 * reported; the buttons are pinned to the bottom edge.
 */
@Composable
internal fun FirstRunContent(
    mark: Painter,
    suggested: Path,
    previousUnavailable: Boolean,
    chooseFolder: suspend (startingAt: Path) -> Path?,
    confirm: suspend (Path) -> FirstRunFailure?,
) {
    val text = strings
    val scope = rememberCoroutineScope()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val rootFocus = remember { FocusRequester() }

    var selected by remember { mutableStateOf(suggested) }
    var failure by remember { mutableStateOf<FirstRunFailure?>(null) }
    var busy by remember { mutableStateOf(false) }
    val status = remember(selected, windowFocused) { DataFolder.inspect(selected) }

    fun choose() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                chooseFolder(selected)?.let { chosen ->
                    selected = chosen
                    failure = null
                }
            } finally {
                busy = false
            }
        }
    }

    fun accept() {
        if (busy || !status.usable) return
        busy = true
        scope.launch {
            failure = confirm(selected)
            busy = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                    accept()
                    true
                } else {
                    false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = Space.xxl, vertical = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(mark, contentDescription = null, modifier = Modifier.size(MARK_SIZE))
            Spacer(Modifier.height(Space.lg))
            Text(
                text.firstRunTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = theme.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text.firstRunBody,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xl))

            if (previousUnavailable) {
                // Full-strength text rather than the warning colour, which
                // misses 4.5:1 on the light background (DESIGN §6).
                Text(
                    text.firstRunPreviousUnavailable,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm),
                )
            }

            FolderCard(selected, status, text)

            Box(Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = ERROR_SLOT)) {
                failure?.let { Problem(failureText(it, text)) }
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuestButton(text.firstRunChoose, onClick = ::choose, glyph = Glyph.FOLDER, enabled = !busy)
                QuestButton(
                    firstRunAction(selected, AppPaths.defaultVault(), status, text),
                    onClick = ::accept,
                    primary = true,
                    enabled = !busy && status.usable,
                )
            }
        }
    }

    LaunchedEffect(Unit) { rootFocus.requestFocus() }
}

/** The path and what is in it: a value shift from the window, no border (DESIGN §7). */
@Composable
private fun FolderCard(folder: Path, status: FolderStatus, text: Strings) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(theme.card, RoundedCornerShape(Radii.lg))
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        QuestIcon(Glyph.FOLDER, size = 18.dp, tint = theme.textMuted)
        Column(Modifier.weight(1f)) {
            // Monospace is the point here (DESIGN §1): a path is read
            // character by character. The middle goes first when it is too
            // long, because the two ends are what identify a folder.
            Text(
                AppPaths.display(folder),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = theme.text,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            if (status.usable) {
                Text(
                    folderStatusText(status, text),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Problem(folderStatusText(status, text))
            }
        }
    }
}

/**
 * A problem, marked by a red glyph rather than red text: the danger colour
 * reads as a mark but misses 4.5:1 as body text on the dark card (DESIGN §6),
 * and colour must not carry the meaning alone anyway.
 */
@Composable
private fun Problem(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        QuestIcon(Glyph.CROSS, size = 14.dp, tint = theme.danger)
        Text(message, style = MaterialTheme.typography.bodySmall, color = theme.text)
    }
}

/**
 * The primary button's label. The default folder is named, because "Create
 * ~/Quests" says exactly what one click will do; any other folder is "this
 * folder", since its path is right above the button.
 */
internal fun firstRunAction(selected: Path, default: Path, status: FolderStatus, text: Strings): String {
    val creates = status == FolderStatus.Missing
    val isDefault = selected.toAbsolutePath().normalize() == default.toAbsolutePath().normalize()
    return when {
        isDefault && creates -> text.firstRunCreate(AppPaths.display(default))
        isDefault -> text.firstRunUse(AppPaths.display(default))
        creates -> text.firstRunCreateFolder
        else -> text.firstRunUseFolder
    }
}

internal fun folderStatusText(status: FolderStatus, text: Strings): String = when (status) {
    FolderStatus.Missing -> text.firstRunNewFolder
    FolderStatus.Empty -> text.firstRunEmptyFolder
    is FolderStatus.HasQuests -> text.firstRunExisting(status.devices)
    FolderStatus.HasOtherFiles -> text.firstRunOtherFiles
    FolderStatus.NotAFolder -> text.folderNotAFolder
    FolderStatus.NotWritable -> text.folderNotWritable
}

private fun failureText(failure: FirstRunFailure, text: Strings): String = when (failure) {
    FirstRunFailure.CANNOT_CREATE -> text.firstRunCannotCreate
    FirstRunFailure.CANNOT_REMEMBER -> text.folderCannotRemember(AppPaths.display(AppPaths.configDir()))
    FirstRunFailure.CANNOT_OPEN -> text.firstRunCannotOpen
}

private val MARK_SIZE = 72.dp

/** Two lines of error text, reserved whether or not there is an error to show. */
private val ERROR_SLOT = 32.dp
