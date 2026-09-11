package com.iranjan.hotspotscheduler.accessibility

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.iranjan.hotspotscheduler.data.model.CALIB_TYPE_CLASS_SIG
import com.iranjan.hotspotscheduler.data.model.CALIB_TYPE_RID
import com.iranjan.hotspotscheduler.data.model.CalibrationSignature

const val KEYWORD_HOTSPOT = "mobile hotspot"
const val KEYWORD_MOBILE_DATA = "mobile data"

object NodeMatcher {

    const val SWITCH_WIDGET_ID = "com.android.settings:id/switch_widget"
    const val SWITCH_TEXT_ID = "com.android.settings:id/switch_text"
    private const val SWITCH_CLASS = "android.widget.Switch"
    private const val COMPOUND_BUTTON_CLASS = "android.widget.CompoundButton"
    private const val EDIT_TEXT_CLASS = "android.widget.EditText"
    private const val MAX_NODES = 1500
    private const val MAX_ANCESTOR_HOPS = 6
    private const val MAX_ROW_TEXT = 400

    fun findToggle(
        root: AccessibilityNodeInfo?,
        calibration: CalibrationSignature?,
        rowKeyword: String
    ): ToggleMatch? {
        if (root == null) return null
        if (rowKeyword == KEYWORD_HOTSPOT) {
            calibration?.let { sig ->
                findCalibrated(root, sig)?.let { return it }
            }
        }
        findByResourceId(root, rowKeyword)?.let { return it }
        findByClass(root, rowKeyword)?.let { return it }
        findByTextProximity(root, rowKeyword)?.let { return it }
        return null
    }

    fun findPasswordEditor(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val editors = mutableListOf<AccessibilityNodeInfo>()
        forEachNode(root) { node ->
            if (node.className?.toString() == EDIT_TEXT_CLASS) editors.add(node)
        }
        if (editors.isEmpty()) return null
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        for (editor in editors) {
            var score = 0
            val own = listOfNotNull(
                editor.hint?.toString(),
                editor.text?.toString(),
                editor.contentDescription?.toString(),
                editor.viewIdResourceName
            ).joinToString(" ").lowercase()
            if (own.contains("password") || own.contains("passwort") || own.contains("mot de passe")) score += 5
            val sibling = labelBefore(editor)
            if (sibling.contains("password") || sibling.contains("passwort")) score += 4
            if (score > bestScore) {
                bestScore = score
                best = editor
            }
        }
        if (best != null && bestScore > 0 && best.isEditable) return best
        return null
    }

    fun findClickableRow(root: AccessibilityNodeInfo?, keyword: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val anchors = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()
        forEachNode(root) { node ->
            val text = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .firstOrNull { it.isNotBlank() } ?: return@forEachNode
            val lower = text.lowercase().trim()
            if (lower == keyword) {
                anchors.add(node to 10)
            } else if (lower.startsWith(keyword)) {
                anchors.add(node to 6)
            } else if (lower.contains(keyword) && !lower.contains("and tethering")) {
                anchors.add(node to 3)
            }
        }
        val sorted = anchors.sortedByDescending { it.second }
        for ((node, _) in sorted) {
            var current: AccessibilityNodeInfo? = node
            var hops = 0
            while (current != null && hops < 5) {
                if (current.isClickable) {
                    if (rowScore(rowTextOf(current), keyword) >= 0) return current
                    break
                }
                current = current.parent
                hops++
            }
        }
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

    fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun onOffToBoolean(text: String?): Boolean? = when (text?.lowercase()) {
        "on" -> true
        "off" -> false
        else -> null
    }

    private fun findCalibrated(root: AccessibilityNodeInfo, sig: CalibrationSignature): ToggleMatch? =
        when (sig.type) {
            CALIB_TYPE_RID -> {
                val candidates = root.findAccessibilityNodeInfosByViewId(sig.value)
                bestRanked(candidates.mapNotNull { wrap(it, "calibrated:$CALIB_TYPE_RID") }, KEYWORD_HOTSPOT)
            }

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

    private fun findByResourceId(root: AccessibilityNodeInfo, rowKeyword: String): ToggleMatch? {
        val nodes = root.findAccessibilityNodeInfosByViewId(SWITCH_WIDGET_ID)
        if (nodes.isEmpty()) return null
        val matches = nodes.mapNotNull { wrap(it, "switch_widget") }
        return bestRanked(matches, rowKeyword)
    }

    private fun findByClass(root: AccessibilityNodeInfo, rowKeyword: String): ToggleMatch? {
        val switches = mutableListOf<AccessibilityNodeInfo>()
        forEachNode(root) { node ->
            val cls = node.className?.toString() ?: return@forEachNode
            if (cls == SWITCH_CLASS || cls == COMPOUND_BUTTON_CLASS) switches.add(node)
        }
        val matches = switches.mapNotNull { wrap(it, "class-switch") }
        return bestRanked(matches, rowKeyword)
    }

    private fun bestRanked(matches: List<ToggleMatch>, rowKeyword: String): ToggleMatch? {
        if (matches.isEmpty()) return null
        if (matches.size == 1) return matches[0]
        return matches.maxByOrNull { rowScore(rowTextOf(it.stateNode), rowKeyword) }
    }

    private fun findByTextProximity(root: AccessibilityNodeInfo, rowKeyword: String): ToggleMatch? {
        val anchors = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()
        forEachNode(root) { node ->
            val combined = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            val lower = combined.lowercase()
            if (lower.contains(rowKeyword)) {
                val bonus = if (lower.trim().startsWith(rowKeyword)) 4 else 0
                anchors.add(node to rowScore(lower, rowKeyword) + bonus)
            }
        }
        val sorted = anchors.sortedByDescending { it.second }
        for ((anchor, _) in sorted) {
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

    private fun rowScore(rowText: String, keyword: String): Int {
        var score = 0
        if (rowText.contains(keyword)) score += 4
        val negativeWords = listOf("bluetooth", "usb", "ethernet", "saver", "roaming", "vpn", "wi-fi sharing", "wifi sharing")
        for (word in negativeWords) {
            if (rowText.contains(word)) score -= 6
        }
        return score
    }

    private fun rowTextOf(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 4 && sb.length < MAX_ROW_TEXT) {
            collectText(current, sb, 0)
            current = current.parent
            hops++
        }
        return sb.toString().lowercase()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (sb.length >= MAX_ROW_TEXT || depth > 3) return
        node.text?.let { if (it.isNotBlank()) { sb.append(it).append(' ') } }
        node.contentDescription?.let { if (it.isNotBlank()) { sb.append(it).append(' ') } }
        if (sb.length >= MAX_ROW_TEXT) return
        for (i in 0 until node.childCount) {
            try {
                node.getChild(i)?.let { collectText(it, sb, depth + 1) }
            } catch (t: Throwable) {
            }
        }
    }

    private fun labelBefore(editor: AccessibilityNodeInfo): String {
        val parent = editor.parent ?: return ""
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChild(i) ?: continue
            if (sibling == editor) break
            val t = sibling.text?.toString() ?: sibling.contentDescription?.toString() ?: ""
            if (t.isNotBlank()) return t.lowercase()
        }
        return ""
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
