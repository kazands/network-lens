package io.github.kazands.networklens

import android.app.PendingIntent
import android.content.*
import android.content.pm.*
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** The system installer performs the final APK signature verification and asks the user to approve. */
internal object UpdateInstaller {
    private fun signers(info: PackageInfo): Set<String> {
        val values=if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners else info.signatures
        return values?.map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .joinToString(""){"%02x".format(it.toInt() and 255)} }?.toSet() ?: emptySet()
    }
    private fun code(info: PackageInfo): Long = if(Build.VERSION.SDK_INT>=28) info.longVersionCode else info.versionCode.toLong()
    fun verify(context: Context, release: AppUpdates.Release, file: File) {
        val flags=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed=context.packageManager.getPackageInfo(context.packageName,flags)
        val candidate=context.packageManager.getPackageArchiveInfo(file.absolutePath,flags)
            ?: throw AppUpdates.Failure("This download isn’t a valid Android update.")
        if(!AppUpdates.compatible(release,candidate.packageName,candidate.versionName.orEmpty(),code(candidate),
            installed.versionName.orEmpty(),code(installed),signers(candidate),signers(installed)))
            throw AppUpdates.Failure("This update doesn’t match this installation’s signing key or version. No changes were made.")
        if((candidate.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE)>Build.VERSION.SDK_INT)
            throw AppUpdates.Failure("This update needs a newer Android version.")
    }
    fun parameters(context: Context, size: Long) = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(context.packageName);setSize(size);setInstallReason(PackageManager.INSTALL_REASON_USER)
        if(Build.VERSION.SDK_INT>=31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
    }
    fun callback(context: Context, id: Int, token: String): PendingIntent = PendingIntent.getBroadcast(context,id,
        Intent(context,UpdateResultReceiver::class.java).setAction(token),
        PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=31) PendingIntent.FLAG_MUTABLE else 0)
}

/** Explicit, non-exported callback; no URL, file or command is accepted from an external app. */
class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { UpdateFlow.receive(context,intent) }
}
