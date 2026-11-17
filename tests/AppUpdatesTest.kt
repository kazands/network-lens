import io.github.kazands.networklens.*
import java.io.*
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.concurrent.*
import javax.net.ssl.HttpsURLConnection

object AppUpdatesTest {
    private var checks=0
    private fun check(ok:Boolean,message:String){checks++;if(!ok)error(message)}
    private fun fail(message:String,action:()->Unit){var failed=false;try{action()}catch(_:Exception){failed=true};check(failed,message)}
    private val data="synthetic APK bytes".toByteArray()
    private val hash=MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun release(version:String="0.3.17",preview:Boolean=true,draft:Boolean=false)= """{"tag_name":"v$version","draft":$draft,"prerelease":$preview,"assets":[{"name":"Network-Lens-$version.apk","state":"uploaded","size":${data.size},"digest":"sha256:$hash","browser_download_url":"https://github.com/kazands/network-lens/releases/download/v$version/Network-Lens-$version.apk"}]}"""
    private fun read(json:String)=AppUpdates.latest(json.toByteArray())
    private class Connection(val body:ByteArray=data,val status:Int=200,val location:String?=null,val size:Long=body.size.toLong(),val slow:Boolean=false):HttpsURLConnection(URL("https://github.com")) {
        @Volatile var ended=false
        val started=CountDownLatch(1)
        override fun getResponseCode()=status
        override fun getHeaderField(name:String)=if(name=="Location")location else null
        override fun getContentLengthLong()=size
        override fun getContentEncoding():String?=null
        override fun getInputStream():InputStream=if(!slow)ByteArrayInputStream(body)else object:InputStream(){
            override fun read():Int {started.countDown();while(!ended){Thread.sleep(5)};throw IOException("closed")}
        }
        override fun disconnect(){ended=true}
        override fun usingProxy()=false
        override fun connect(){}
        override fun getCipherSuite()="TEST"
        override fun getLocalCertificates():Array<Certificate>?=null
        override fun getServerCertificates():Array<Certificate> = emptyArray()
    }
    @JvmStatic fun main(args:Array<String>) {
        check(AppUpdates.Version.parse("0.3.17")!!>AppUpdates.Version.parse("0.3.9")!!,"Numeric version ordering")
        check(AppUpdates.Version.parse("1.0.0")!!>AppUpdates.Version.parse("0.99.99")!!,"Major version ordering")
        for(bad in listOf("v0.3.17","01.3.17","1.2","-1.2.3","1.2.3.4","1.0.0-beta","999999.0.0"))check(AppUpdates.Version.parse(bad)==null,"Malformed version rejected")
        val target=read("[${release("0.3.9",false)},${release()},${release("1.0.0",draft=true)}]")
        check(target.version.toString()=="0.3.17" && target.preview,"Highest numbered preview included; drafts excluded")
        check(read("[${release("0.3.20")},${release("0.3.17") }]").version.toString()=="0.3.20","Release list order does not determine version")
        for(bad in listOf("[]","{}","[${release()},${release()}]","[${release().replace("\"draft\":false","\"draft\":false,\"draft\":true")}]","[${release().replace("uploaded","uploading")}]","[${release().replace("sha256:","md5:")}]","[${release().replace("github.com/kazands","github.com/other")}]","[${release().replace("\"size\":${data.size}","\"size\":999999999")}]","[${release().replace("Network-Lens-0.3.17.apk","other.apk")}]"))fail("Incomplete/malformed feed cannot say up-to-date"){read(bad)}
        check(AppUpdates.allowed(AppUpdates.FEED,true),"Fixed public feed allowed")
        for(bad in listOf("http://api.github.com/repos/kazands/network-lens/releases?per_page=100",AppUpdates.FEED+"&token=secret","https://evil.example/feed"))check(!AppUpdates.allowed(bad,true),"Feed cannot redirect or carry credentials")
        check(AppUpdates.allowed(target.url,false),"Expected release asset allowed")
        check(AppUpdates.allowed("https://release-assets.githubusercontent.com/synthetic?token=synthetic",false),"GitHub signed asset redirect allowed")
        for(bad in listOf("http://github.com/kazands/network-lens/releases/download/v0.3.17/Network-Lens-0.3.17.apk","https://github.com.evil.example/a.apk","https://user:password@release-assets.githubusercontent.com/a","https://release-assets.githubusercontent.com:444/a","https://127.0.0.1/a","https://github.com/other/repo/releases/download/v0.3.17/Network-Lens-0.3.17.apk","https://release-assets.githubusercontent.com/a#fragment"))check(!AppUpdates.allowed(bad,false),"Untrusted asset destination rejected")
        val signer=setOf(AppUpdates.CERTIFICATE)
        fun compatible(pkg:String=AppUpdates.PACKAGE,name:String="0.3.17",code:Long=45,own:String="0.3.16",ownCode:Long=44,s:Set<String> =signer,installed:Set<String> =signer)=AppUpdates.compatible(target,pkg,name,code,own,ownCode,s,installed)
        check(compatible(),"Exact package, version and pinned public signer accepted")
        check(!compatible(pkg="other.app") && !compatible(name="0.3.18") && !compatible(code=44),"Wrong app, name or non-advancing code blocked")
        check(!compatible(own="0.3.17") && !compatible(own="1.0.0") && !compatible(own="unknown"),"Same/newer/unknown installed version blocked")
        check(!compatible(s=setOf("other")) && !compatible(installed=setOf("development")) && !compatible(s=signer+"other"),"Mismatched or multiple signing identities blocked")
        val pin=java.io.File("android/release-signing.json").readText()
        check(pin.contains("\"certificateSha256\": \"${AppUpdates.CERTIFICATE}\""),"Updater pin matches maintained public signing identity")
        val large="[{\"note\":\""+"x".repeat(10000)+"\"}]"
        check(BoundedJson.releaseArray(large.toByteArray())!=null && BoundedJson.document(large.drop(1).dropLast(1).toByteArray())==null,"Larger release bounds do not weaken discovery parsing")
        check(BoundedJson.releaseArray(ByteArray(2*1024*1024+1))==null,"Release feed body bounded")
        check(BoundedJson.releaseArray("[\"\\ud800\"]".toByteArray())==null,"Invalid Unicode rejected")
        val folder=Files.createTempDirectory("synthetic-update-").toFile()
        try {
            val file=File(folder,"update.apk");var progress=0
            val direct=Connection()
            UpdateTransfer(3){direct}.use{it.download(target,file){n,total->check(n<=total,"Progress bounded");progress++}}
            check(file.readBytes().contentEquals(data) && progress>0 && direct.ended,"Verified download retained and connection closed")
            val redir=Connection(status=302,location="https://release-assets.githubusercontent.com/synthetic")
            var calls=0
            UpdateTransfer(3){if(calls++==0)redir else Connection()}.use{it.download(target,file){_,_->}}
            check(calls==2 && redir.ended,"Permitted HTTPS redirect followed and closed")
            calls=0
            fail("Off-origin redirect not followed"){UpdateTransfer(3){calls++;Connection(status=302,location="http://198.18.0.1/a")}.use{it.download(target,file){_,_->}}}
            check(calls==1 && !file.exists(),"Rejected redirect leaves no APK")
            fail("Redirect loop bounded"){UpdateTransfer(3){calls++;Connection(status=302,location="https://release-assets.githubusercontent.com/loop")}.use{it.download(target,file){_,_->}}}
            check(calls==5,"At most four total asset requests")
            for(c in listOf(Connection(status=403),Connection(status=429),Connection(status=404),Connection(body=data+1,size=-1),Connection(body=data.dropLast(1).toByteArray(),size=-1),Connection(body=ByteArray(data.size){1}))) {
                fail("Failed download rejected"){UpdateTransfer(3){c}.use{it.download(target,file){_,_->}}}
                check(!file.exists() && c.ended,"Failed download cleaned up")
            }
            val cancelled=UpdateTransfer(3){error("Must not connect")};cancelled.close()
            fail("Cancelled request never connects"){cancelled.download(target,file){_,_->}}
            val slow=Connection(slow=true)
            fail("Whole-operation deadline closes stalled read"){UpdateTransfer(1){slow}.use{it.download(target,file){_,_->}}}
            check(slow.ended && !file.exists(),"Deadline closes connection and removes partial APK")
            val interrupted=Connection(slow=true)
            val transfer=UpdateTransfer(10){interrupted}
            val stopped=CountDownLatch(1)
            var cancellation:Exception?=null
            val thread=Thread {try{transfer.download(target,file){_,_->}}catch(e:Exception){cancellation=e}finally{stopped.countDown()}}
            thread.start()
            check(interrupted.started.await(2,TimeUnit.SECONDS),"Cancellation fixture reaches active body read")
            transfer.close()
            check(stopped.await(2,TimeUnit.SECONDS) && cancellation is AppUpdates.Failure,"Explicit cancellation stops active transfer")
            check(interrupted.ended && !file.exists(),"Explicit cancellation closes connection and removes partial APK")
            thread.join(2000)
            val feed=Connection("[${release()}]".toByteArray())
            UpdateTransfer(3){feed}.use{check(AppUpdates.latest(it.feed())==target,"Public feed parsed through bounded transfer")}
        } finally {folder.deleteRecursively()}
        println("PASS · $checks app update policy, parsing and transport checks")
    }
}
