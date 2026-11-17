package io.github.kazands.networklens

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/** Preserve the branded native views while exposing the role of their action. */
internal object AccessibleActions {
    fun button(view: View) {
        view.isFocusable = true
        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
            }
        }
    }
}
