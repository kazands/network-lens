@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.contentcapture.ContentCaptureManager
import androidx.compose.ui.platform.ViewRootForTest
import androidx.lifecycle.LifecycleOwner
import io.github.kazands.networklens.*
import java.lang.reflect.Proxy

/** Synthetic fixtures only. No OEM capture service, phone access or real scan is needed. */
internal object ComposePrivacyChecks {
    fun run(test: Instrumentation,check: (Boolean,String)->Unit) {
        ComposeDriver(test,check).use { d ->
            Store(test.targetContext).clear()
            d.start()
            d.fixture(ComposeUiChecks.fixtures())
            fun protected(label: String,separateWindow: Boolean=false) {
                d.main {
                    check(!ContentCaptureManager.isEnabled,"$label disables Compose virtual content capture")
                    val roots=d.attachedRoots()
                    check(roots.isNotEmpty(),"$label exercises attached Compose roots")
                    if(separateWindow) check(roots.size>=2,"$label exercises a separate window in addition to the Activity")
                    for(root in roots) {
                        val decor=root.view.rootView
                        check(decor.importantForAutofill==View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,"$label excludes the entire window from autofill")
                        if(Build.VERSION.SDK_INT>=29) {
                            check(decor.importantForContentCapture==View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS,"$label excludes the entire window from platform content capture")
                            assertCaptureBlocked(d,root,label,check)
                        }
                        val params=decor.layoutParams as? WindowManager.LayoutParams
                        check(params!=null && params.flags and WindowManager.LayoutParams.FLAG_SECURE==0,"$label keeps user screenshots available")
                    }
                }
            }
            protected("Device list")
            d.clickTag("device:192.0.2.41")
            d.main {check(d.words().contains("Fixture hallway") && d.words().contains("192.0.2.41"),"Capture regression uses displayed synthetic private names and addresses")}
            d.reveal("Show technical details");d.click("Show technical details")
            protected("Device details and observations")
            d.recreate();protected("Recreated device details")
            d.clickTag("options");protected("Overflow popup",true)
            d.click("Settings");protected("Settings")
            for(modal in UiModal.entries) {
                d.main {
                    if(modal==UiModal.REPORT) d.set("sharedReport",TroubleshootingReport.Document(listOf("Synthetic private report: Fixture hallway · 192.0.2.41")))
                    d.set("modal",modal);d.render()
                }
                d.settle()
                d.main {check(d.activity.uiState().modal==modal,"${modal.name} remains open during its privacy check")}
                protected("${modal.name} window",true)
                d.main {d.set("modal",null);d.render()};d.settle()
            }
            if(Build.VERSION.SDK_INT>=29) {
                val screenshot=test.uiAutomation.takeScreenshot()
                check(screenshot!=null,"Content-capture protection allows an actual emulator screenshot")
                screenshot?.recycle()
            }
        }
        Store(test.targetContext).clear()
    }

    /**
     * Inject a session at the pinned runtime's capture boundary. The positive control
     * enables capture within this single main-thread call, proves that the same real
     * manager would request virtual structures, then restores privacy before yielding.
     * Returning null structures avoids creating or retaining any captured payload.
     */
    private fun assertCaptureBlocked(d: ComposeDriver,root: ViewRootForTest,label: String,check: (Boolean,String)->Unit) {
        val field=root.view.javaClass.getDeclaredField("contentCaptureManager").apply {isAccessible=true}
        val manager=field.get(root.view)
        val factory=manager.javaClass.getDeclaredField("onContentCaptureSession").apply {isAccessible=true}
        val session=manager.javaClass.getDeclaredField("contentCaptureSession").apply {isAccessible=true}
        val buffered=manager.javaClass.getDeclaredField("bufferedEvents").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") val events=buffered.get(manager) as MutableList<Any>
        val oldFactory=factory.get(manager);val oldSession=session.get(manager);val oldEvents=events.toList()
        val calls=mutableListOf<String>()
        val contract=Class.forName("androidx.compose.ui.contentcapture.ContentCaptureSessionWrapper")
        val receiver=Proxy.newProxyInstance(contract.classLoader,arrayOf(contract)) {proxy,method,args ->
            when(method.name) {
                "equals" -> proxy===args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Synthetic capture recipient"
                else -> {calls.add(method.name);if(method.name=="newAutofillId") root.view.autofillId else null}
            }
        }
        val start=manager.javaClass.getMethod("onStart",LifecycleOwner::class.java)
        try {
            events.clear();factory.set(manager,{receiver})
            start.invoke(manager,d.activity)
            check(session.get(manager)===receiver,"$label receives a supplied capture session, rather than passing because capture is unavailable")
            check(calls.isEmpty(),"$label sends no structures or text to the supplied capture recipient")
            ContentCaptureManager.isEnabled=true
            start.invoke(manager,d.activity)
            check(calls.contains("newVirtualViewStructure"),"$label positive control detects capture attempts when the protection is disabled")
        } finally {
            ContentCaptureManager.isEnabled=false
            factory.set(manager,oldFactory);session.set(manager,oldSession)
            events.clear();events.addAll(oldEvents)
        }
    }
}
