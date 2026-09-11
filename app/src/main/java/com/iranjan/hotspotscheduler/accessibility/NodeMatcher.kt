package com.iranjan.hotspotscheduler.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.iranjan.hotspotscheduler.data.model.CALIB_TYPE_CLASS_SIG
import com.iranjan.hotspotscheduler.data.model.CALIB_TYPE_RID
import com.iranjan.hotspotscheduler.data.model.CalibrationSignature

object NodeMatcher {

    const val SWITCH_WIDGET_ID = "com.android.settings:id/switch_widget"
    const val SWITCH_TEXT_ID = "com.android.settings:id/switch_text"
    private const val SWITCH_CLASS = "android.widget.Switch"
    private const val COMPOUND_BUTTON_CLASS = "android.widget.CompoundButton"
    private const val MAX_NODES = 1500
    private const val MAX_ANCESTOR_HOPS = 6

    fun findToggle(root: AccessibilityNodeInfo?, calibration: CalibrationSignature?): ToggleMatch? {
        if (root == null) return null
        calibration?.let { sig ->
            findCalibrated(root, sig)?.let { return it }
        }
        findByResourceId(root)?.let { return it }
        findByClass(root)?.let { return it }
        findByTextProximity(root)?.let { return it }
        return null
    }

    fun readState(match: ToggleMatch): Boolean? {
        val node = match.stateNode
        if (node.isCheckable) return node.isChecked
        var parent = node.parent ?: return null
        repeat(MAX_ANCESTOR_HOPS) {
            var found: Boolean? = null
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                if (sibling.viewIdResourceName == SWITCH_TEXT_ID) {
                    found = onOffToBoolean(sibling.text?.toString())
                    break
                }
            }
            if (found != null) return found
            parent = parent.parent ?: return null
        }
        return null
    }

    private fun onOffToBoolean(text: String?): Boolean? = when (text?.lowercase()) {
        "on" -> true
        "off" -> false
        else -> null
    }

    private fun findCalibrated(root: AccessibilityNodeInfo, sig: CalibrationSignature): ToggleMatch? =
        when (sig.type) {
            CALIB_TYPE_RID -> root.findAccessibilityNodeInfosByViewId(sig.value)
                .firstOrNull()
                ?.let { wrap(it, "calibrated:$CALIB_TYPE_RID") }

            CALIB_TYPE_CLASS_SIG -> findByClassSignature(root, sig.value)
                ?.let { wrap(it, "calibrated:$CALIB_TYPE_CLASS_SIG") }

            else -> null
        }

    private fun findByClassSignature(root: AccessibilityNodeInfo, value: String): AccessibilityNodeInfo? {
        val parts = value.split("|")
        if (parts.size < 3) return null
        val className = parts[0]
        val label = parts[1]
        val index = parts[2].toIntOrNull() ?: 0
        var sameClassSeen = 0
        var firstOfClass: AccessibilityNodeInfo? = null
        var labeledMatch: AccessibilityNodeInfo? = null
        var indexedMatch: AccessibilityNodeInfo? = null
        forEachNode(root) { node ->
            if (node.className?.toString() != className) return@forEachNode
            if (firstOfClass == null) firstOfClass = node
            sameClassSeen++
            if (indexedMatch == null && sameClassSeen - 1 == index) indexedMatch = node
            if (labeledMatch == null && label.isNotBlank()) {
                if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                    labeledMatch = node
                }
            }
        }
        return labeledMatch ?: indexedMatch ?: firstOfClass
    }

    private fun findByResourceId(root: AccessibilityNodeInfo): ToggleMatch? =
        root.findAccessibilityNodeInfosByViewId(SWITCH_WIDGET_ID)
            .firstOrNull()
            ?.let { wrap(it, "switch_widget") }

    private fun findByClass(root: AccessibilityNodeInfo): ToggleMatch? {
        val switches = mutableListOf<AccessibilityNodeInfo>()
        forEachNode(root) { node ->
            val cls = node.className?.toString() ?: return@forEachNode
            if (cls == SWITCH_CLASS || cls == COMPOUND_BUTTON_CLASS) switches.add(node)
        }
        val preferred = switches.firstOrNull { it.isCheckable } ?: switches.firstOrNull() ?: return null
        return wrap(preferred, "class:${preferred.className}")
    }

    private fun findByTextProximity(root: AccessibilityNodeInfo): ToggleMatch? {
        val anchors = mutableListOf<AccessibilityNodeInfo>()
        forEachNode(root) { node ->
            val combined = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            if (combined.contains("hotspot", ignoreCase = true)) anchors.add(node)
        }
        for (anchor in anchors) {
            var subtreeMatch: ToggleMatch? = null
            forEachNode(anchor) { node ->
                if (subtreeMatch == null && isSwitchLike(node)) subtreeMatch = wrap(node, "text-proximity:descendant")
            }
            if (subtreeMatch != null) return subtreeMatch

            var current = anchor.parent
            repeat(MAX_ANCESTOR_HOPS) {
                val parent = current ?: return@repeat
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i) ?: continue
                    if (isSwitchLike(sibling)) {
                        wrap(sibling, "text-proximity:sibling")?.let { return it }
                    }
                    var nested: ToggleMatch? = null
                    forEachNode(sibling) { node ->
                        if (nested == null && isSwitchLike(node)) nested = wrap(node, "text-proximity:sibling-subtree")
                    }
                    if (nested != null) return nested
                }
                current = parent.parent
            }
        }
        return null
    }

    private fun isSwitchLike(node: AccessibilityNodeInfo): Boolean {
        val cls = node.className?.toString() ?: return false
        return (cls == SWITCH_CLASS || cls == COMPOUND_BUTTON_CLASS) &&
            (node.isCheckable || node.isClickable)
    }

    private fun wrap(stateNode: AccessibilityNodeInfo, source: String): ToggleMatch? {
        val target = if (stateNode.isClickable) {
            stateNode
        } else {
            var current: AccessibilityNodeInfo? = stateNode.parent
            var hops = 0
            var clickable: AccessibilityNodeInfo? = null
            while (current != null && hops < MAX_ANCESTOR_HOPS) {
                if (current.isClickable) {
                    clickable = current
                    break
                }
                current = current.parent
                hops++
            }
            clickable
        } ?: return null
        return ToggleMatch(stateNode, target, source)
    }

    private inline fun forEachNode(
        root: AccessibilityNodeInfo,
        maxNodes: Int = MAX_NODES,
        action: (AccessibilityNodeInfo) -> Unit
    ) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < maxNodes) {
            val node = queue.removeFirst()
            count++
            action(node)
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { queue.add(it) }
                } catch (t: Throwable) {
                }
            }
        }
    }
}
