@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package io.github.kazands.networklens

import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.contentcapture.ContentCaptureManager
import androidx.compose.ui.platform.LocalView

/** Screen text stays out of automatic capture; explicit copy/share and screenshots still work. */
internal object UiPrivacy {
    fun protect(root: View) {
        // Compose reports a virtual hierarchy directly to its capture session. Disable
        // that path before creating any Compose content, including separate windows.
        ContentCaptureManager.isEnabled = false
        root.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (Build.VERSION.SDK_INT >= 29) {
            root.importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        }
    }
}

/** Dialogs, sheets and popups have their own view roots; the Activity guard is not inherited. */
@Composable internal fun PrivateUiRoot() {
    val root = LocalView.current.rootView
    SideEffect { UiPrivacy.protect(root) }
}
