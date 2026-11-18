package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.net.nsd.NsdServiceInfo
import io.github.kazands.networklens.*
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic current-wire models, with no Android Network and no household connection. */
internal object GoogleCastIdentityChecks {
    private fun get(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).let { it.isAccessible=true;it.get(owner)!! }
    private fun set(owner: Any, name: String, value: Any) = owner.javaClass.getDeclaredField(name).let { it.isAccessible=true;it.set(owner,value) }
    fun run(test: Instrumentation, check: (Boolean,String)->Unit) {
        DeviceCatalogLoader.load(test.targetContext)
        val net=NetworkState().apply { ip="198.18.0.1";prefix=24;key="synthetic-google-cast";addresses.add(ip);prefixes[ip]=prefix }
        val op=Discovery(test.targetContext,net)
        set(op,"accepting",true);set(op,"enriching",true)
        val found=get(op,"found") as MutableMap<String,Device>
        val fresh=get(op,"freshResponders") as MutableSet<String>
        val current=Discovery::class.java.getDeclaredMethod("applyCurrentService",Device::class.java,MdnsPacket.Service::class.java).apply { isAccessible=true }
        val apply=Discovery::class.java.getDeclaredMethod("applyCurrentMetadata",Device::class.java,LocalMetadata.Request::class.java,LocalMetadata.Report::class.java).apply { isAccessible=true }
        val gate=CountDownLatch(1);val entered=CountDownLatch(2)
        val work=get(op,"enrichment") as DiscoveryWork
        repeat(2) {work.submit(DiscoveryWork.Lane.CAST) {entered.countDown();try {gate.await()} catch (_:InterruptedException) {Thread.currentThread().interrupt()} } }
        fun peer(n:Int)=Device().apply {ip="198.18.0.$n";found[ip]=this;fresh.add(ip)}
        fun service(model:String,n:Int,group:Boolean=false)=MdnsPacket.Service("Synthetic receiver $n","_googlecast._tcp.").apply {
            host="synthetic-google-$n.local";port=8009
            attributes["fn"]="Synthetic room $n".toByteArray()
            attributes["md"]=model.toByteArray()
            attributes["ca"]=(if(group)"32" else "5").toByteArray()
        }
        val cases=listOf(
            Triple("Google Nest Mini","Google Nest Mini",DeviceCatalog.Category.SPEAKER),
            Triple("Google Home","Google Home",DeviceCatalog.Category.SPEAKER),
            Triple("Google Home Mini","Google Home Mini",DeviceCatalog.Category.SPEAKER),
            Triple("Nest Audio","Google Nest Audio",DeviceCatalog.Category.SPEAKER),
            Triple("Google Home Max","Google Home Max",DeviceCatalog.Category.SPEAKER),
            Triple("Google Home Speaker","Google Home Speaker",DeviceCatalog.Category.SPEAKER),
            Triple("Google Home Hub","Google Nest Hub",DeviceCatalog.Category.SMART_DISPLAY),
            Triple("Nest Hub Max","Google Nest Hub Max",DeviceCatalog.Category.SMART_DISPLAY),
            Triple("Chromecast","Google Chromecast",DeviceCatalog.Category.TV_PLAYER),
            Triple("Chromecast Ultra","Google Chromecast Ultra",DeviceCatalog.Category.TV_PLAYER),
            Triple("Chromecast with Google TV","Google Chromecast with Google TV",DeviceCatalog.Category.TV_PLAYER),
            Triple("Google TV Streamer","Google TV Streamer",DeviceCatalog.Category.TV_PLAYER),
            Triple("Chromecast Audio","Google Chromecast Audio",DeviceCatalog.Category.AUDIO_RECEIVER),
            Triple("Nest Wifi Point","Google Nest Wifi Point",DeviceCatalog.Category.ACCESS_POINT),
            Triple("Pixel Tablet","Google Pixel Tablet",DeviceCatalog.Category.TABLET))
        try {
            check(entered.await(3,TimeUnit.SECONDS),"Google Cast fixture holds metadata workers without network access")
            for ((index,case) in cases.withIndex()) {
                val (model,title,category)=case;val n=index+30
                val d=peer(n);val record=service(model,n)
                current.invoke(op,d,record)
                check(d.model==model && d.manufacturer.isEmpty() && d.mac.isEmpty(),"Current $model stays raw without invented manufacturer or MAC")
                check(d.presentation().title==title && d.presentation().subtitle==category.label+" · Synthetic room $n",
                    "$model has its product title and preserves the room name underneath")
                check(d.classification().category==category && d.capabilities().contains("Google Cast"),
                    "$model separates its physical type from its Cast capability")
                val icon=when(category) {
                    DeviceCatalog.Category.SPEAKER,DeviceCatalog.Category.AUDIO_RECEIVER->R.drawable.ic_device_speaker
                    DeviceCatalog.Category.SMART_DISPLAY->R.drawable.ic_device_monitor
                    DeviceCatalog.Category.ACCESS_POINT->R.drawable.ic_device_router
                    DeviceCatalog.Category.TABLET->R.drawable.ic_device_tablet
                    else->R.drawable.ic_device_tv_player
                }
                check(DeviceIcon.forGroup(d).drawable==icon && DeviceIcon.categoryLabel(d)==category.label,
                    "$model uses its physical icon and readable category")
                val reply=CastInfo.parse("""{"name":"Synthetic alternate room"}""".toByteArray())!!
                check(apply.invoke(op,d,LocalMetadata.request(record)!!,reply)==true,
                    "A name-only Cast HTTP reply is admitted for $model")
                check(d.model==model && d.manufacturer.isEmpty() && d.presentation().title==title && d.name=="Synthetic room $n",
                    "A successful HTTP reply omitting identity cannot erase $model from current mDNS")
                check(d.metadataLookupRows().single().second.contains("model not reported") &&
                    d.observations().any {it.field=="Model" && it.source=="mDNS current _googlecast._tcp. TXT" && it.value==model},
                    "$model retains both the method-specific HTTP outcome and the actual model source")
                check(d.json().getJSONObject("localRecognition").getString("recognizedBrand")=="Google",
                    "Interpreted Google brand stays separate from missing reported manufacturer")
                val v6=Device().apply {ip="2001:db8::$n";link("mdns-host:"+record.host)}
                val grouped=Device.grouped(listOf(d.copy(),v6)).single()
                check(grouped.groupProduct()?.product?.title==title && grouped.classification().category==category && v6.model.isEmpty(),
                    "A dual-stack $model group shares presentation without borrowing raw endpoint fields")
                val snapshot=d.copy()
                d.hint("Manufacturer","Synthetic other maker","Synthetic conflicting report",90)
                check(d.identifiedProduct()==null && d.classification().category==null && snapshot.presentation().title==title,
                    "$model manufacturer conflicts suppress interpretation without changing retained copies")
            }
            val group=peer(60);current.invoke(op,group,service("Google Nest Mini",60,true))
            check(group.model.isEmpty() && group.identifiedProduct()==null && group.classification().category==null,
                "A virtual Cast group cannot become a physical Nest Mini even with a known model token")
            val nameOnly=peer(61)
            current.invoke(op,nameOnly,service("",61).apply {attributes["fn"]="Google Home Mini".toByteArray()})
            check(nameOnly.identifiedProduct()==null && nameOnly.classification().category==null,
                "A friendly name matching Google hardware cannot create model evidence")
            val thirdParty=peer(62)
            current.invoke(op,thirdParty,service("Google Home Mini",62).apply {attributes["manufacturer"]="Synthetic other maker".toByteArray()})
            check(thirdParty.identifiedProduct()==null && thirdParty.classification().category==null,
                "A contradictory current manufacturer cannot borrow a Google product through Cast")
            val cached=peer(63)
            val info=NsdServiceInfo().apply {serviceName="Synthetic cached receiver";serviceType="_googlecast._tcp.";host=InetAddress.getByName(cached.ip);port=8009;setAttribute("md","Google Home")}
            Discovery::class.java.getDeclaredMethod("resolved",NsdServiceInfo::class.java).apply {isAccessible=true}.invoke(op,info)
            check(cached.model.isEmpty() && cached.identifiedProduct()==null,"Fresh presence does not promote cached Google model data")
            op.cancel()
            val stopped=peer(64);current.invoke(op,stopped,service("Google Nest Mini",64))
            check(stopped.model.isEmpty() && stopped.identifiedProduct()==null,"Late Google advertisements cannot identify devices after Stop")
            check(Device().apply {ip=stopped.ip}.identifiedProduct()==null,"A new scan never inherits a Google identity")
        } finally {op.cancel();gate.countDown()}
    }
}
