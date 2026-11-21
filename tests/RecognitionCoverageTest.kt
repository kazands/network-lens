import io.github.kazands.networklens.*
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Independent synthetic observations, not generated from the matcher definitions. */
object RecognitionCoverageTest {
    private var checks=0
    private fun check(value:Boolean,message:String) {checks++;if(!value)error(message)}
    private fun service(type:String,vararg attrs:Pair<String,String>)=MdnsPacket.Service("Synthetic device",type).apply {
        port=8080;host="fixture.local";attrs.forEach {(k,v)->attributes[k]=v.toByteArray()}
    }
    @JvmStatic fun main(args:Array<String>) {
        val fixtures=listOf(
            "_nanoleafapi._tcp." to "nanoleaf", "_wled._tcp." to "wled", "_shelly._tcp." to "shelly",
            "_heos-audio._tcp." to "heos", "_ecobee._tcp." to "ecobee", "_esphomelib._tcp." to "esphome",
            "_bond._tcp." to "bond", "_airgradient._tcp." to "airgradient", "_dkapi._tcp." to "daikin",
            "_homeconnect._tcp." to "home_connect", "_homewizard._tcp." to "homewizard",
            "_googlecast._tcp." to "cast", "_androidtvremote2._tcp." to "androidtv_remote",
            "_volumio._tcp." to "volumio", "_devialet-http._tcp." to "devialet",
            "_bangolufsen._tcp." to "bang_olufsen", "_lifx._udp." to "lifx",
            "_elg._tcp." to "elgato", "_haven._tcp." to "haven_iaq", "_fbx-api._tcp." to "freebox")
        for((type,family) in fixtures) {
            for(observed in listOf(type,type.uppercase(),type+"local.")) {
                val matches=DiscoveryMatchers.mdns(service(observed))
                check(family in matches,"Current service maps to family $family: $observed")
                check(DiscoveryMatchers.family(family)!=null,"Matched family has reviewed presentation")
                check(DiscoveryMatchers.mdns(service("_synthetic-unknown._tcp.","model" to family)).isEmpty(),"A name/model alone cannot trigger $family")
            }
        }
        for(type in listOf("_airplay._tcp.","_raop._tcp.","_googlecast._tcp.","_smb._tcp.","_http._tcp.","_ipp._tcp.","_uscan._tcp."))
            check(HardwareClassification.serviceKind(type).isEmpty(),"General service cannot classify physical host: $type")
        for(type in listOf("_nanoleafapi._tcp.","_wled._tcp."))
            check(HardwareClassification.reported(HardwareClassification.serviceKind(type))==DeviceCatalog.Category.LIGHT,"Specific lighting API classifies lighting")
        for(family in listOf("apple_tv","plex","cast","synology_dsm","heos","esphome","vizio","braviatv"))
            check(DiscoveryMatchers.family(family)!!.hardware.isEmpty(),"Family does not force physical type: $family")
        check(DiscoveryMatchers.preferredFamily(listOf("nanoleaf","homekit_controller","matter"))?.label=="Nanoleaf device","Generic services do not hide a specific family")
        check(DiscoveryMatchers.preferredFamily(listOf("nanoleaf","shelly"))==null,"Conflicting ecosystems remain ambiguous")
        check(DiscoveryMatchers.preferredFamily(listOf("powerfox","powerfox_local"))?.label=="powerfox device","Equivalent integration labels coalesce")
        check(HaMatchers.rules.all {DiscoveryMatchers.family(it.domain)!=null},"Every pinned integration has an interpretation policy")
        val retry=MdnsRetries();retry.request("_http._tcp.")
        check(retry.due(0,2).size==1,"First query")
        check(retry.due(TimeUnit.SECONDS.toNanos(2),2).size==1,"Second query")
        check(retry.due(TimeUnit.SECONDS.toNanos(8),2).isEmpty(),"No unsolicited unlimited retries")
        check(retry.recover(listOf("_http._tcp."))==1,"One explicit recovery")
        check(retry.due(TimeUnit.SECONDS.toNanos(8),2).size==1,"Recovery query is available")
        retry.recover(listOf("_http._tcp."))
        check(retry.due(TimeUnit.SECONDS.toNanos(10),2).isEmpty(),"Repeated recovery request cannot reset bounds")
        check(String(SsdpDiscovery.query("ssdp:all")).contains("HOST: 239.255.255.250:1900"),"IPv4 SSDP host")
        val xml="""<scan:ScannerCapabilities xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03" xmlns:pwg="http://www.pwg.org/schemas/2010/12/sm"><pwg:Version>2.0</pwg:Version><pwg:MakeAndModel>Synthetic Scan 100</pwg:MakeAndModel><pwg:Manufacturer>Synthetic</pwg:Manufacturer></scan:ScannerCapabilities>"""
        check(EsclInfo.applies("_USCAN._TCP") && EsclInfo.applies("_uscans._tcp."),"Scanner isolation accepts both canonical service types")
        check(!EsclInfo.applies("_ipp._tcp.") && !EsclInfo.applies("_uscan._udp."),"Scanner isolation does not intercept unrelated services")
        val scannerReport=EsclInfo.parse(xml.toByteArray())!!
        check(scannerReport.details[EsclInfo.MODEL]=="Synthetic Scan 100" && scannerReport.details[EsclInfo.MANUFACTURER]=="Synthetic","Scanner identity remains source-labelled service details")
        check(scannerReport.hints.isEmpty() && scannerReport.mac.isEmpty(),"Shared scanner cannot supply host identity or grouping evidence")
        val scannerLookup=MetadataLookups()
        scannerLookup.queue(LocalMetadata.Request(LocalMetadata.Kind.ESCL,8080,"/eSCL/ScannerCapabilities"))
        scannerLookup.success(LocalMetadata.Kind.ESCL,scannerReport)
        check(scannerLookup.snapshot().single().description()=="Succeeded; scanner information is shown separately","Scanner success does not claim reported scanner fields were missing")
        check(EsclInfo.parse(xml.replace("</scan:ScannerCapabilities>","<pwg:MakeAndModel>Other</pwg:MakeAndModel></scan:ScannerCapabilities>").toByteArray())==null,"Duplicate scanner model rejected")
        check(EsclInfo.parse(xml.replace("http://schemas.hp.com/imaging/escl/2011/05/03","urn:wrong").toByteArray())==null,"Wrong scanner namespace rejected")
        check(EsclInfo.parse(("<!DOCTYPE x [<!ENTITY y SYSTEM 'file:///synthetic'>]>"+xml).toByteArray())==null,"External entities rejected")
        val request=LocalMetadata.request(service("_uscans._tcp.","rs" to "eSCL"))!!
        check(request.secure && request.path=="/eSCL/ScannerCapabilities","Secure advertised scanner root retained")
        check(String(request.bytes(InetAddress.getByName("198.18.0.2"))).startsWith("GET /eSCL/ScannerCapabilities"),"No scanner job/control request")
        for(path in listOf("//evil.invalid","http://evil.invalid","../control","eSCL?secret=x","%2fadmin"))
            check(LocalMetadata.request(service("_uscan._tcp.","rs" to path))==null,"Unsafe scanner root rejected: $path")
        check(RecognitionStatus.explain(false,true,true,emptyList(),emptyList()).contains("conflicting"),"Conflict explanation")
        check(RecognitionStatus.explain(false,false,true,emptyList(),emptyList()).contains("catalogue"),"Unknown reported model distinguished from missing model")
        check(RecognitionStatus.explain(false,false,false,listOf("ESPHome device"),emptyList()).contains("ESPHome"),"Family-level evidence explained")
        check(RecognitionStatus.explain(false,false,false,emptyList(),emptyList(),named=true).contains("shared a name"),"Named endpoints never claim their name was missing")
        val noPriorRetry=MdnsRetries();noPriorRetry.recover(listOf("_http._tcp."))
        check(noPriorRetry.due(0,2).size==1 && noPriorRetry.due(TimeUnit.SECONDS.toNanos(2),2).isEmpty(),"Recovery without a resolver failure gets only one opportunity")
        val failed=MetadataLookups.Entry(LocalMetadata.Kind.ESCL,8080,false,MetadataLookups.State.FAILED)
        check(RecognitionStatus.explain(false,false,false,emptyList(),listOf(failed)).contains("did not succeed"),"Failed request isn't claimed to be absent device metadata")
        println("PASS: $checks cross-family interpretation, bounded IPv4 recovery and scanner checks (synthetic; not household accuracy)")
    }
}
