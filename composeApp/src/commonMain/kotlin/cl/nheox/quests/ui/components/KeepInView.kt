package cl.nheox.quests.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Scrolls a list just far enough to show the item at [index], whenever the
 * index changes; -1 means nothing to show.
 *
 * Just far enough, so that walking down a list with the arrow keys moves the
 * list one row at a time at its edge, the way every list on the desktop does,
 * instead of jumping each new row to the top. An item not laid out at all is
 * far away, and is brought to the top.
 */
@Composable
public fun KeepInView(state: LazyListState, index: Int) {
    LaunchedEffect(index) {
        if (index < 0) return@LaunchedEffect
        val layout = state.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            state.scrollToItem(index)
            return@LaunchedEffect
        }
        val start = layout.viewportStartOffset + layout.beforeContentPadding
        val end = layout.viewportEndOffset - layout.afterContentPadding
        when {
            item.offset < start -> state.scrollBy((item.offset - start).toFloat())
            item.offset + item.size > end -> state.scrollBy((item.offset + item.size - end).toFloat())
        }
    }
}
