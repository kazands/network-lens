package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*

/** No Android Network, sockets, household addresses or physical-device access. */
internal object BroadRecognitionChecks {
    fun run(test:Instrumentation, check:(Boolean,String)->Unit) {
        val op=Discovery(test.targetContext,NetworkState().apply {
            ip="198.18.0.1";prefix=24;key="synthetic-coverage";addresses.add(ip);prefixes[ip]=prefix
        })
        val accepting=Discovery::class.java.getDeclaredField("accepting").apply {isAccessible=true}
        val current=Discovery::class.java.getDeclaredMethod("applyCurrentService",Device::class.java,MdnsPacket.Service::class.java).apply {isAccessible=true}
        try {
            accepting.set(op,true)
            val light=Device().apply {ip="198.18.0.2"}
            val service=MdnsPacket.Service("Fixture lighting","_nanoleafapi._tcp.").apply {host="fixture-light.local";port=16021}
            current.invoke(op,light,service)
            check(light.classification().category==DeviceCatalog.Category.LIGHT,"Current lighting API supplies a physical lighting category")
            check(light.manufacturer.isEmpty() && light.model.isEmpty(),"Family/category do not invent reported model or manufacturer")
            check(light.recognitionSummary().contains("Nanoleaf"),"Unmatched named device explains recognised family")
            check(light.copy().recognitionSummary()==light.recognitionSummary(),"Recognition explanation survives UI snapshots")
            light.hint("Device type","HomeKit thermostat","Synthetic conflict",80)
            check(light.classification().conflicted && light.recognitionSummary().contains("conflicting"),"Conflicting physical roles stay unresolved")
            val software=Device().apply {ip="198.18.0.3";discoveryFamilies(setOf("plex"),"Synthetic current advertisement")}
            check(software.presentation().title=="Plex media server" && software.classification().category==null,"Software identity never implies NAS hardware")
            software.hint("Name","Fixture workstation","Synthetic name",90)
            check(software.presentation().title=="Fixture workstation","Useful names are preserved")
            val peer=Device().apply {ip="2001:db8::3";link("synthetic:shared");discoveryFamilies(setOf("plex"),"Synthetic current IPv6 advertisement")}
            software.link("synthetic:shared")
            check(Device.grouped(listOf(software,peer)).single().recognitionSummary().contains("Plex"),"Grouped addresses retain family explanation")
            val scanner=LocalMetadata.parse(LocalMetadata.Kind.ESCL,"""<s:ScannerCapabilities xmlns:s="http://schemas.hp.com/imaging/escl/2011/05/03" xmlns:p="http://www.pwg.org/schemas/2010/12/sm"><p:Version>2.0</p:Version><p:MakeAndModel>Synthetic Scanner</p:MakeAndModel></s:ScannerCapabilities>""".toByteArray())
            check(scanner?.details?.get(EsclInfo.MODEL)=="Synthetic Scanner" && scanner.hints.isEmpty(),"Android XML parser keeps scanner identity separate")
            val sharedScanner=LocalMetadata.parse(LocalMetadata.Kind.ESCL,"""<s:ScannerCapabilities xmlns:s="http://schemas.hp.com/imaging/escl/2011/05/03" xmlns:p="http://www.pwg.org/schemas/2010/12/sm"><p:Version>2.0</p:Version><p:MakeAndModel>HP Scanjet 2400</p:MakeAndModel><p:Manufacturer>HP</p:Manufacturer></s:ScannerCapabilities>""".toByteArray())!!
            val applyMetadata=Discovery::class.java.getDeclaredMethod("applyMetadata",Device::class.java,LocalMetadata.Report::class.java,String::class.java).apply {isAccessible=true}
            val computer=Device().apply {
                ip="198.18.0.5";hint("Name","Fixture computer","Synthetic current name",90)
                recognizeAppleModel("MacBookPro18,3")
                hint("Model","MacBookPro18,3","Synthetic current Apple identity",80)
                hint("Manufacturer","Apple","Synthetic current maker",80)
            }
            val original=computer.presentation();val originalProduct=computer.groupProduct()!!.product
            applyMetadata.invoke(op,computer,sharedScanner,LocalMetadata.Kind.ESCL.source + " (reported)")
            check(computer.presentation()==original && computer.groupProduct()?.product==originalProduct,"Shared scanner cannot replace an identified computer")
            check(computer.model=="MacBookPro18,3" && computer.manufacturer=="Apple" && computer.classification().category==DeviceCatalog.Category.LAPTOP,"Scanner fields cannot change host model, manufacturer or physical type")
            check(computer.details[EsclInfo.MODEL]=="HP Scanjet 2400" && computer.details[EsclInfo.MANUFACTURER]=="HP","Shared scanner details remain visible separately")
            check(computer.copy().presentation()==original && computer.copy().details[EsclInfo.MODEL]=="HP Scanjet 2400","Snapshots retain host identity and separate scanner information")
            val unnamed=Device().apply {ip="198.18.0.6"}
            applyMetadata.invoke(op,unnamed,sharedScanner,LocalMetadata.Kind.ESCL.source + " (reported)")
            check(unnamed.model.isEmpty() && unnamed.manufacturer.isEmpty() && unnamed.groupProduct()==null && unnamed.presentation().unnamed,"A shared scanner cannot identify an unknown host")
            computer.link("synthetic:scanner-host");unnamed.link("synthetic:scanner-host")
            check(Device.grouped(listOf(computer,unnamed)).single().groupProduct()?.product==originalProduct,"Grouping cannot promote scanner service details into the host's product")
            val request=LocalMetadata.Request(LocalMetadata.Kind.ESCL,8080,"/eSCL/ScannerCapabilities")
            computer.metadataLookups.queue(request);computer.metadataLookups.success(request.kind,sharedScanner)
            check(computer.metadataLookupRows().single().second=="Succeeded; scanner information is shown separately","Scanner lookup does not conflate host and scanner model sources")

            for (type in listOf("_uscan._tcp.", "_uscans._tcp.")) {
                val advertised=MdnsPacket.Service("Fixture shared scanner",type).apply {
                    host="fixture-computer.local";port=8090
                    for ((key,value) in mapOf("ty" to "HP Scanjet 2400", "usb_MFG" to "HP", "rs" to "eSCL",
                        "fn" to "Fixture scanner name", "id" to "synthetic-scanner-id", "mac" to "02:00:00:00:00:42",
                        "os" to "Scanner OS", "fv" to "Scanner firmware")) attributes[key]=value.toByteArray()
                }
                for (known in listOf(true,false)) {
                    val host=Device().apply {
                        ip=if(known) "198.18.0.7" else "198.18.0.8"
                        hint("Name","Fixture computer","Synthetic current name",90)
                        if(known) {
                            recognizeAppleModel("MacBookPro18,3")
                            hint("Model","MacBookPro18,3","Synthetic current Apple identity",80)
                            hint("Manufacturer","Apple","Synthetic current maker",80)
                        }
                    }
                    val before=host.presentation();val product=host.groupProduct()?.product
                    current.invoke(op,host,advertised)
                    check(host.presentation()==before && host.groupProduct()?.product==product,"$type advertisement preserves host identity (known=$known)")
                    check(host.model==(if(known) "MacBookPro18,3" else "") && host.manufacturer==(if(known) "Apple" else ""),"$type scanner TXT never becomes reported host model or maker")
                    check(host.mac.isEmpty() && host.hostname.isEmpty(),"$type scanner identifiers do not become host identity")
                    check(host.details["mDNS current $type model · port 8090"]=="HP Scanjet 2400" &&
                        host.details["mDNS current $type manufacturer · port 8090"]=="HP" &&
                        host.details["mDNS current $type name · port 8090"]=="Fixture shared scanner","$type advertised scanner fields remain source-labelled details")
                    check(type in host.services && 8090 in host.ports,"$type still records service and port")
                    val plan=LocalMetadata.request(advertised)
                    check(plan?.kind==LocalMetadata.Kind.ESCL && plan.secure==(type=="_uscans._tcp."),"$type retains scanner metadata transport")
                    applyMetadata.invoke(op,host,sharedScanner,LocalMetadata.Kind.ESCL.source + " (reported)")
                    current.invoke(op,host,advertised)
                    check(host.presentation()==before && host.groupProduct()?.product==product,"$type HTTP follow-up and repeated advertisement preserve host identity")
                    val snapshot=host.copy()
                    check(snapshot.presentation()==before && snapshot.details["mDNS current $type model · port 8090"]=="HP Scanjet 2400","$type snapshots preserve separate scanner details")
                    val peer=Device().apply {ip="2001:db8::7"}
                    current.invoke(op,peer,advertised)
                    check(peer.presentation().unnamed && peer.groupProduct()==null && peer.model.isEmpty() && peer.manufacturer.isEmpty(),"$type scanner cannot identify an unnamed host")
                    val routed=Discovery::class.java.getDeclaredMethod("applyService",Device::class.java,MdnsPacket.Service::class.java,String::class.java).apply {isAccessible=true}
                    routed.invoke(op,host,advertised,"Wi-Fi proxy advertisement")
                    check(host.presentation()==before && host.groupProduct()?.product==product && host.mac.isEmpty(),"$type routed scanner advertisement preserves host identity too")
                    val grouped=Device.grouped(listOf(host,peer))
                    check(grouped.size==1 && grouped.single().groupProduct()?.product==product,"$type host-address grouping cannot promote scanner identity")
                }
            }

            // Exercise Discovery's actual completion predicate with an active receiving
            // family after all ordinary discovery queues have settled. No sockets.
            val release=java.util.concurrent.CountDownLatch(1)
            val receiver=Thread({release.await()},"synthetic-mdns-receiver").apply {start()}
            val threadField=Discovery::class.java.getDeclaredField("mdnsThread").apply {isAccessible=true}
            val retries=Discovery::class.java.getDeclaredField("mdnsRetries").apply {isAccessible=true}.get(op) as MdnsRetries
            val pending=Discovery::class.java.getDeclaredMethod("mdnsPending").apply {isAccessible=true}
            val retryPending=Discovery::class.java.getDeclaredMethod("mdnsRetriesPending",java.lang.Long.TYPE).apply {isAccessible=true}
            try {
                threadField.set(op,receiver)
                val now=System.nanoTime()
                check(pending.invoke(op)==false,"Ordinary discovery is settled before late recovery is admitted")
                retries.recover(listOf("_http._tcp."))
                check(pending.invoke(op)==true,"Late recovery keeps the actual Discovery completion predicate pending")
                check(retries.due(false,now,2)==listOf("_http._tcp."),"Queued late recovery gets a send opportunity")
                check(retryPending.invoke(op,now + 1_199_999_999L)==true,"Discovery retains the final response window")
                check(retryPending.invoke(op,now + 1_200_000_000L)==false,"Inactive IPv6 does not extend completed IPv4 recovery")
            } finally {release.countDown();receiver.join(1000);threadField.set(op,null)}
            check(retryPending.invoke(op,System.nanoTime())==false,"Absent or exited receivers do not keep a failed scan waiting")
            op.cancel()
            val late=Device().apply {ip="198.18.0.4"}
            current.invoke(op,late,service)
            check(late.services.isEmpty() && late.classification().category==null,"Cancelled discovery rejects late family and physical-type evidence")
            current.invoke(op,late,MdnsPacket.Service("Fixture scanner","_uscan._tcp.").apply {port=8090;attributes["ty"]="HP Scanjet 2400".toByteArray()})
            check(late.services.isEmpty() && late.details.isEmpty(),"Cancelled discovery rejects scanner advertisements too")
            check(Device().recognitionSummary()==RecognitionCopy.UNNAMED,"Fresh endpoint inherits no prior identity")
        } finally {op.cancel()}
    }
}
