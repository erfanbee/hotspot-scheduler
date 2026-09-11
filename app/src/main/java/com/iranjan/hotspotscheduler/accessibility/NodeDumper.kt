package com.iranjan.hotspotscheduler.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.iranjan.hotspotscheduler.data.model.NodeDump

object NodeDumper {

    private const val MAX_NODES = 1500

    fun dump(root: AccessibilityNodeInfo?): List<NodeDump> {
        if (root == null) return emptyList()
        val result = mutableListOf<NodeDump>()
        val classCounters = HashMap<String, Int>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var count = 0
        while (queue.isNotEmpty() && count < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            count++
            val cls = node.className?.toString() ?: "null"
            val index = classCounters.getOrDefault(cls, 0)
            classCounters[cls] = index + 1
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            result.add(
                NodeDump(
                    depth = depth,
                    className = cls,
                    viewId = node.viewIdResourceName ?: "",
                    text = node.text?.toString() ?: "",
                    contentDescription = node.contentDescription?.toString() ?: "",
                    bounds = "${bounds.left},${bounds.top}-${bounds.right},${bounds.bottom}",
                    clickable = node.isClickable,
                    checkable = node.isCheckable,
                    checked = node.isChecked,
                    siblingIndexOfClass = index
                )
            )
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { queue.add(it to depth + 1) }
                } catch (t: Throwable) {
                }
            }
        }
        return result
    }
}
