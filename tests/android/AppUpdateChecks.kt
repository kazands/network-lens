package io.github.kazands.networklens.test

import android.app.*
import android.content.*
import android.content.pm.*
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import io.github.kazands.networklens.*
import java.io.File
import java.io.IOException

/** Isolated emulator only; fixtures never contact GitHub or install a release. */
internal object AppUpdateChecks {
    private fun get(o: Any,n: String): Any? = o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun set(o: Any,n: String,v: Any?) {o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.set(o,v)}}
    private fun texts(v: View): List<TextView> = buildList {if(v is TextView)add(v);if(v is ViewGroup)repeat(v.childCount){addAll(texts(v.getChildAt(it)))}}
    private fun views(v: View): List<View> = buildList { add(v);if(v is ViewGroup)repeat(v.childCount){addAll(views(v.getChildAt(it)))}}
    fun run(test: Instrumentation,check:(Boolean,String)->Unit) {
        val a=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo",true)) as MainActivity
        val original=Configuration(a.resources.configuration)
        fun main(action:()->Unit){var fail:Throwable?=null;test.runOnMainSync{try{action()}catch(e:Throwable){fail=e}};fail?.let{throw it}}
        val flow=get(a,"updater") as UpdateFlow
        val release=AppUpdates.Release(AppUpdates.Version(0,99,0),true,"https://github.com/kazands/network-lens/releases/download/v0.99.0/Network-Lens-0.99.0.apk",1024,"a".repeat(64))
        fun show(){MainActivity::class.java.getDeclaredMethod("showUpdates",Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,false)}
        fun refresh(){MainActivity::class.java.getDeclaredMethod("refreshUpdateDialog").apply{isAccessible=true}.invoke(a)}
        fun dialog()=get(a,"updateDialog") as Dialog
        fun state(step:UpdateFlow.Step){set(flow,"release",release);set(flow,"view",UpdateFlow.View(step,"Synthetic update status",47))}
        try {
            main {
                check(flow.view.step==UpdateFlow.Step.IDLE && get(flow,"transfer")==null,"Opening app never requests an update")
                val receiver=a.packageManager.getReceiverInfo(ComponentName(a,UpdateResultReceiver::class.java),0)
                check(!receiver.exported && receiver.enabled,"Installer callback is private")
                check(a.packageManager.getPackageInfo(a.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions?.contains("android.permission.REQUEST_INSTALL_PACKAGES")==true,"APK installation permission declared")
            }
            val labels=mapOf(UpdateFlow.Step.IDLE to "Ready to check",UpdateFlow.Step.CHECKING to "Checking for updates…",UpdateFlow.Step.CURRENT to "You’re up to date",UpdateFlow.Step.AVAILABLE to "Update available · 0.99.0",UpdateFlow.Step.DOWNLOADING to "Downloading · 47%",UpdateFlow.Step.READY to "Update verified · 0.99.0",UpdateFlow.Step.STAGING to "Preparing the installer…",UpdateFlow.Step.INSTALLING to "Confirm the update",UpdateFlow.Step.ERROR to "Update couldn’t finish")
            for(dark in listOf(false,true)) for(scale in listOf(1f,2f)) {
                main {
                    a.resources.updateConfiguration(Configuration(original).apply{fontScale=scale;screenWidthDp=320},a.resources.displayMetrics)
                    Store(a).dark(dark)
                    MainActivity::class.java.getDeclaredMethod("render").apply{isAccessible=true}.invoke(a)
                }
                for((step,label) in labels) {
                    main{state(step);show()};test.waitForIdleSync()
                    main{
                        val rows=texts(dialog().window!!.decorView)
                        check(rows.any{it.text.toString()==label},"Update state $step readable at scale $scale, dark=$dark")
                        check(rows.any{it.text.toString().startsWith("Installed · ")},"Installed version visible")
                        check(rows.none{it.text.toString().contains("null")},"No missing release text exposed")
                        val window = dialog().window!!
                        check(window.attributes.dimAmount >= .6f && window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0,
                            "Update $step separates its surface from the underlying page")
                        for (row in rows.filter { it.visibility == View.VISIBLE && it.text.isNotEmpty() }) {
                            val layout = row.layout!!
                            check(layout.getLineEnd(layout.lineCount - 1) == row.text.length &&
                                row.height - row.compoundPaddingTop - row.compoundPaddingBottom >= layout.height &&
                                (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 && layout.getLineMax(it) <= row.width - row.compoundPaddingLeft - row.compoundPaddingRight + 1 },
                                "Update $step text fits without clipping at scale $scale, dark=$dark")
                            if(row.isClickable) {
                                val position=IntArray(2);row.getLocationInWindow(position)
                                check(row.height>=48*a.resources.displayMetrics.density && position[1]>=0 &&
                                    position[1]+row.height<=window.decorView.height,"Update $step action remains visible and touchable")
                            }
                        }
                    }
                    // Layout assertions above run on the current view tree. Let its frame
                    // reach the compositor before capturing; otherwise a capture can show
                    // the preceding synthetic state even though the assertions passed.
                    android.os.SystemClock.sleep(250)
                    test.waitForIdleSync()
                    val folder=File(a.getExternalFilesDir(null),"update-surfaces-api${Build.VERSION.SDK_INT}").apply{mkdirs()}
                    val image=test.uiAutomation.takeScreenshot()
                    File(folder,"${step.name.lowercase()}-${scale}x-${if(dark) "dark" else "light"}.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                    image.recycle()
                }
                lateinit var before: List<TextView>
                lateinit var scroll: ScrollView
                lateinit var cancel: TextView
                var y=0
                main { state(UpdateFlow.Step.DOWNLOADING);show() }
                test.waitForIdleSync()
                main {
                    before=texts(dialog().window!!.decorView)
                    scroll=views(dialog().window!!.decorView).filterIsInstance<ScrollView>().single()
                    cancel=before.single{it.text.toString()=="Cancel"}
                    cancel.isFocusableInTouchMode=true;cancel.requestFocus()
                    scroll.scrollTo(0,scroll.getChildAt(0).height)
                    y=scroll.scrollY
                    for(percent in listOf(48,48,49,50)) {
                        set(flow,"view",UpdateFlow.View(UpdateFlow.Step.DOWNLOADING,"Synthetic update status",percent));refresh()
                    }
                }
                test.waitForIdleSync()
                main {
                    check(texts(dialog().window!!.decorView)==before,"Repeated progress retains every text and action view, scale=$scale dark=$dark")
                    check(scroll.scrollY==y && cancel.hasFocus(),"Download progress preserves scroll and action focus, scale=$scale dark=$dark")
                    check(before.any{it.text.toString()=="Downloading · 50%"},"Stable progress still displays the latest percentage")
                    val shown=dialog()
                    MainActivity::class.java.getDeclaredMethod("render").apply{isAccessible=true}.invoke(a)
                    check(dialog()===shown,"Unrelated page refresh retains the update window")
                    state(UpdateFlow.Step.INSTALLING);refresh()
                    set(flow,"pendingConfirmation",Intent("synthetic.confirmation"));refresh()
                    check(texts(dialog().window!!.decorView).any{it.text.toString()=="Continue installation"},
                        "New installer confirmation replaces actions even when the step is unchanged")
                    set(flow,"pendingConfirmation",null);refresh()
                    check(texts(dialog().window!!.decorView).none{it.text.toString()=="Continue installation"},
                        "Withdrawn confirmation removes the obsolete installer action")
                }
            }
            main {
                state(UpdateFlow.Step.DOWNLOADING);refresh()
                texts(dialog().window!!.decorView).single{it.text.toString()=="Cancel"}.performClick()
            }
            test.waitForIdleSync()
            main {
                check(flow.view.step==UpdateFlow.Step.AVAILABLE && !(get(a,"updatesOpen") as Boolean),"Cancel returns to retryable state and dismisses dialog")
                state(UpdateFlow.Step.DOWNLOADING);show()
                Store(a).dark(!Store(a).dark())
                MainActivity::class.java.getDeclaredMethod("render").apply{isAccessible=true}.invoke(a)
            }
            test.waitForIdleSync()
            main {
                check(flow.view.step==UpdateFlow.Step.DOWNLOADING && get(a,"updatesOpen")==true && dialog().isShowing,"Queued dismissal of replaced dialog cannot cancel active download")
                for(step in listOf(UpdateFlow.Step.CHECKING,UpdateFlow.Step.DOWNLOADING,UpdateFlow.Step.STAGING)) {
                    val temporary=File(a.cacheDir,"synthetic-update.apk").apply{writeText("synthetic")}
                    set(flow,"apk",temporary);state(step);flow.pause()
                    check(flow.view.step==UpdateFlow.Step.AVAILABLE && !temporary.exists(),"Leaving foreground cancels $step and removes temporary APK")
                    flow.resume()
                }
                val installer=a.packageManager.packageInstaller
                val id=installer.createSession(UpdateInstaller.parameters(a,1024))
                set(flow,"sessionId",id);set(flow,"callbackToken","synthetic-token");state(UpdateFlow.Step.INSTALLING)
                val result=UpdateFlow::class.java.getDeclaredMethod("result",Intent::class.java).apply{isAccessible=true}
                result.invoke(flow,Intent("wrong-token").putExtra(PackageInstaller.EXTRA_SESSION_ID,id).putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_SUCCESS))
                check(flow.view.step==UpdateFlow.Step.INSTALLING,"Unrelated callback token ignored")
                result.invoke(flow,Intent("synthetic-token").putExtra(PackageInstaller.EXTRA_SESSION_ID,id+1).putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_SUCCESS))
                check(flow.view.step==UpdateFlow.Step.INSTALLING,"Unrelated installer session ignored")
                result.invoke(flow,Intent("synthetic-token").putExtra(PackageInstaller.EXTRA_SESSION_ID,id).putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE_ABORTED))
                check(flow.view.step==UpdateFlow.Step.ERROR && get(flow,"sessionId")==-1,"Rejected installation abandons session without claiming success")
                val file=File(a.cacheDir,"synthetic-invalid.apk").apply{writeText("not an apk")}
                var rejected=false
                try{UpdateInstaller.verify(a,release,file)}catch(_:AppUpdates.Failure){rejected=true}
                check(rejected,"Invalid Android archive rejected before staging");file.delete()
                rejected=false
                try{UpdateInstaller.verify(a,release,File(a.applicationInfo.sourceDir))}catch(_:AppUpdates.Failure){rejected=true}
                check(rejected,"Installed development APK cannot masquerade as a newer public update")
                state(UpdateFlow.Step.IDLE);show();flow.pause();flow.check()
                check(flow.view.step==UpdateFlow.Step.IDLE && get(flow,"transfer")==null,"Background app cannot check GitHub")
                flow.resume()
            }
            // Reopen the staged session on the real main-thread handoff, with only the platform
            // opener injected. Session/callback cleanup still uses the real Android installer.
            for(fault in listOf(IOException("Synthetic unavailable storage"),
                SecurityException("Synthetic invalid session"),IllegalStateException("Synthetic installer failure"))) {
                main {
                    val installer=a.packageManager.packageInstaller
                    val id=installer.createSession(UpdateInstaller.parameters(a,1024))
                    var attempts=0
                    var issuedCallback:PendingIntent?=null
                    lateinit var failing:UpdateFlow
                    failing=UpdateFlow(a,{}) { _,requested ->
                        attempts++
                        check(android.os.Looper.myLooper()==android.os.Looper.getMainLooper(),"Installer reopen failure is exercised on the main thread")
                        check(requested==id,"The staged session is the one being reopened")
                        issuedCallback=get(failing,"callback") as PendingIntent
                        throw fault
                    }
                    try {
                        set(failing,"sessionId",id);set(failing,"callbackToken","synthetic-failure-$id")
                        set(failing,"view",UpdateFlow.View(UpdateFlow.Step.STAGING))
                        UpdateFlow::class.java.getDeclaredMethod("commitInstall",Int::class.javaPrimitiveType)
                            .apply{isAccessible=true}.invoke(failing,id)
                        check(attempts==1 && failing.view.step==UpdateFlow.Step.ERROR,"Session reopen ${fault.javaClass.simpleName} becomes a recoverable error without escaping")
                        check(failing.view.message=="Android couldn’t prepare the update. Please try again.","Installer failure has a fixed user-facing message without platform exception details")
                        check(get(failing,"sessionId")==-1 && get(failing,"callback")==null && !failing.canContinue(),"Failed handoff clears session ownership, callback and confirmation")
                        var cancelled=false
                        try {issuedCallback!!.send()} catch(_:PendingIntent.CanceledException) {cancelled=true}
                        check(cancelled,"Failed handoff cancels the already-issued installer callback")
                    } finally {
                        failing.close()
                        try {installer.abandonSession(id)} catch(_:RuntimeException) {}
                    }
                }
            }
            // Exercise the real framework commit/callback with the installed synthetic test build.
            // Do not approve or perform the installation: only Android's pending-user-action handoff.
            val installer=a.packageManager.packageInstaller
            val source=File(a.applicationInfo.sourceDir)
            val sid=installer.createSession(UpdateInstaller.parameters(a,source.length()))
            installer.openSession(sid).use {session->session.openWrite("base.apk",0,source.length()).use {out->source.inputStream().use{it.copyTo(out)};session.fsync(out)}}
            main {
                set(flow,"sessionId",sid);set(flow,"callbackToken","synthetic-real-session");state(UpdateFlow.Step.INSTALLING)
                set(flow,"foreground",false)
                UpdateFlow::class.java.getDeclaredField("owner").apply{isAccessible=true}.set(null,java.lang.ref.WeakReference(flow))
                val callback=UpdateInstaller.callback(a,sid,"synthetic-real-session");set(flow,"callback",callback)
                installer.openSession(sid).use{it.commit(callback.intentSender)}
            }
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15)
            var ready=false
            while(System.nanoTime()<deadline && !ready){test.waitForIdleSync();main{ready=flow.canContinue() || flow.view.step!=UpdateFlow.Step.INSTALLING};if(!ready)Thread.sleep(100)}
            main {
                check(flow.canContinue(),"Real PackageInstaller callback requires user confirmation before installation")
                check(flow.view.step==UpdateFlow.Step.INSTALLING,"Installer handoff is not reported as installation success")
                flow.dismiss();flow.resume()
                check(get(flow,"sessionId")==-1 && !flow.canContinue(),"Cancelling approval clears owned session and confirmation")
                val owned=get(flow,"owned") as File;owned.mkdirs();File(owned,"synthetic.apk").writeText("temporary")
                flow.close();check(!owned.exists(),"Closing activity clears owned update cache")
            }
            val removalDeadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while(installer.mySessions.isNotEmpty() && System.nanoTime()<removalDeadline)Thread.sleep(50)
            check(installer.mySessions.isEmpty(),"Android eventually removes all abandoned test installer sessions")
        } finally {main{a.resources.updateConfiguration(original,a.resources.displayMetrics);a.finish()};Store(test.targetContext).clear()}
    }
}
