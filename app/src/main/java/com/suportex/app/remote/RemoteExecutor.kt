package com.suportex.app.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object RemoteExecutor {
    private const val TAG = "SXS/CTRL"
    private var svc: RemoteControlService? = null

    fun bind(service: RemoteControlService) {
        svc = service
    }

    fun unbind(service: RemoteControlService) {
        if (svc === service) {
            svc = null
        }
    }

    fun isReady(): Boolean = svc != null

    fun tap(xNorm: Float, yNorm: Float) {
        val (x, y) = normalizeToPx(xNorm, yNorm) ?: return
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1)
        dispatchGesture(stroke)
    }

    fun longPress(xNorm: Float, yNorm: Float, durationMs: Long = 500L) {
        val (x, y) = normalizeToPx(xNorm, yNorm) ?: return
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        dispatchGesture(stroke)
    }

    fun swipe(
        xStartNorm: Float,
        yStartNorm: Float,
        xEndNorm: Float,
        yEndNorm: Float,
        durationMs: Long = 250L
    ) {
        val start = normalizeToPx(xStartNorm, yStartNorm) ?: return
        val end = normalizeToPx(xEndNorm, yEndNorm) ?: return
        val path = Path().apply {
            moveTo(start.first, start.second)
            lineTo(end.first, end.second)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        dispatchGesture(stroke)
    }

    fun back() {
        svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun home() {
        svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun recents() {
        svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    fun inputText(text: String) {
        val service = svc ?: return
        if (text.isBlank()) return
        val root = service.rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) {
            Log.w(TAG, "Falha ao inserir texto")
        }
    }

    private fun normalizeToPx(xNorm: Float, yNorm: Float): Pair<Float, Float>? {
        val service = svc ?: return null
        val dm = service.resources.displayMetrics
        val x = xNorm.coerceIn(0f, 1f) * dm.widthPixels
        val y = yNorm.coerceIn(0f, 1f) * dm.heightPixels
        return x to y
    }

    private fun dispatchGesture(stroke: GestureDescription.StrokeDescription) {
        val service = svc ?: return
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }
}
