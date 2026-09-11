package com.iranjan.hotspotscheduler.data.model

const val CALIB_TYPE_RID = "RID"
const val CALIB_TYPE_CLASS_SIG = "CLASS_SIG"

data class CalibrationSignature(
    val type: String,
    val value: String
)

data class NodeDump(
    val depth: Int,
    val className: String,
    val viewId: String,
    val text: String,
    val contentDescription: String,
    val bounds: String,
    val clickable: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val siblingIndexOfClass: Int
) {
    fun signature(): CalibrationSignature = if (viewId.isNotBlank()) {
        CalibrationSignature(CALIB_TYPE_RID, viewId)
    } else {
        val label = if (text.isNotBlank()) text else contentDescription
        CalibrationSignature(CALIB_TYPE_CLASS_SIG, "$className|$label|$siblingIndexOfClass")
    }

    fun display(): String = buildString {
        append("class=").append(className)
        if (viewId.isNotBlank()) append("  id=").append(viewId)
        if (text.isNotBlank()) append("  text=\"").append(text).append("\"")
        if (contentDescription.isNotBlank()) append("  cd=\"").append(contentDescription).append("\"")
        append("\nbounds=").append(bounds)
        append("  clickable=").append(clickable)
        append("  checkable=").append(checkable)
        append("  checked=").append(checked)
        append("  depth=").append(depth)
        append("  index=").append(siblingIndexOfClass)
    }
}
