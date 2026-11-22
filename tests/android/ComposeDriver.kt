@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class,androidx.compose.ui.ExperimentalComposeUiApi::class)
package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.Intent
import android.content.ContextWrapper
import java.io.FileInputStream
import android.os.SystemClock
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import io.github.kazands.networklens.*
import java.lang.ref.WeakReference

/** Drives real Compose semantics, including dialog roots. No fake Android widgets. */
internal class ComposeDriver(val test: Instrumentation,private val check: (Boolean,String)->Unit): AutoCloseable {
    private val roots=mutableListOf<WeakReference<ViewRootForTest>>()
    private val previous=ViewRootForTest.onViewCreatedCallback
    init {
        val process=Runtime.getRuntime().exec(arrayOf("/system/bin/getprop","ro.kernel.qemu"))
        check(process.inputStream.bufferedReader().use {it.readText().trim()}=="1","Compose fixtures reject physical devices before touching app state")
        ViewRootForTest.onViewCreatedCallback={ root -> synchronized(roots) {roots.add(WeakReference(root))};previous?.invoke(root) }
    }
    private var configured=false
    private var oldScale="1.0"
    private var oldSize: String?=null
    private var oldDensity: String?=null
    lateinit var activity: MainActivity
    fun main(block: ()->Unit) {
        var failure: Throwable?=null
        test.runOnMainSync {try {block()} catch(t: Throwable) {failure=t}}
        failure?.let {throw it}
    }
    fun start(demo: Boolean=true) {
        activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo",demo)) as MainActivity
        settle()
    }
    fun attachedRoots(): List<ViewRootForTest> = synchronized(roots) {
        roots.mapNotNull {it.get()}.filter {it.view.isAttachedToWindow}
    }
    fun root(): ViewRootForTest {
        val attached=attachedRoots()
        return attached.lastOrNull {it.view.hasWindowFocus()} ?: attached.last()
    }
    fun awaitTag(tag: String) {
        val deadline=SystemClock.uptimeMillis()+5000
        while(SystemClock.uptimeMillis()<deadline) {
            var found=false;main {found=tagged(tag)!=null}
            if(found) return
            settle()
        }
        throw AssertionError("Compose surface did not become ready: $tag")
    }
    fun nodes(): List<SemanticsNode> {
        val result=mutableListOf<SemanticsNode>()
        fun visit(n: SemanticsNode) {result.add(n);n.children.forEach(::visit)}
        root().updateSemanticsForTest();visit(root().semanticsOwner.unmergedRootSemanticsNode);return result
    }
    fun words(): List<String> = nodes().flatMap { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map {it.text}+
            node.config.getOrNull(SemanticsProperties.EditableText)?.let {listOf(it.text)}.orEmpty()+node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    }
    fun tagged(tag: String)=nodes().singleOrNull {it.config.getOrNull(SemanticsProperties.TestTag)==tag}
    fun text(label: String)=nodes().firstOrNull {n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any {it.text==label} || label in n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()}
    fun click(label: String) {
        main {
            var node=text(label) ?: throw AssertionError("Missing Compose action: $label; visible words: ${words()}")
            while(node.config.getOrNull(SemanticsActions.OnClick)?.action==null && node.parent!=null) node=node.parent!!
            check(node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()==true,"Compose action works: $label")
        };settle()
    }
    fun revealTag(tag: String) {
        // Lazy rows genuinely do not exist until scrolled into the viewport.
        // Exercise their real reachability, including at 200% system text size.
        var exists=false
        main {exists=tagged(tag)!=null}
        if(!exists) {
            var list=false;main {list=tagged("devices-list")!=null}
            if(list) {
                scrollToIndex(0)
                for(attempt in 0 until 30) {
                    main {exists=tagged(tag)!=null}
                    if(exists) break
                    scroll("devices-list",400f)
                }
            }
        }
    }
    fun clickTag(tag: String) {
        revealTag(tag)
        main { val node=tagged(tag) ?: throw AssertionError("Missing Compose tag: $tag")
            check(node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()==true,"Compose action works: $tag")
        };settle()
    }
    fun privateKeyboard() {
        main { val field=nodes().single {it.config.getOrNull(SemanticsActions.SetText)?.action!=null};field.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke() }
        settle()
        main {
            val editor=android.view.inputmethod.EditorInfo()
            val connection=root().view.onCreateInputConnection(editor)
            check(connection!=null && editor.imeOptions and android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0,"Compose text input preserves the no-personalized-learning flag")
            (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(root().view.windowToken,0)
        }
    }
    fun tapTag(tag: String) {
        var x=0f;var y=0f;var ready=false
        main {
            val node=tagged(tag) ?: throw AssertionError("Missing touch target: $tag")
            val location=IntArray(2);root().view.getLocationOnScreen(location)
            val bounds=node.boundsInRoot
            ready=root().view.hasWindowFocus() && bounds.width>0 && bounds.height>0 && !node.config.contains(SemanticsProperties.Disabled)
            x=location[0]+bounds.center.x;y=location[1]+bounds.center.y
        }
        check(ready,"Genuine input targets a visible focused Compose control")
        val down=SystemClock.uptimeMillis()
        for(kind in intArrayOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),kind,x,y,0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try {check(test.uiAutomation.injectInputEvent(event,true),"Genuine Compose touch is delivered")} finally {event.recycle()}
        }
        settle()
    }
    fun search(value: String) {
        var field=false;var list=false
        main {field=nodes().any {it.config.getOrNull(SemanticsActions.SetText)?.action!=null};list=tagged("devices-list")!=null}
        if(!field && list) {
            scrollToIndex(0)
            for(attempt in 0 until 30) {
                main {field=nodes().any {it.config.getOrNull(SemanticsActions.SetText)?.action!=null}}
                if(field) break
                scroll("devices-list",400f)
            }
        }
        main {
            val node=nodes().single {it.config.getOrNull(SemanticsActions.SetText)?.action!=null}
            check(node.config.getOrNull(SemanticsActions.SetText)!!.action!!.invoke(AnnotatedString(value)),"Real Compose text input accepts the search")
        };settle()
    }
    fun scroll(tag: String,dy: Float) {
        main {
            val node=tagged(tag) ?: throw AssertionError("Missing scroll surface: $tag")
            val scroll=node.config.getOrNull(SemanticsActions.ScrollBy)?.action
            check(scroll!=null && scroll.invoke(0f,dy),"Compose scroll surface responds: $tag")
        };settle();awaitScrollIdle(tag)
    }
    private fun awaitScrollIdle(tag: String) {
        val deadline=SystemClock.uptimeMillis()+3000
        var previous=Float.NaN;var stable=0
        while(SystemClock.uptimeMillis()<deadline) {
            var offset=Float.NaN
            main {offset=tagged(tag)?.config?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: throw AssertionError("Missing actual scroll range: $tag")}
            stable=if(offset==previous) stable+1 else 0
            if(stable>=3) return
            previous=offset;SystemClock.sleep(50);test.waitForIdleSync()
        }
        throw AssertionError("Scroll did not settle: $tag")
    }
    fun reveal(label: String,scrollTag: String="device-details") {
        repeat(30) {
            var visible=false;var step=400f
            main {
                visible=text(label)?.boundsInRoot?.let {it.width>0 && it.height>0}==true
                // Follow the actual viewport: a fixed pixel step needs too many
                // swipes for source-labelled evidence at enlarged text sizes.
                step=maxOf(step,(tagged(scrollTag)?.boundsInRoot?.height ?: 0f)*.75f)
            }
            if(visible) return
            scroll(scrollTag,step)
        }
        throw AssertionError("Compose action could not be brought into view: $label")
    }
    fun scrollToIndex(index: Int) {
        main { check(tagged("devices-list")?.config?.getOrNull(SemanticsActions.ScrollToIndex)?.action?.invoke(index)==true,"Lazy list scrolls to a real item") };settle()
    }
    fun assertTextLayouts(label: String) {
        main {
            var count=0
            for(n in nodes()) {
                val getter=n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: continue
                val results=mutableListOf<TextLayoutResult>();getter.invoke(results)
                for(result in results) {
                    val fits=result.multiParagraph.height<=result.size.height+1f && (0 until result.lineCount).all {result.getLineRight(it)-result.getLineLeft(it)<=result.size.width+1f && !result.isLineEllipsized(it)}
                    check(fits,"$label lays out complete text without overflow: text=${result.layoutInput.text.text}, size=${result.size}, width=${result.didOverflowWidth}, height=${result.didOverflowHeight}")
                    count++
                }
            }
            check(count>0,"$label inspects measured Compose text")
        }
    }
    fun field(name: String): Any? = activity.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(activity)
    fun set(name: String,value: Any?) = activity.javaClass.getDeclaredField(name).apply {isAccessible=true}.set(activity,value)
    fun render() = MainActivity::class.java.getDeclaredMethod("render").apply {isAccessible=true}.invoke(activity)
    fun fixture(devices: List<Device>,selected: Device?=null) {
        main {set("devices",devices);set("groupedSource",null);set("selected",selected);set("query","");activity.uiMemory.resetList();activity.uiMemory.resetDetails();render()};settle()
    }
    fun shell(command: String): String = test.uiAutomation.executeShellCommand(command).use { fd -> FileInputStream(fd.fileDescriptor).bufferedReader().use {it.readText().trim()} }
    fun configure(width: Int,scale: Float) {
        if(!configured) {
            oldScale=shell("settings get system font_scale").toFloatOrNull()?.toString() ?: "1.0"
            oldSize=Regex("Override size: ([0-9]+x[0-9]+)").find(shell("wm size"))?.groupValues?.get(1)
            oldDensity=Regex("Override density: ([0-9]+)").find(shell("wm density"))?.groupValues?.get(1)
            configured=true
        }
        shell("wm size "+if(width>=840) "1800x2560" else if(width<=320) "840x1920" else if(width<=360) "945x2100" else "1080x2400")
        // Android 8's legacy display scaling caps this image's effective width;
        // a lower density is needed to exercise the same wide layout class.
        shell("wm density "+if(width>=840) if(android.os.Build.VERSION.SDK_INT<=26) "240" else "320" else if(width==375) "460" else "420")
        shell("settings put system font_scale $scale")
        SystemClock.sleep(1200)
        for(attempt in 0 until 20) {
            settle()
            try {rebind()} catch(_:NoSuchElementException) {continue}
            var applied=false
            main {
                val cfg=activity.resources.configuration
                applied=(if(width>=840) cfg.screenWidthDp>=840 else cfg.screenWidthDp in (width-48)..(width+2)) && kotlin.math.abs(cfg.fontScale-scale)<.01f
            }
            if(applied) break
        }
        main {
            val actual=activity.resources.configuration.screenWidthDp
            check(if(width>=840) actual>=840 else actual in (width-48)..(width+2),"Actual emulator viewport applies ${width}dp layout class (available ${actual}dp)")
            check(kotlin.math.abs(activity.resources.configuration.fontScale-scale)<.01f,"Actual emulator font scale applies $scale")
        }
    }
    private fun rebind() {
        main {
            var context: android.content.Context?=root().view.context
            while(context!=null && context !is MainActivity) context=(context as? ContextWrapper)?.baseContext
            if(context is MainActivity) activity=context
        }
    }
    fun settle() {test.waitForIdleSync();SystemClock.sleep(250);test.waitForIdleSync()}
    fun recreate() { main {activity.recreate()};SystemClock.sleep(800);settle();rebind() }
    override fun close() {
        try {
            if(configured) {
                shell("wm size "+(oldSize ?: "reset"));shell("wm density "+(oldDensity ?: "reset"));shell("settings put system font_scale $oldScale")
                SystemClock.sleep(1000);settle();rebind()
            }
        } finally {
            if(::activity.isInitialized) main {activity.finish()}
            ViewRootForTest.onViewCreatedCallback=previous
        }
    }
}
