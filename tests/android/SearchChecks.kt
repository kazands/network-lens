@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class,androidx.compose.ui.ExperimentalComposeUiApi::class)
package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.semantics.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.kazands.networklens.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Synthetic observations, genuine field/key events and the current IME action. */
internal object SearchChecks {
    fun run(test: Instrumentation,check: (Boolean,String)->Unit) {
        val snapshots=JSONArray()
        ComposeDriver(test,check).use { d ->
            val prior=d.shell("settings get secure show_ime_with_hard_keyboard")
            try {
                d.shell("settings put secure show_ime_with_hard_keyboard 1")
                Store(test.targetContext).clear();DeviceCatalogLoader.load(test.targetContext)
                d.start()
                val devices=ComposeUiChecks.fixtures()+(60..99).map { n -> Device().apply {
                    ip="192.0.2.$n";seen=true;name="Sample responder $n"
                } }
                fun awaitKeyboard(visible: Boolean) {
                    var actual=!visible
                    val deadline=SystemClock.uptimeMillis()+4000
                    while(actual!=visible && SystemClock.uptimeMillis()<deadline) {
                        d.main {actual=ViewCompat.getRootWindowInsets(d.root().view)?.isVisible(WindowInsetsCompat.Type.ime())==true}
                        if(actual!=visible) SystemClock.sleep(100)
                    }
                    check(actual==visible,if(visible) "Tapping search opens the keyboard" else "Keyboard Search dismisses the keyboard to show results")
                }
                fun snapshot(label: String) {
                    d.main {
                        val field=d.nodes().singleOrNull {it.config.getOrNull(SemanticsActions.SetText)?.action!=null}
                        snapshots.put(JSONObject().put("step",label).put("query",d.activity.uiState().query)
                            .put("focused",field?.config?.getOrNull(SemanticsProperties.Focused)==true)
                            .put("fieldBounds",field?.boundsInRoot?.toString())
                            .put("keyboardVisible",ViewCompat.getRootWindowInsets(d.root().view)?.isVisible(WindowInsetsCompat.Type.ime())==true)
                            .put("listIndex",d.activity.uiMemory.listIndex).put("listOffset",d.activity.uiMemory.listOffset))
                    }
                    ComposeUiChecks.capture(d,"search-$label")
                }
                for((width,scale) in listOf(375 to 1f,320 to 2f)) {
                d.configure(width,scale)
                for(dark in listOf(false,true)) {
                    val label="$width-$scale-$dark"
                    val network=NetworkState.current(d.activity)
                    d.fixture(devices)
                    d.main {
                        val store=Store(d.activity);store.dark(dark)
                        store.retainIfCurrent(store.token(),network.key,devices,ScanReport("192.0.2.0/24",true,"",254,254,System.currentTimeMillis()))
                        d.set("network",network);d.set("demo",false);d.set("range","192.0.2.0/24");d.render()
                    };d.settle()
                    d.scrollToIndex(1);d.tapTag("search");awaitKeyboard(true);d.settle();snapshot("$label-focused")
                    var connection: android.view.inputmethod.InputConnection?=null
                    var top=0f
                    d.main {top=d.tagged("search")!!.boundsInRoot.top}
                    for((index,char) in "ecb601".withIndex()) {
                        // Android may restart input after a layout change. Send
                        // keys to the current editor, not a cached connection.
                        test.sendStringSync(char.toString());d.settle()
                        snapshot("$label-typed-${index+1}")
                        d.main {
                            check(d.activity.uiState().query=="ecb601".take(index+1),"Typing preserves every search character")
                            val field=d.nodes().singleOrNull {it.config.getOrNull(SemanticsActions.SetText)?.action!=null}
                            check(field?.config?.getOrNull(SemanticsProperties.Focused)==true,"Typing keeps the search field focused")
                            check(field?.boundsInRoot?.let {it.width>0 && it.height>0}==true,"Typing keeps the search field onscreen")
                            // A short result list clamps its scroll offset when
                            // it fits the viewport. The broad first query still
                            // has enough rows to preserve the search position.
                            if(index==0) check(kotlin.math.abs(d.tagged("search")!!.boundsInRoot.top-top)<8*d.root().density.density,"Typing a broad query preserves the search position")
                        }
                    }
                    d.main {
                        val editor=EditorInfo()
                        connection=d.root().view.onCreateInputConnection(editor)
                        check(connection!=null,"Focused search exposes a real Android IME connection")
                        check(editor.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING!=0,"Search keeps keyboard learning disabled")
                        check(connection!!.performEditorAction(EditorInfo.IME_ACTION_SEARCH),"Keyboard Search action is accepted")
                    };awaitKeyboard(false);d.settle()
                    snapshot("$label-submitted")
                    d.main {
                        check(d.activity.uiState().query=="ecb601","Search keeps the submitted query")
                        check(d.nodes().none {it.config.getOrNull(SemanticsActions.SetText)!=null && it.config.getOrNull(SemanticsProperties.Focused)==true},"Search releases field focus")
                    }
                    d.revealTag("result-count")
                    d.main {check(d.words().contains("1 matching device"),"Live model search narrows the synthetic list")}
                    if(scale==1f) d.main {
                        check(d.tagged("device:192.0.2.41")?.boundsInRoot?.height?.let {it>0}==true,"Phone-sized search results are visible after submitting")
                    }
                    d.reveal(devices.first {it.ip=="192.0.2.41"}.presentation().title,"devices-list")
                    snapshot("$label-results")
                    d.clickTag("device:192.0.2.41");d.clickTag("back")
                    d.main {check(d.activity.uiState().query=="ecb601","Back from a search result preserves the query")}
                    d.scrollToIndex(1);d.tapTag("search");awaitKeyboard(true);d.settle()
                    test.sendKeyDownUpSync(KeyEvent.KEYCODE_MOVE_END)
                    test.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL);d.settle()
                    d.main {check(d.activity.uiState().query=="ecb60","Editing the query preserves the remaining characters")}
                    d.click("Clear search")
                    d.main {
                        check(d.activity.uiState().query.isEmpty(),"Clear search restores the unfiltered query")
                        check(d.nodes().any {it.config.getOrNull(SemanticsActions.SetText)!=null && it.config.getOrNull(SemanticsProperties.Focused)==true},"Clear search keeps input ready for another query")
                        connection=d.root().view.onCreateInputConnection(EditorInfo())
                        check(connection!!.performEditorAction(EditorInfo.IME_ACTION_SEARCH),"Empty Search action is accepted")
                    };awaitKeyboard(false);d.settle()
                    d.revealTag("result-count")
                    d.main {check(d.words().contains("${d.activity.uiState().groups.size} devices"),"Clearing restores every synthetic device")}
                    snapshot("$label-cleared")
                }
                }
            } catch(t: Throwable) {
                runCatching {ComposeUiChecks.capture(d,"search-failure")};throw t
            } finally {
                File(d.activity.getExternalFilesDir(null),"search-checks.json").writeText(snapshots.toString(2))
                if(prior=="null") d.shell("settings delete secure show_ime_with_hard_keyboard")
                else d.shell("settings put secure show_ime_with_hard_keyboard $prior")
            }
        }
        Store(test.targetContext).clear()
    }
}
