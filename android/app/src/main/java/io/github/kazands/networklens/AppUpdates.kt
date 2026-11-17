package io.github.kazands.networklens

import java.net.URI

/** Public release data only. No network/device inventory enters this API. */
internal object AppUpdates {
    const val PACKAGE = "io.github.kazands.networklens"
    const val CERTIFICATE = "5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3"
    const val FEED = "https://api.github.com/repos/kazands/network-lens/releases?per_page=100"
    const val MAX_APK = 64L * 1024 * 1024
    data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
        override fun compareTo(other: Version): Int = compareValuesBy(this, other, {it.major}, {it.minor}, {it.patch})
        override fun toString() = "$major.$minor.$patch"
        companion object {
            fun parse(text: String): Version? {
                if (!text.matches(Regex("(?:0|[1-9][0-9]{0,4})\\.(?:0|[1-9][0-9]{0,4})\\.(?:0|[1-9][0-9]{0,4})"))) return null
                val n=text.split('.').map {it.toInt()};return Version(n[0],n[1],n[2])
            }
        }
    }
    data class Release(val version: Version, val preview: Boolean, val url: String, val bytes: Long, val sha256: String)
    class Failure(message: String) : java.io.IOException(message)
    private fun invalid(): Nothing = throw Failure("GitHub returned incomplete update information. Please try again later.")
    private fun obj(v: BoundedJson.Value?) = (v as? BoundedJson.ObjectValue)?.values ?: invalid()
    private fun text(v: BoundedJson.Value?) = (v as? BoundedJson.Scalar)?.takeUnless {it.number}?.text ?: invalid()
    private fun bool(v: BoundedJson.Value?) = (v as? BoundedJson.BooleanValue)?.value ?: invalid()
    private fun number(v: BoundedJson.Value?) = (v as? BoundedJson.Scalar)?.takeIf {it.number}?.text?.toLongOrNull() ?: invalid()
    /** Includes numbered preview releases. Never equate an empty/malformed feed with up-to-date. */
    fun latest(bytes: ByteArray): Release {
        val values=BoundedJson.releaseArray(bytes)?.values ?: invalid()
        val releases=values.map(::obj).filter { !bool(it["draft"]) }.mapNotNull { item ->
            val tag=text(item["tag_name"])
            val version=if(tag.startsWith('v')) Version.parse(tag.substring(1)) else null
            version?.let { it to item }
        }
        val best=releases.maxOfOrNull {it.first} ?: invalid()
        val item=releases.filter {it.first==best}.singleOrNull()?.second ?: invalid()
        val preview=bool(item["prerelease"])
        val name="Network-Lens-$best.apk"
        val assets=(item["assets"] as? BoundedJson.ArrayValue)?.values?.map(::obj) ?: invalid()
        val asset=assets.filter { text(it["name"])==name }.singleOrNull() ?: invalid()
        val url=text(asset["browser_download_url"])
        if(url!="https://github.com/kazands/network-lens/releases/download/v$best/$name" || text(asset["state"])!="uploaded") invalid()
        val size=number(asset["size"]);if(size !in 1..MAX_APK) invalid()
        val digest=text(asset["digest"])
        if(!digest.matches(Regex("sha256:[0-9a-f]{64}"))) invalid()
        return Release(best,preview,url,size,digest.substring(7))
    }
    fun allowed(url: String, feed: Boolean): Boolean {
        if(feed) return url==FEED
        if(url.length>16384) return false
        val u=try{URI(url)}catch(_:Exception){return false}
        if(u.scheme!="https" || u.rawUserInfo!=null || u.rawFragment!=null || u.port !in listOf(-1,443)) return false
        return when(u.host) {
            "github.com" -> u.rawQuery==null && Regex("/kazands/network-lens/releases/download/v[0-9.]+/Network-Lens-[0-9.]+\\.apk").matches(u.rawPath.orEmpty())
            "release-assets.githubusercontent.com", "objects.githubusercontent.com" -> u.rawPath?.startsWith('/')==true
            else -> false
        }
    }
    /** Semantic name and Android's monotonic code must both advance; one pinned signer only. */
    fun compatible(release: Release, packageName: String, versionName: String, code: Long,
                   installedName: String, installedCode: Long, signers: Set<String>, installedSigners: Set<String>): Boolean =
        packageName==PACKAGE && Version.parse(versionName)==release.version &&
        Version.parse(installedName)?.let {release.version>it}==true && code>installedCode &&
        signers==setOf(CERTIFICATE) && installedSigners==setOf(CERTIFICATE)
}
