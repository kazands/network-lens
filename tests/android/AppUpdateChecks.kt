package io.github.kazands.networklens.test

import android.app.*
import android.content.*
import android.content.pm.*
import io.github.kazands.networklens.*
import java.io.File
import java.io.IOException

/** Framework installer/security regressions; no network requests or installations. */
internal object AppUpdateChecks {
    private fun get(o: Any,n: String): Any? = o.javaClass.getDeclaredField(n).let {it.isAccessible=true;it.get(o)}
    private fun set(o: Any,n: String,v: Any?) {o.javaClass.getDeclaredField(n).let {it.isAccessible=true;it.set(o,v)}}
    fun run(test: Instrumentation,check: (Boolean,String)->Unit) {
        ComposeDriver(test,check).use { driver ->
            driver.start();val a=driver.activity
            fun main(action: ()->Unit) = driver.main(action)
            val flow=get(a,"updater") as UpdateFlow
            val release=AppUpdates.Release(AppUpdates.Version(0,99,0),true,"https://github.com/kazands/network-lens/releases/download/v0.99.0/Network-Lens-0.99.0.apk",1024,"a".repeat(64))
            fun state(step: UpdateFlow.Step) {set(flow,"release",release);set(flow,"view",UpdateFlow.View(step,"Synthetic update status",47))}
            main {
                check(flow.view.step==UpdateFlow.Step.IDLE && get(flow,"transfer")==null,"Opening Compose never requests an update")
                check(!a.packageManager.getReceiverInfo(ComponentName(a,UpdateResultReceiver::class.java),0).exported,"Installer callback remains private")
            }
            main {
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
                val blockedFile=File(a.cacheDir,"synthetic-blocked.apk").apply{writeText("synthetic")}
                val blockedId=installer.createSession(UpdateInstaller.parameters(a,1024))
                set(flow,"apk",blockedFile);set(flow,"sessionId",blockedId);set(flow,"callbackToken","synthetic-blocked-token")
                set(flow,"callback",UpdateInstaller.callback(a,blockedId,"synthetic-blocked-token"))
                set(flow,"pendingConfirmation",Intent("synthetic.confirmation"));state(UpdateFlow.Step.INSTALLING)
                val blockedResult=Intent("synthetic-blocked-token").putExtra(PackageInstaller.EXTRA_SESSION_ID,blockedId)
                    .putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE_BLOCKED)
                    .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE,"Untrusted synthetic platform details")
                result.invoke(flow,Intent(blockedResult).setAction("wrong-token"))
                check(blockedFile.exists() && flow.view.step==UpdateFlow.Step.INSTALLING,"Unauthenticated block cannot delete the download or change state")
                result.invoke(flow,blockedResult)
                check(flow.view.step==UpdateFlow.Step.BLOCKED,"Android security or policy block is distinct from a retryable failure")
                check(!blockedFile.exists() && get(flow,"apk")==null,"Blocked download is removed")
                check(get(flow,"sessionId")==-1 && get(flow,"callback")==null && !flow.canContinue(),"Block clears the installer session, callback and confirmation")
                // PackageInstaller removes abandoned sessions asynchronously. The bounded
                // eventual-removal assertion below covers this session along with the others.
                check(flow.view.message.contains("Keep Play Protect enabled") && !flow.view.message.contains("Untrusted"),"Fixed block guidance does not expose platform details")
                flow.install();flow.download();flow.pause();flow.resume();flow.dismiss()
                check(flow.view.step==UpdateFlow.Step.BLOCKED && get(flow,"transfer")==null,"Blocked state cannot restart installation or download and survives foreground changes")
                val file=File(a.cacheDir,"synthetic-invalid.apk").apply{writeText("not an apk")}
                var rejected=false
                try{UpdateInstaller.verify(a,release,file)}catch(_:AppUpdates.Failure){rejected=true}
                check(rejected,"Invalid Android archive rejected before staging");file.delete()
                rejected=false
                try{UpdateInstaller.verify(a,release,File(a.applicationInfo.sourceDir))}catch(_:AppUpdates.Failure){rejected=true}
                check(rejected,"Installed development APK cannot masquerade as a newer public update")
                state(UpdateFlow.Step.IDLE);flow.pause();flow.check()
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
        }
    }
}
