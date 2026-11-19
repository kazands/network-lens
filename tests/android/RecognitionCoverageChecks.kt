package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*

/** Current synthetic observations only. No Wi-Fi, native discovery or physical-device access. */
internal object RecognitionCoverageChecks {
    fun run(test:Instrumentation,check:(Boolean,String)->Unit) {
        fun set(owner:Any,key:String,value:Any?) = owner.javaClass.getDeclaredField(key).let {it.isAccessible=true;it.set(owner,value)}
        val selfOnly=Discovery(test.targetContext,NetworkState().apply {ip="198.18.0.1";prefix=24;key="synthetic-coverage";prefixes[ip]=24;addresses.add(ip)})
        set(selfOnly,"accepting",true);set(selfOnly,"enriching",true);set(selfOnly,"range",IpRange("198.18.0.1",32))
        try {
            for(method in listOf("startLanIdentities","startTuyaAnnouncements","startLifxIdentity","startEasySmartDiscovery","startNetworkEquipment"))
                Discovery::class.java.getDeclaredMethod(method).apply {isAccessible=true}.invoke(selfOnly)
            val threads=selfOnly.javaClass.getDeclaredField("lanThreads").let {it.isAccessible=true;it.get(selfOnly) as List<*>}
            check(threads.isEmpty(),"Self-only scan starts no added broadcast/passive workers")
            check(selfOnly.sources().values.all {it.startsWith("Not used")},"Skipped discovery remains explicit and complete")
        } finally {selfOnly.cancel()}
        val old=MatterInfo.index
        try {
            MatterInfo.index=MatterInfo.Index.read("DCL1\t20261117\n123\t456\tFixture Maker\tFixture Thermostat\tFT-1\t769\n".byteInputStream())
            val device=Device().apply {ip="192.0.2.40";seen=true;service("_spotify-connect._tcp.")
                recognizeMatter(MatterInfo.Report(123,456,769,"Fixture room"),"Synthetic Matter TXT")}
            check(device.presentation().title=="Fixture Maker Fixture Thermostat","Matter registry reaches device title")
            check(device.manufacturer.isEmpty() && device.model.isEmpty() && device.mac.isEmpty(),"Registry never fabricates reported fields")
            check(device.classification().category==DeviceCatalog.Category.THERMOSTAT,"Matter hardware wins over Spotify service")
            check(device.copy().identifiedProduct()==device.identifiedProduct(),"Matter evidence survives presentation snapshot")
            check(Device().apply {ip=device.ip}.identifiedProduct()==null,"Fresh scan has no old Matter identity")
            device.recognizeMatter(MatterInfo.Report(123,999,null,""),"Synthetic conflicting identity")
            check(device.identifiedProduct()==null && device.copy().identifiedProduct()==null,"Unknown conflicting pair survives copy")
            fun endpoint(ip:String, vendor:Int?, product:Int?) = Device().apply {
                this.ip=ip;seen=true;link("mdns-host:matter-fixture.local")
                recognizeMatter(MatterInfo.Report(vendor,product,null,""),"Synthetic current Matter TXT")
            }
            val known=endpoint("192.0.2.42",123,456)
            for ((vendor,product) in listOf(123 to 999,124 to 456)) {
                val conflicting=endpoint("2001:db8::42",vendor,product)
                check(conflicting.identifiedProduct()==null,"Unknown conflicting pair has no catalogue match")
                for (endpoints in listOf(listOf(known,conflicting),listOf(conflicting,known))) {
                    val group=Device.grouped(endpoints).single()
                    check(group.endpoints().size==2,"Matter regression exercises linked IPv4/IPv6 endpoints")
                    check(group.groupProduct()==null,"Grouped conflicting vendor/product IDs suppress exact product in either input order")
                    check(group.presentation().title!="Fixture Maker Fixture Thermostat","Conflicting grouped identity does not leak into title")
                    check(group.copy().groupProduct()==null,"Grouped snapshot retains conflict with unknown registry pair")
                }
            }
            for (peer in listOf(endpoint("2001:db8::42",123,456),endpoint("2001:db8::42",null,null))) {
                val group=Device.grouped(listOf(peer,known)).single()
                check(group.groupProduct()==known.identifiedProduct(),"Matching or absent peer identity preserves valid grouped recognition")
                check(group.copy().presentation().title=="Fixture Maker Fixture Thermostat","Compatible grouped snapshot preserves exact title")
            }
            val candidate=Device().apply {ip="192.0.2.41";discoveryFamilies(setOf("wiz"),"Synthetic WiZ reply")}
            check(candidate.presentation().title=="WiZ-compatible device" && candidate.manufacturer.isEmpty() && candidate.model.isEmpty(),"Unknown WiZ retail model uses explicit family fallback")
            check(candidate.copy().presentation()==candidate.presentation(),"Family fallback survives copy")
            candidate.hint("Name","Fixture custom name","Synthetic reported name",80)
            check(candidate.presentation().title=="Fixture custom name","Useful reported name preserved")
            val samsung=Device().apply {discoveryFamilies(setOf("samsungtv"),"Synthetic AirPlay match");service("_airplay._tcp.")}
            check(samsung.classification().category==null,"Samsung AirPlay alone does not force television hardware")
            val publicTv=Device().apply {hint("Model","FixtureTV-1",LocalMetadata.Kind.SAMSUNG.source+" (reported)",80)}
            check(publicTv.presentation().title=="Samsung FixtureTV-1" && publicTv.manufacturer.isEmpty(),"Samsung protocol title keeps absent reported manufacturer unknown")
            val bridge=Device().apply {discoveryFamilies(setOf("lutron_caseta"),"Synthetic complete matcher");service("_airplay._tcp.")}
            check(bridge.classification().category==DeviceCatalog.Category.BRIDGE,"Reviewed bridge evidence overrides playback capability")
            val switch=Device().apply {hint("Model","TL-SG108E",EasySmartDiscovery.MODEL_SOURCE,80);hint("Device type","TP-Link Easy Smart network switch",EasySmartDiscovery.MODEL_SOURCE,80)}
            check(switch.presentation().title=="TP-Link TL-SG108E" && switch.classification().category==DeviceCatalog.Category.NETWORK_SWITCH,"Easy Smart switch display")
            // Android's runtime regex engine must accept every generated condition.
            check(HaMatchers.rules.size==348 && HaMatchers.rules.all {it.conditions.isNotEmpty()},"All generated patterns load on Android")
            test.targetContext.resources.openRawResource(R.raw.matter_models).use {check(MatterInfo.Index.read(it).lookup(0,0)==null,"Bundled DCL snapshot loads without inferring unknown IDs")}
        } finally {MatterInfo.index=old}
    }
}
