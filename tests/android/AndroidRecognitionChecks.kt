package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.net.nsd.NsdServiceInfo
import io.github.kazands.networklens.*
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic scope with no Android Network: cannot open a household connection. */
internal object AndroidRecognitionChecks {
    private fun get(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).let { it.isAccessible=true; it.get(owner)!! }
    private fun set(owner: Any, name: String, value: Any) = owner.javaClass.getDeclaredField(name).let { it.isAccessible=true; it.set(owner,value) }
    fun run(test: Instrumentation, check: (Boolean,String)->Unit) {
        DeviceCatalogLoader.load(test.targetContext)
        val op=Discovery(test.targetContext,NetworkState().apply { ip="198.18.0.1";prefix=24;key="synthetic-android";prefixes[ip]=prefix;addresses.add(ip) })
        set(op,"accepting",true);set(op,"enriching",true)
        val found=get(op,"found") as MutableMap<String,Device>;val fresh=get(op,"freshResponders") as MutableSet<String>
        val plans=get(op,"metadataPlans") as MetadataPlans
        val current=Discovery::class.java.getDeclaredMethod("applyCurrentService",Device::class.java,MdnsPacket.Service::class.java).apply { isAccessible=true }
        val apply=Discovery::class.java.getDeclaredMethod("applyCurrentMetadata",Device::class.java,LocalMetadata.Request::class.java,LocalMetadata.Report::class.java).apply { isAccessible=true }
        val work=get(op,"enrichment") as DiscoveryWork; val gate=CountDownLatch(1);val started=CountDownLatch(2)
        repeat(2) { work.submit(DiscoveryWork.Lane.CAST) { started.countDown();try{gate.await()}catch(_:InterruptedException){Thread.currentThread().interrupt()} } }
        check(started.await(3,TimeUnit.SECONDS),"Cast metadata uses its bounded worker lane")
        fun peer(n:Int)=Device().apply { ip="198.18.0.$n";found[ip]=this;fresh.add(ip) }
        fun service(group:Boolean=false)=MdnsPacket.Service(if(group)"Synthetic everywhere" else "Synthetic named receiver","_googlecast._tcp.").apply {
            port=if(group)32123 else 8009;host="synthetic-receiver.local";attributes["ca"]=(if(group)"36" else "5").toByteArray()
            attributes["id"]="synthetic-shared-group-id".toByteArray()
            if(group)attributes["md"]="Google Cast Group".toByteArray()
        }
        val request=LocalMetadata.request(service())!!
        val report=CastInfo.parse("""{"name":"Synthetic alternate room","device_info":{"model_name":"SHIELD Android TV","manufacturer":"NVIDIA"}}""".toByteArray())!!
        try {
            val cached=peer(2)
            val info=NsdServiceInfo().apply { serviceName="Synthetic cached Cast";serviceType="_googlecast._tcp.";host=InetAddress.getByName(cached.ip);port=8009;setAttribute("md","SHIELD Android TV") }
            Discovery::class.java.getDeclaredMethod("resolved",NsdServiceInfo::class.java).apply { isAccessible=true }.invoke(op,info)
            check(plans.size()==0 && cached.model.isEmpty(),"Fresh presence plus cached Cast metadata cannot establish current identity or queue a lookup")
            val named=peer(3).apply { hint("Name","Synthetic chosen room","Current friendly name",90) }
            current.invoke(op,named,service())
            repeat(4){current.invoke(op,named,service())}
            check(plans.size()==1 && named.metadataLookups.snapshot().single().state==MetadataLookups.State.QUEUED,"An already named receiver still gets exactly one model/manufacturer lookup")
            check(apply.invoke(op,named,request,report)==true,"Current requested metadata is admitted")
            check(named.name=="Synthetic chosen room" && named.manufacturer=="NVIDIA" && named.model=="SHIELD Android TV","Additional identity fields preserve the useful reported name")
            check(named.presentation().title=="NVIDIA SHIELD TV" && named.classification().category==DeviceCatalog.Category.TV_PLAYER,"Receiver report reaches existing canonical product and physical category")
            check(named.observations().any{it.field=="Model" && it.source=="Cast device information (reported)"} && named.mac.isEmpty(),"New metadata is source-labelled without fabricated MACs")
            check(named.metadataLookupRows().single().second.startsWith("Succeeded"),"Lookup outcome is visible per endpoint")
            val snapshot=named.copy()
            current.invoke(op,named,service(true))
            check(named.identifiedProduct()!=null && named.name=="Synthetic chosen room" && named.details.values.contains("Synthetic everywhere"),"A group hosted by a receiver cannot overwrite or conflict with its hardware identity")
            check(snapshot.services.none{it=="Cast speaker group"},"Progress snapshots do not change with later group advertisements")
            val group1=peer(4);val group2=peer(5)
            current.invoke(op,group1,service(true));current.invoke(op,group2,service(true))
            check(group1.model.isEmpty() && group1.manufacturer.isEmpty() && group1.name.isEmpty() && group1.links().isEmpty(),"Group model, name, hostname and ID do not become hardware identity")
            check(plans.size()==1 && Device.grouped(listOf(group1,group2)).size==2,"Shared group IDs never merge two physical endpoints or queue hardware metadata")
            check(DeviceIcon.forGroup(group1).label=="Speaker group service" && group1.classification().category==null,"Group capability never claims a physical speaker category")
            current.invoke(op,group1,service())
            check(plans.size()==2,"A later physical receiver advertisement can still request metadata")
            val stale=peer(6);current.invoke(op,stale,service());fresh.remove(stale.ip)
            check(apply.invoke(op,stale,request,report)==false,"Response without current presence cannot supply a model")
            val replaced=peer(7);current.invoke(op,replaced,service());found[replaced.ip]=Device().apply{ip=replaced.ip}
            check(apply.invoke(op,replaced,request,report)==false,"A response cannot apply to an address's replaced occupant")
            val unknown=peer(8);current.invoke(op,unknown,service())
            apply.invoke(op,unknown,request,LocalMetadata.Report(mapOf("Name" to "Synthetic room")))
            check(unknown.metadataLookupRows().single().second.contains("model not reported") && unknown.model.isEmpty(),"Partial metadata explicitly reports missing fields")
            for((n,case) in listOf(Triple("Google","Pixel 8",DeviceCatalog.Category.PHONE),Triple("Samsung","SM-T720",DeviceCatalog.Category.TABLET),Triple("OnePlus","A0001",DeviceCatalog.Category.PHONE)).withIndex()) {
                val (maker,model,category)=case
                val d=peer(10+n).apply { hint("Model",model,"Synthetic model report",80);hint("Manufacturer",maker,"Synthetic manufacturer report",80);service("_spotify-connect._tcp.");hint("Device type","Audio service","Synthetic capability",60) }
                check(d.classification().category==category && d.classification().facts.any{it.source.contains("LineageOS@")},"$maker physical category leads audio capability and retains attribution")
                val icon=if(category==DeviceCatalog.Category.PHONE)R.drawable.ic_device_smartphone else R.drawable.ic_device_tablet
                check(DeviceIcon.forGroup(d).drawable==icon && d.presentation().subtitle.contains(category.label),"$maker receives the correct native icon and category label")
                val copy=d.copy();d.hint("Manufacturer","Synthetic other maker","Synthetic conflicting report",90)
                check(d.classification().category==null && copy.classification().category==category,"Conflicts clear interpretation without corrupting earlier copies")
            }
            val pending=peer(20);current.invoke(op,pending,service())
            op.cancel()
            check(pending.metadataLookups.snapshot().single().state==MetadataLookups.State.STOPPED && apply.invoke(op,pending,request,report)==false,"Stop seals outcomes and rejects late model responses")
            check(Device().apply{ip=pending.ip}.model.isEmpty(),"A new scan never inherits prior Android identity")
        } finally { op.cancel();gate.countDown() }
    }
}
