package io.github.kazands.networklens

import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.*
import android.provider.Settings
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** Activity-owned explicit updater. Only public APK bytes touch private temporary storage. */
internal class UpdateFlow(private val activity: Activity, private val changed: ()->Unit) {
    enum class Step { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, STAGING, INSTALLING, ERROR }
    data class View(val step: Step=Step.IDLE, val message: String="", val percent: Int=0)
    var view=View(); private set
    var release: AppUpdates.Release?=null; private set
    private val handler=Handler(Looper.getMainLooper())
    private val worker=ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue(1))
    @Volatile private var generation=0L
    @Volatile private var closed=false
    @Volatile private var transfer: UpdateTransfer?=null
    private var future: Future<*>?=null
    private var foreground=false
    private val directory=File(activity.cacheDir,"app-updates")
    private val owned=File(directory,UUID.randomUUID().toString())
    private var apk: File?=null
    @Volatile private var sessionId=-1
    private var callback: PendingIntent?=null
    private var callbackToken=""
    private var pendingConfirmation: Intent?=null
    init {
        if(cleaned.compareAndSet(false,true)) {
            directory.deleteRecursively()
            // Recover only this app's abandoned installer staging after process death.
            try {activity.packageManager.packageInstaller.mySessions.forEach { activity.packageManager.packageInstaller.abandonSession(it.sessionId) }} catch(_:RuntimeException) {}
        }
    }
    fun resume() {foreground=true}
    fun pause() {
        foreground=false
        if(view.step in setOf(Step.CHECKING,Step.DOWNLOADING,Step.STAGING)) {
            cancelWork();abandon();apk?.delete();apk=null
            set(View(if(release==null) Step.IDLE else Step.AVAILABLE,"Update stopped when you left the app. Try again when you’re ready."))
        }
    }
    fun dismiss() {
        if(view.step in setOf(Step.CHECKING,Step.DOWNLOADING,Step.STAGING,Step.INSTALLING)) {
            cancelWork();abandon()
            set(View(if(apk?.isFile==true) Step.READY else if(release!=null) Step.AVAILABLE else Step.IDLE,"Update cancelled."))
        }
    }
    fun check() {
        if(!foreground || closed || view.step in setOf(Step.CHECKING,Step.DOWNLOADING,Step.STAGING,Step.INSTALLING)) return
        cancelWork();abandon();apk?.delete();apk=null;release=null
        set(View(Step.CHECKING))
        task({
            UpdateTransfer(25).use { t -> transfer=t;ensureActive();AppUpdates.latest(t.feed()) }
        }) { found ->
            release=found
            val current=AppUpdates.Version.parse(version())
            if(current==null) set(View(Step.ERROR,"This installation’s version could not be read."))
            else set(View(if(found.version>current) Step.AVAILABLE else Step.CURRENT))
        }
    }
    fun download() {
        val target=release ?: return
        if(!foreground || view.step !in setOf(Step.AVAILABLE,Step.ERROR)) return
        cancelWork();apk?.delete();apk=null
        set(View(Step.DOWNLOADING))
        val id=generation
        task({
            if(!owned.isDirectory && !owned.mkdirs()) throw AppUpdates.Failure("There isn’t enough space to save the update.")
            val file=File(owned,UUID.randomUUID().toString()+".apk")
            try {
                UpdateTransfer(120).use { t ->
                    transfer=t;ensureActive()
                    t.download(target,file) {n,total-> handler.post {if(!closed && id==generation && view.step==Step.DOWNLOADING) set(View(Step.DOWNLOADING,percent=(n*100/total).toInt()))} }
                }
                ensureActive();UpdateInstaller.verify(activity,target,file);file
            } catch(e:Exception) {file.delete();throw e}
        }) { file ->apk=file;set(View(Step.READY)) }
    }
    fun install() {
        if(!foreground || closed) return
        pendingConfirmation?.let { intent ->
            pendingConfirmation=null
            try {activity.startActivity(intent)} catch(_:RuntimeException) {abandon();set(View(Step.ERROR,"Android couldn’t open the update installer. Please try again."))}
            return
        }
        val file=apk ?: return;val target=release ?: return
        if(view.step!=Step.READY) return
        if(!activity.packageManager.canRequestPackageInstalls()) {
            set(View(Step.READY,"Allow Network Lens to install updates in Android settings, then return and tap Install update. Android will ask you to approve the update."))
            try {activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.packageName)))}
            catch(_:RuntimeException) {set(View(Step.READY,"Open Android Settings → Apps → Special app access → Install unknown apps, and allow Network Lens. Then return here."))}
            return
        }
        cancelWork();set(View(Step.STAGING))
        callbackToken=UUID.randomUUID().toString();owner=WeakReference(this)
        task({
            UpdateInstaller.verify(activity,target,file);ensureActive()
            val installer=activity.packageManager.packageInstaller
            val id=installer.createSession(UpdateInstaller.parameters(activity,file.length()))
            sessionId=id
            try {
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk",0,file.length()).use {out->file.inputStream().use {input->
                        val buf=ByteArray(16384)
                        while(true){ensureActive();val n=input.read(buf);if(n<0)break;out.write(buf,0,n)}
                    };session.fsync(out)}
                }
                ensureActive();id
            } catch(e:Exception) {try{installer.abandonSession(id)}catch(_:RuntimeException){};throw e}
        }) { id ->
            try {
                callback=UpdateInstaller.callback(activity,id,callbackToken)
                set(View(Step.INSTALLING,"Opening Android’s update installer…"))
                activity.packageManager.packageInstaller.openSession(id).use {it.commit(callback!!.intentSender)}
            } catch(_:RuntimeException) {abandon();set(View(Step.ERROR,"Android couldn’t prepare the update. Please try again."))}
        }
    }
    private fun result(intent: Intent) {
        if(closed || intent.action!=callbackToken || intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2)!=sessionId) return
        when(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if(confirm==null) {abandon();set(View(Step.ERROR,"Android couldn’t open the update installer."));return}
                pendingConfirmation=confirm
                set(View(Step.INSTALLING,"Approve the update in Android’s installer."))
                if(foreground) install()
            }
            PackageInstaller.STATUS_SUCCESS -> {complete();set(View(Step.CURRENT,"Update installed. Reopen Network Lens to start a fresh scan."))}
            else -> {abandon();set(View(if(apk?.isFile==true) Step.READY else Step.ERROR,"The update wasn’t installed. You can try again."))}
        }
    }
    fun canContinue() = pendingConfirmation!=null
    fun version(): String = try {activity.packageManager.getPackageInfo(activity.packageName,0).versionName.orEmpty()}catch(_:Exception){"Unknown"}
    private fun set(next: View) {if(!closed){view=next;changed()}}
    private fun ensureActive() {if(closed || Thread.currentThread().isInterrupted) throw AppUpdates.Failure("Update cancelled.")}
    private fun <T> task(work:()->T, done:(T)->Unit) {
        val id=generation
        try {future=worker.submit {
            try {
                ensureActive();val result=work()
                handler.post {
                    if(!closed && generation==id && foreground) done(result)
                    else {if(result is File)result.delete();if(result is Int)try{activity.packageManager.packageInstaller.abandonSession(result)}catch(_:RuntimeException){}}
                }
            } catch(e:Exception) {handler.post {if(!closed && generation==id) {abandon();set(View(Step.ERROR,if(e is AppUpdates.Failure)e.message.orEmpty() else "The update couldn’t finish. Check available storage and try again."))}}}
            finally {transfer=null}
        }} catch(_:RejectedExecutionException) {set(View(Step.ERROR,"The previous update request is still stopping. Please try again shortly."))}
    }
    private fun cancelWork() {generation++;transfer?.close();future?.cancel(true);worker.purge()}
    private fun abandon() {
        val id=sessionId;sessionId=-1
        if(id>=0) try{activity.packageManager.packageInstaller.abandonSession(id)}catch(_:RuntimeException){}
        callback?.cancel();callback=null;pendingConfirmation=null
    }
    private fun complete() {sessionId=-1;callback?.cancel();callback=null;pendingConfirmation=null;apk?.delete();apk=null;owned.deleteRecursively()}
    fun close() {if(closed)return;cancelWork();abandon();closed=true;worker.shutdownNow();apk?.delete();owned.deleteRecursively();if(owner?.get()===this)owner=null}
    companion object {
        private val cleaned=AtomicBoolean()
        private var owner: WeakReference<UpdateFlow>?=null
        fun receive(context: Context,intent: Intent) {
            val active=owner?.get()
            if(active!=null) active.result(intent)
            else {
                val id=intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1)
                if(id>=0) try{context.packageManager.packageInstaller.abandonSession(id)}catch(_:RuntimeException){}
            }
        }
    }
}
