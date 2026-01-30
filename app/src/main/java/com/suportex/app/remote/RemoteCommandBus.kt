package com.suportex.app.remote

import android.util.Log
import org.json.JSONObject

object RemoteCommandBus {
    private const val TAG = "SXS/CTRL"

    @Volatile private var sessionActive = false
    @Volatile private var remoteEnabled = false

    fun setSessionActive(active: Boolean) {
        sessionActive = active
    }

    fun setRemoteEnabled(enabled: Boolean) {
        remoteEnabled = enabled
    }

    fun onMessage(raw: String) {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val type = obj.optString("t", "")
        if (!canExecute(type)) return

        when (type) {
            "tap" -> {
                val x = obj.optDouble("x", Double.NaN)
                val y = obj.optDouble("y", Double.NaN)
                if (!x.isNaN() && !y.isNaN()) {
                    RemoteExecutor.tap(x.toFloat(), y.toFloat())
                }
            }
            "longpress" -> {
                val x = obj.optDouble("x", Double.NaN)
                val y = obj.optDouble("y", Double.NaN)
                val duration = obj.optLong("durationMs", 500L)
                if (!x.isNaN() && !y.isNaN()) {
                    RemoteExecutor.longPress(x.toFloat(), y.toFloat(), duration)
                }
            }
            "swipe" -> {
                val x1 = obj.optDouble("x1", Double.NaN)
                val y1 = obj.optDouble("y1", Double.NaN)
                val x2 = obj.optDouble("x2", Double.NaN)
                val y2 = obj.optDouble("y2", Double.NaN)
                val duration = obj.optLong("durationMs", 250L)
                if (!x1.isNaN() && !y1.isNaN() && !x2.isNaN() && !y2.isNaN()) {
                    RemoteExecutor.swipe(
                        x1.toFloat(),
                        y1.toFloat(),
                        x2.toFloat(),
                        y2.toFloat(),
                        duration
                    )
                }
            }
            "drag" -> {
                val x1 = obj.optDouble("x1", Double.NaN)
                val y1 = obj.optDouble("y1", Double.NaN)
                val x2 = obj.optDouble("x2", Double.NaN)
                val y2 = obj.optDouble("y2", Double.NaN)
                val duration = obj.optLong("durationMs", 450L)
                if (!x1.isNaN() && !y1.isNaN() && !x2.isNaN() && !y2.isNaN()) {
                    RemoteExecutor.drag(
                        x1.toFloat(),
                        y1.toFloat(),
                        x2.toFloat(),
                        y2.toFloat(),
                        duration
                    )
                }
            }
            "back" -> RemoteExecutor.back()
            "home" -> RemoteExecutor.home()
            "recents" -> RemoteExecutor.recents()
            "text" -> {
                val text = obj.optString("text", obj.optString("value", ""))
                val append = obj.optBoolean("append", true)
                RemoteExecutor.inputText(text, append)
            }
        }
    }

    private fun canExecute(type: String): Boolean {
        if (type.isBlank()) return false
        if (!sessionActive || !remoteEnabled || !RemoteExecutor.isReady()) {
            Log.d(TAG, "Comando bloqueado. session=$sessionActive remote=$remoteEnabled acc=${RemoteExecutor.isReady()}")
            return false
        }
        return true
    }
}
