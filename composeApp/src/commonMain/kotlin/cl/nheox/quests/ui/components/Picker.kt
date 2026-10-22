package cl.nheox.quests.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/**
 * One choice in a picker.
 *
 * [detail] is a second, quieter line — a parent quest's path, say — and is
 * searched as well as [label]. [ring] is the small coloured ring a skill is
 * drawn with; null draws none.
 */
public data class PickerOption<T>(
    val value: T,
    val label: String,
    val detail: String? = null,
    val ring: Color? = null,
)

/** Which options a typed query keeps. Pure, so it is tested rather than tried. */
public object PickerSearch {
    /**
     * Every word typed must appear, in any order, in the label or the detail,
     * ignoring case — what people expect of a search box. An empty query
     * keeps everything, in the order given.
     */
    public fun <T> filter(options: List<PickerOption<T>>, query: String): List<PickerOption<T>> {
        val terms = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return options
        return options.filter { option ->
            val haystack = (option.label + " " + option.detail.orEmpty()).lowercase()
            terms.all { haystack.contains(it) }
        }
    }
}

/**
 * The picker (the owner's layout 4): a small box that opens next to a field
 * to choose a value — a search field, the options with a tick on the chosen
 * ones, an optional "+ New …" line, and the keys that work, said at the foot.
 *
 * Keyboard first: the search field has focus from the start, so typing
 * filters at once; ↑ and ↓ move the highlight, Enter chooses, Esc closes. The
 * highlight follows the pointer too, so mouse and keys never disagree about
 * which line Enter would choose.
 *
 * [create], when given, adds a last line that makes a new value from what was
 * typed ("+ New skill “Cooking”"). [closeOnPick] is false for a picker that
 * chooses several things, like skills, so the box stays open while they are
 * ticked.
 */
@Composable
public fun <T> PickerPanel(
    options: List<PickerOption<T>>,
    selected: Set<T>,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    searchPlaceholder: String,
    onTypingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    create: ((String) -> Unit)? = null,
    createLabel: (String) -> String = { it },
    closeOnPick: Boolean = true,
) {
    val text = strings
    var query by remember { mutableStateOf("") }
    val shown = remember(options, query) { PickerSearch.filter(options, query) }
    val canCreate = create != null
    val lines = shown.size + if (canCreate) 1 else 0
    var highlight by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, lines) { highlight = highlight.coerceIn(0, (lines - 1).coerceAtLeast(0)) }
    val listState = rememberLazyListState()
    LaunchedEffect(highlight) { if (lines > 0) listState.animateScrollToItem(highlight.coerceAtMost(lines - 1)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // The search field stops single-letter shortcuts while it has focus; make
    // sure that never outlives the picker, however it is closed.
    DisposableEffect(Unit) { onDispose { onTypingChange(false) } }

    fun choose(index: Int) {
        when {
            index < shown.size -> {
                onPick(shown[index].value)
                if (closeOnPick) onDismiss()
            }

            canCreate -> {
                create?.invoke(query.trim())
                query = ""
                if (closeOnPick) onDismiss()
            }
        }
    }

    val shape = RoundedCornerShape(Radii.lg)
    Column(
        modifier
            .width(PickerWidth)
            // Floating over the page, so it takes the card value and a hairline
            // edge: the value alone would vanish over a card behind it.
            .background(theme.card, shape)
            .border(1.dp, theme.border, shape)
            .padding(Space.sm)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        if (lines > 0) highlight = (highlight + 1) % lines
                        true
                    }

                    Key.DirectionUp -> {
                        if (lines > 0) highlight = (highlight - 1 + lines) % lines
                        true
                    }

                    Key.Enter, Key.NumPadEnter -> {
                        if (lines > 0) choose(highlight)
                        true
                    }

                    Key.Escape -> {
                        onDismiss()
                        true
                    }

                    else -> false
                }
            },
    ) {
        QuestTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = searchPlaceholder,
            onFocusChange = onTypingChange,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.focusRequester(focus),
        )
        Spacer(Modifier.height(Space.xs))

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), state = listState) {
            itemsIndexed(shown, key = { index, option -> "o$index:${option.label}" }) { index, option ->
                PickerLine(
                    highlighted = index == highlight,
                    onHover = { highlight = index },
                    onClick = { choose(index) },
                ) {
                    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                        if (option.ring != null) {
                            Box(Modifier.size(12.dp).border(1.5.dp, option.ring, CircleShape))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (option.detail != null) {
                            Text(
                                option.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // A fixed slot, so ticking never shifts the label.
                    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                        if (option.value in selected) QuestIcon(Glyph.CHECK, size = 14.dp, tint = theme.accent)
                    }
                }
            }
            if (canCreate) {
                item(key = "create") {
                    PickerLine(
                        highlighted = highlight == shown.size,
                        onHover = { highlight = shown.size },
                        onClick = { choose(shown.size) },
                    ) {
                        Text(
                            createLabel(query.trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (shown.isEmpty() && !canCreate) {
            Text(
                text.messageNoResults,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMuted,
                modifier = Modifier.padding(Space.sm),
            )
        }

        HLine(Modifier.padding(vertical = Space.xs))
        Row(
            Modifier.padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            KeyCap("↑↓")
            HintWord(text.pickerMove)
            Spacer(Modifier.width(Space.xs))
            KeyCap("Enter")
            HintWord(text.pickerChoose)
            Spacer(Modifier.width(Space.xs))
            KeyCap("Esc")
            HintWord(text.pickerClose)
        }
    }
}

private val PickerWidth = 300.dp

@Composable
private fun PickerLine(
    highlighted: Boolean,
    onHover: () -> Unit,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .background(if (highlighted) theme.accent.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(Radii.md))
            .clickableRow(interaction = interaction, onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        content = content,
    )
}

/** A key, drawn as a quiet key cap. */
@Composable
public fun KeyCap(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = theme.textMuted,
        maxLines = 1,
        modifier = Modifier
            .background(theme.card, RoundedCornerShape(Radii.sm))
            .padding(horizontal = Space.xs + Space.xxs, vertical = Space.xxs),
    )
}

@Composable
private fun HintWord(word: String) {
    Text(word, style = MaterialTheme.typography.labelMedium, color = theme.textFaint, maxLines = 1)
}

/**
 * A picker that opens [below] the top of the control it belongs to — that
 * control's height — right-aligned with it.
 *
 * Focusable, so the search field takes the keyboard while it is open and the
 * list's single-letter shortcuts stay out of the way; a click outside closes
 * it.
 */
@Composable
public fun PickerPopup(
    open: Boolean,
    onDismiss: () -> Unit,
    below: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    if (!open) return
    val offset = with(androidx.compose.ui.platform.LocalDensity.current) { below.roundToPx() + Space.xs.roundToPx() }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, offset),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        content()
    }
}
