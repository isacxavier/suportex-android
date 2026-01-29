package com.suportex.app.remote

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class RemoteControlService : AccessibilityService() {

    override fun onServiceConnected() {
        RemoteExecutor.bind(this)
        Log.d("SXS/ACC", "Accessibility connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        RemoteExecutor.unbind(this)
        super.onDestroy()
    }
}
