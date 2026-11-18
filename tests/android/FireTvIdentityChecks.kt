package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.net.nsd.NsdServiceInfo
import io.github.kazands.networklens.*
import java.net.InetAddress

/** Synthetic advertisements only, without an Android Network or LAN sockets. */
internal object FireTvIdentityChecks {
    private fun get(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).let { it.isAccessible=true;it.get(owner)!! }
    private fun set(owner: Any, name: String, value: Any) = owner.javaClass.getDeclaredField(name).let { it.isAccessible=true;it.set(owner,value) }
    fun run(test: Instrumentation, check: (Boolean,String)->Unit) {
        val net=NetworkState().apply {ip="198.18.0.1";prefix=24;key="synthetic-fire-tv";addresses.add(ip);prefixes[ip]=prefix}
        val op=Discovery(test.targetContext,net)
        set(op,"accepting",true);set(op,"enriching",true)
        val found=get(op,"found") as MutableMap<String,Device>
        val fresh=get(op,"freshResponders") as MutableSet<String>
        val current=Discovery::class.java.getDeclaredMethod("applyCurrentService",Device::class.java,MdnsPacket.Service::class.java).apply {isAccessible=true}
        fun peer(n:Int)=Device().apply {ip="198.18.0.$n";found[ip]=this;fresh.add(ip)}
        fun service(n:Int)=MdnsPacket.Service("amzn.dmgr:00000000000000000000000000000001:FixtureToken:123456",FireTvInfo.TYPE).apply {
            port=45678;host="synthetic-fire-$n.local"
            attributes["ad"]="ASYNTHETIC123".toByteArray()
        }
        try {
            val d=peer(10);val ad=service(10)
            current.invoke(op,d,ad)
            check(d.presentation().title=="Amazon Fire TV", "Technical manager ID gets a Fire TV family fallback")
            check(d.presentation().subtitle=="Casting device" && DeviceIcon.forGroup(d).drawable==R.drawable.ic_device_cast,
                "WhisperPlay has a casting icon without guessing physical hardware")
            check(d.model.isEmpty() && d.manufacturer.isEmpty() && d.mac.isEmpty() && d.identifiedProduct()==null && d.classification().category==null,
                "Service family never invents a model, manufacturer, MAC or physical type")
            check(d.capabilities()==listOf("Amazon WhisperPlay") && d.observations().any {it.value==ad.name},
                "Capability is readable and original instance remains in technical observations")
            check(d.details["Amazon device type code (advertised)"]=="ASYNTHETIC123", "Amazon type code stays separate from Model")
            d.hint("Name","Android.local","Synthetic reverse name",35)
            check(d.presentation().title=="Amazon Fire TV", "A generic Android reverse hostname cannot hide the Fire TV family")
            current.invoke(op,d,ad.apply {attributes["n"]="Synthetic media room".toByteArray()})
            check(d.presentation().title=="Synthetic media room" && d.observations().any {it.value=="Synthetic media room" && it.source.endsWith("TXT n (friendly name)")},
                "Current WhisperPlay friendly name is displayed with its specific field source")
            val raw=d.copy()
            current.invoke(op,d,service(10))
            check(d.name=="Synthetic media room" && raw.name==d.name, "Later missing names do not erase current name evidence or snapshots")
            val dual=Device().apply {ip="2001:db8::10";link("mdns-host:"+ad.host)}
            check(Device.grouped(listOf(raw,dual)).single().capabilities().contains("Amazon WhisperPlay") && dual.name.isEmpty(),
                "Dual-stack grouping shares capability without copying raw endpoint names")
            val other=peer(11)
            current.invoke(op,other,MdnsPacket.Service(ad.name,"_amzn-alexa._tcp.").apply {port=45678})
            check(other.presentation().title!="Amazon Fire TV" && other.classification().category==null,
                "An Alexa service cannot become a Fire TV")
            val cached=peer(12)
            val native=NsdServiceInfo().apply {serviceName=ad.name;serviceType=FireTvInfo.TYPE;host=InetAddress.getByName(cached.ip);port=45678;setAttribute("n","Synthetic cached room")}
            Discovery::class.java.getDeclaredMethod("resolved",NsdServiceInfo::class.java).apply {isAccessible=true}.invoke(op,native)
            check(cached.name.isEmpty() && cached.presentation().title=="Unnamed device", "Cached native records cannot supply an Amazon identity")
            val known=peer(15)
            current.invoke(op,known,service(15).apply {
                attributes["ad"]="AKPGW064GI9HE".toByteArray();attributes["n"]="Synthetic den".toByteArray()
            })
            check(known.presentation().title=="Amazon Fire TV Stick 4K" && known.presentation().subtitle=="Streaming device · Synthetic den",
                "Reviewed type code supplies product title and keeps the room name beneath it")
            check(known.model.isEmpty() && known.manufacturer.isEmpty() && known.mac.isEmpty() && known.identifiedProduct()!!.product.familyOnly,
                "Type-code interpretation preserves unknown reported fields and unspecified generation")
            check(known.classification().category==DeviceCatalog.Category.TV_PLAYER && DeviceIcon.forGroup(known).drawable==R.drawable.ic_device_tv_player,
                "A reviewed Stick code provides a streaming-device category and physical icon")
            check(known.json().getJSONObject("localRecognition").getString("recognizedBrand")=="Amazon", "Interpreted brand is distinct from reported manufacturer")
            val unknown=peer(16);val sameHost=service(16).apply {host="synthetic-fire-15.local"}
            current.invoke(op,unknown,sameHost)
            check(Device.grouped(listOf(known.copy(),unknown)).single().groupProduct()==null, "A grouped unknown but different type code suppresses the known product")
            val copy=known.copy()
            known.hint("Manufacturer","Synthetic other maker","Synthetic conflicting report",90)
            check(known.identifiedProduct()==null && known.classification().category==null && copy.presentation().title=="Amazon Fire TV Stick 4K",
                "Manufacturer conflict suppresses identity without altering retained snapshots")
            for ((appleType, appleModel) in listOf("_airplay._tcp." to "AppleTV3,2", "_device-info._tcp." to "iPad13,18")) {
                for (amazonFirst in listOf(false, true)) {
                    val mixed=peer(30)
                    val amazon=service(30).apply {attributes["ad"]="AKPGW064GI9HE".toByteArray()}
                    val apple=MdnsPacket.Service("Synthetic receiver",appleType).apply {
                        port=7000;host=amazon.host;attributes["model"]=appleModel.toByteArray()
                    }
                    current.invoke(op,mixed,if(amazonFirst)amazon else apple)
                    val before=mixed.copy()
                    val beforeProduct=before.identifiedProduct()!!.product.id
                    current.invoke(op,mixed,if(amazonFirst)apple else amazon)
                    check(mixed.identifiedProduct()==null && mixed.locallyRecognized()==null && mixed.groupRecognition()==null,
                        "Known Amazon hardware and an Apple model suppress both product interpretations in either arrival order")
                    check(mixed.classification().category==null && mixed.model==appleModel && mixed.manufacturer.isEmpty() && mixed.mac.isEmpty(),
                        "Contradictory Apple hardware categories are suppressed while reported fields remain unchanged")
                    check(mixed.copy().identifiedProduct()==null && before.identifiedProduct()!!.product.id==beforeProduct,
                        "Conflict snapshots retain suppression without changing an earlier independent snapshot")
                    current.invoke(op,mixed,service(30))
                    current.invoke(op,mixed,apple)
                    check(mixed.identifiedProduct()==null, "A later unknown Amazon code or repeated Apple model cannot erase known hardware evidence")

                    val fireEndpoint=peer(31)
                    val appleEndpoint=peer(32)
                    current.invoke(op,fireEndpoint,amazon)
                    current.invoke(op,appleEndpoint,apple)
                    val grouped=Device.grouped(if(amazonFirst)listOf(fireEndpoint,appleEndpoint) else listOf(appleEndpoint,fireEndpoint)).single()
                    check(grouped.groupProduct()==null && grouped.groupRecognition()==null,
                        "Grouped addresses cannot borrow conflicting Apple recognition across a known Amazon endpoint")
                    check(grouped.classification().category==null && grouped.classification().conflicted &&
                        DeviceIcon.forGroup(grouped).label=="Conflicting device types",
                        "Grouped Amazon/Apple conflicts cannot supply a definite primary type or physical icon")
                    val mixedGroup=Device.grouped(listOf(mixed.copy(),appleEndpoint)).single()
                    check(mixedGroup.groupProduct()==null && mixedGroup.groupRecognition()==null,
                        "An endpoint with conflicting observations cannot borrow an Apple identity from another grouped address")
                    for (members in listOf(listOf(mixed.copy(),appleEndpoint),listOf(appleEndpoint,mixed.copy()))) {
                        val checkedGroup=Device.grouped(members).single().copy()
                        check(checkedGroup.classification().category==null && checkedGroup.classification().conflicted &&
                            DeviceIcon.forGroup(checkedGroup).label=="Conflicting device types" &&
                            checkedGroup.presentation().subtitle=="Conflicting device types",
                            "Grouped conflicting evidence stays suppressed in both input orders and snapshots, including list labels")
                    }
                    check(fireEndpoint.identifiedProduct()!!.product.brand=="Amazon" && appleEndpoint.locallyRecognized()!=null,
                        "Grouping does not mutate either endpoint's independent observations")
                }
            }
            val unknownFirst=peer(33)
            current.invoke(op,unknownFirst,service(33))
            val appleOnly=MdnsPacket.Service("Synthetic Apple receiver","_airplay._tcp.").apply {
                port=7000;host="synthetic-fire-33.local";attributes["model"]="AppleTV3,2".toByteArray()
            }
            current.invoke(op,unknownFirst,appleOnly)
            check(unknownFirst.locallyRecognized()!=null, "An unknown Amazon code alone does not establish conflicting hardware")
            current.invoke(op,unknownFirst,service(33).apply {attributes["ad"]="AKPGW064GI9HE".toByteArray()})
            check(unknownFirst.copy().identifiedProduct()==null && unknownFirst.classification().category==null,
                "Known hardware remains effective even when an unknown code was observed first")
            for (amazonFirst in listOf(false,true)) {
                val mixed=peer(40)
                val amazon=service(40).apply {attributes["ad"]="AKPGW064GI9HE".toByteArray()}
                val cast=MdnsPacket.Service("Synthetic Cast receiver","_googlecast._tcp.").apply {
                    port=8009;host=amazon.host;attributes["md"]="Google Nest Mini".toByteArray()
                }
                current.invoke(op,mixed,if(amazonFirst)amazon else cast)
                val before=mixed.copy()
                val beforeType=before.classification().category
                current.invoke(op,mixed,if(amazonFirst)cast else amazon)
                check(mixed.identifiedProduct()==null && mixed.classification().category==null && mixed.classification().conflicted,
                    "A current Google model cannot reclassify contradictory Amazon hardware as a speaker in either arrival order")
                check(DeviceIcon.forGroup(mixed).label=="Conflicting device types" && mixed.model=="Google Nest Mini" && mixed.manufacturer.isEmpty(),
                    "Conflict icons preserve the reported model and missing manufacturer")
                check(mixed.copy().classification().conflicted && before.classification().category==beforeType,
                    "Physical conflict snapshots remain independent")
                val other=peer(41)
                current.invoke(op,other,cast)
                for (members in listOf(listOf(mixed,other),listOf(other,mixed))) {
                    val grouped=Device.grouped(members).single()
                    check(grouped.classification().category==null && grouped.classification().conflicted && grouped.groupProduct()==null,
                        "A grouped Cast address cannot restore the rejected speaker type")
                }
            }
            op.cancel()
            val stopped=peer(14);current.invoke(op,stopped,service(14))
            check(stopped.name.isEmpty() && stopped.services.isEmpty(), "Late Amazon responses cannot update a stopped scan")
            check(Device().apply {ip=d.ip}.presentation().title=="Unnamed device", "Fresh scans do not reuse a prior occupant's Fire TV identity")
        } finally {op.cancel()}
    }
}
