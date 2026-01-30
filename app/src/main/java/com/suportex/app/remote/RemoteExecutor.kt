package com.suportex.app.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo

object RemoteExecutor {
    private const val TAG = "SXS/CTRL"
    private const val TAP_DURATION_MS = 100L
    private var svc: RemoteControlService? = null
    @Volatile private var captureFrameWidth = 0
    @Volatile private var captureFrameHeight = 0

    fun bind(service: RemoteControlService) {
        svc = service
    }

    fun unbind(service: RemoteControlService) {
        if (svc === service) {
            svc = null
            clearCaptureFrameSize()
        }
    }

    fun isReady(): Boolean = svc != null

    fun tap(xNorm: Float, yNorm: Float) {
        val (x, y) = normalizeToPx(xNorm, yNorm) ?: return
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS)
        dispatchGesture(stroke)
    }

    fun longPress(xNorm: Float, yNorm: Float, durationMs: Long = 550L) {
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

    fun setCaptureFrameSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            captureFrameWidth = width
            captureFrameHeight = height
        }
    }

    fun clearCaptureFrameSize() {
        captureFrameWidth = 0
        captureFrameHeight = 0
    }

    private fun normalizeToPx(xNorm: Float, yNorm: Float): Pair<Float, Float>? {
        val service = svc ?: return null
        val area = resolveTappableArea(service)
        val width = (area.right - area.left).toFloat().coerceAtLeast(1f)
        val height = (area.bottom - area.top).toFloat().coerceAtLeast(1f)
        val x = area.left + xNorm.coerceIn(0f, 1f) * width
        val y = area.top + yNorm.coerceIn(0f, 1f) * height
        return x to y
    }

    private fun resolveTappableArea(service: AccessibilityService): Rect {
        if (captureFrameWidth > 0 && captureFrameHeight > 0) {
            return Rect(0, 0, captureFrameWidth, captureFrameHeight)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = service.getSystemService(WindowManager::class.java)
            if (windowManager != null) {
                val metrics = windowManager.currentWindowMetrics
                val bounds = metrics.bounds
                return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
        }
        val dm = service.resources.displayMetrics
        return Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun dispatchGesture(stroke: GestureDescription.StrokeDescription) {
        val service = svc ?: return
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }
}
