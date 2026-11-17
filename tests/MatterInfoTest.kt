import io.github.kazands.networklens.*

object MatterInfoTest {
    private var checks=0
    private fun check(value:Boolean,why:String) {checks++;if(!value)error(why)}
    private fun parse(vararg attrs:Pair<String,String>)=MatterInfo.parse(attrs.associate {it.first to it.second.toByteArray()})
    @JvmStatic fun main(args:Array<String>) {
        val report=parse("VP" to "123+456", "DT" to "769", "DN" to "Fixture thermostat")!!
        check(report.vendor==123 && report.product==456 && report.deviceType==769L,"Decimal fields parsed")
        check(MatterInfo.category(report.deviceType)==DeviceCatalog.Category.THERMOSTAT,"Matter thermostat type")
        check(parse("vp" to "123")!!.product==null,"Vendor without product remains incomplete")
        for(v in listOf("", "-1+2", "0+1", "65536+1", "1+65536", "0x123+1", "1+2+3", " 1+2", "1.0+2")) check(parse("vp" to v)==null,"Reject malformed VP $v")
        check(parse("vp" to "1+0")!!.product==0,"Zero PID allowed")
        check(parse("dt" to "4294967296")==null,"DT overflow rejected")
        check(parse("VP" to "1+2", "vp" to "2+3")==null,"Conflicting TXT rejected")
        val data="DCL1\t20261117\n123\t456\tFixture Maker\tFixture Thermostat\tFT-1\t769\n123\t457\tFixture Maker\tFixture Lamp\tFL-1\t256\n"
        val index=MatterInfo.Index.read(data.byteInputStream())
        val match=index.lookup(123,456)!!
        check(match.product.title=="Fixture Maker Fixture Thermostat","Registry title uses exact vendor/product pair")
        check(index.lookup(124,456)==null && index.lookup(123,999)==null,"Unknown pair not guessed")
        check(index.lookup(-1,456)==null && index.lookup(123,65536)==null,"Invalid lookup cannot alias key")
        for (loaded in listOf(MatterInfo.Index.EMPTY,index)) {
            MatterInfo.index=loaded
            val matching=DeviceCatalog.Evidence();matching.offerRegistryIdentity("matter:123:456")
            check(matching.compatible(match.product),"Same raw identity corroborates a candidate regardless of registry availability")
            check(DeviceCatalog.Evidence().compatible(match.product),"An endpoint without Matter identity remains compatible")
            for (id in listOf("matter:123:999","matter:124:456","matter:123:457")) {
                val differing=DeviceCatalog.Evidence();differing.offerRegistryIdentity(id)
                check(!differing.compatible(match.product),"Different raw vendor/product pair rejects candidate even without a registry entry: $id")
                check(!differing.copy().compatible(match.product),"Snapshot preserves incompatible raw identity: $id")
                val merged=DeviceCatalog.Evidence();merged.include(differing)
                check(!merged.compatible(match.product),"Endpoint merge preserves incompatible raw identity: $id")
            }
        }
        val delayed=DeviceCatalog.Evidence();delayed.offerRegistryIdentity("matter:123:456")
        MatterInfo.index=MatterInfo.Index.EMPTY
        check(delayed.match()==null,"Unloaded registry leaves evidence unresolved")
        MatterInfo.index=index
        check(delayed.match()==match,"Identity resolves when background catalogue load finishes")
        delayed.offerRegistryIdentity("matter:123:999")
        check(delayed.match()==null,"Unknown conflicting pair also suppresses earlier match")
        MatterInfo.index=MatterInfo.Index.EMPTY
        val evidence=DeviceCatalog.Evidence();evidence.offerProduct(match)
        check(evidence.match()==match && evidence.copy().match()==match,"Registry match survives copy")
        evidence.offer("Model","FT-1","Synthetic protocol")
        check(evidence.match()==match,"Reported part corroborates product")
        val joined=DeviceCatalog.Evidence();joined.include(evidence)
        check(joined.match()==match,"Registry match survives endpoint merge")
        evidence.offerProduct(index.lookup(123,457)!!)
        check(evidence.match()==null && evidence.copy().match()==null,"Conflicting Matter IDs suppress title")
        val conflict=DeviceCatalog.Evidence();conflict.offerProduct(match);conflict.offer("Manufacturer","Other Maker","Synthetic")
        check(conflict.match()==null,"Conflicting reported manufacturer suppresses registry match")
        val unknown=DeviceCatalog.Evidence();unknown.offerProduct(match);unknown.offer("Model","Other model","Synthetic")
        check(unknown.match()==null,"Unrelated reported model suppresses registry match")
        check(MatterInfo.category(0xffffffffL)==DeviceCatalog.Category.UNSPECIFIED,"Unknown DT remains unknown")
        for(bad in listOf(data+"123\t456\tFixture Maker\tDuplicate\t\t256\n",data.replace("DCL1","DCL2"),data.replace("123\t456","65536\t456"))) {
            check(runCatching { MatterInfo.Index.read(bad.byteInputStream()) }.isFailure,"Reject malformed registry")
        }
        println("PASS: $checks Matter recognition checks")
    }
}
