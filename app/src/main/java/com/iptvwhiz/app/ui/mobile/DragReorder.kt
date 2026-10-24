package com.iptvwhiz.app.ui.mobile

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Drag-to-reorder for a LazyColumn whose items are keyed. The dragged row follows the finger;
 * when its centre passes a neighbour, [onMove] swaps them in the caller's list.
 */
class DragReorderState(private val listState: LazyListState) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    fun start(key: Any) {
        draggingKey = key
        offset = 0f
    }

    /** Returns (from, to) list indices when the row should swap places. */
    fun drag(deltaY: Float): Pair<Int, Int>? {
        val key = draggingKey ?: return null
        offset += deltaY
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key } ?: return null
        val centre = current.offset + offset + current.size / 2f
        val target = items.firstOrNull {
            it.key != key && centre >= it.offset && centre <= it.offset + it.size
        } ?: return null
        // Once the row takes the target's slot, its layout position jumps; cancel that out so it stays under the finger.
        val newOffset = if (target.index > current.index) target.offset + target.size - current.size else target.offset
        offset += current.offset - newOffset
        return current.index to target.index
    }

    fun end() {
        draggingKey = null
        offset = 0f
    }
}

@Composable
fun rememberDragReorderState(listState: LazyListState) = remember(listState) { DragReorderState(listState) }

/** Lifts and offsets the row being dragged. Apply to each row. */
fun Modifier.draggableRow(state: DragReorderState, key: Any): Modifier {
    val dragging = state.draggingKey == key
    return zIndex(if (dragging) 1f else 0f).graphicsLayer {
        translationY = if (dragging) state.offset else 0f
        shadowElevation = if (dragging) 12f else 0f
    }
}

/** The ≡ handle that starts a drag. [onDrop] saves the new order. */
fun Modifier.dragHandle(
    state: DragReorderState,
    key: Any,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: (key: Any) -> Unit,
): Modifier = pointerInput(key) {
    detectDragGestures(
        onDragStart = { state.start(key) },
        onDragEnd = {
            state.end()
            onDrop(key)
        },
        onDragCancel = {
            state.end()
            onDrop(key)
        },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)?.let { (from, to) -> onMove(from, to) }
        },
    )
}
