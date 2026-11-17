package io.github.kazands.networklens.test

import android.app.*
import android.content.*
import android.content.pm.*
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.kazands.networklens.*
import java.io.File

/** Isolated emulator only; fixtures never contact GitHub or install a release. */
internal object AppUpdateChecks {
    private fun get(o: Any,n: String): Any? = o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun set(o: Any,n: String,v: Any?) {o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.set(o,v)}}
    private fun texts(v: View): List<TextView> = buildList {if(v is TextView)add(v);if(v is ViewGroup)repeat(v.childCount){addAll(texts(v.getChildAt(it)))}}
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
                    }
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
