import io.github.kazands.networklens.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

object ProductModelIndexTest {
    private var checks=0
    private fun check(ok:Boolean, why:String) { checks++;if(!ok)throw AssertionError(why) }
    private fun parse(value:String)=ProductModelIndex.read(ByteArrayInputStream(value.toByteArray()))
    @JvmStatic fun main(args:Array<String>) {
        val file=File("android/app/src/main/res/raw/product_models.gz")
        val index=GZIPInputStream(file.inputStream()).use(ProductModelIndex::read)
        check(index.size==256338,"All pinned model aliases load from the actual packaged resource")
        val cases=listOf(Triple("Samsung","SM-S918B","Samsung Galaxy S23 Ultra"),Triple("Google","Pixel 8","Google Pixel 8"),
            Triple("QNAP","TS-469U","QNAP TS-469U"),Triple("Sony","PlayStation 3","Sony PlayStation 3"),
            Triple("D-Link","DWL-2100AP","D-Link DWL-2100AP"),
            Triple("Brother","HL-L6200DW","Brother HL-L6200DW"), Triple("NETGEAR","R7000","NETGEAR R7000"),
            Triple("Lenovo","ThinkServer RD650","Lenovo ThinkServer RD650"), Triple("Axis","M1033-W","Axis M1033-W"))
        for ((maker,model,title) in cases) {
            check(index.lookup(maker,model)?.title==title,"Exact $maker model selects its source title")
            check(index.lookup(maker,maker+" "+model)?.title==title,"A corroborated make-and-model field accepts its exact brand prefix")
            check(index.lookup(maker.lowercase(),model.lowercase())?.title==title,"Case does not change $maker model meaning")
            check(index.lookup("Fixture rival",model)==null && index.lookup("",model)==null,"A model cannot borrow another manufacturer's catalogue entry")
            check(index.lookup(maker,"${model}extra")==null,"No substring product matching")
        }
        check(index.lookup("NVIDIA","SHIELD Android TV")==null,"Shared SHIELD variants cannot claim an exact Pro or generation from the broad index")
        val header="# Network Lens product model catalogue v1\n"
        for (value in listOf("bad\n",header+"sony\tps3\tSony\tPlayStation 3\n",header+"SONY\tps3\tSony\tSony PS3\tNIST\n",
            header+"sony\tps3\tOther\tSony PS3\tNIST\n",header+"sony\tps3\tSony\tSony PS3\tNIST\nsony\tps3\tSony\tSony PS3\tNIST\n",
            header+"sony\tps4\tSony\tSony PS4\tNIST\nsony\tps3\tSony\tSony PS3\tNIST\n",header+"sony\tps3\tSony\tSony\u202e PS3\tNIST\n")) {
            var rejected=false;try{parse(value)}catch(_:IOException){rejected=true};check(rejected,"Malformed, ambiguous or unsorted catalogue resource fails closed")
        }
        var longRejected=false
        try { parse(header+"x".repeat(900)) } catch (_:IOException) { longRejected=true }
        check(longRejected,"A corrupt long line is rejected before unbounded allocation")
        var utfRejected=false
        try { ProductModelIndex.read(ByteArrayInputStream(header.toByteArray()+byteArrayOf(0xc3.toByte(),0x28))) } catch (_:IOException) { utfRejected=true }
        check(utfRejected,"Malformed UTF-8 cannot enter catalogue names")
        DeviceCatalog.install(index)
        val evidence=DeviceCatalog.Evidence()
        evidence.offer("Model","SM-S918B","Current model fixture")
        check(evidence.match()==null,"A broad model code alone cannot seed identification")
        evidence.offer("Manufacturer","Samsung","Current manufacturer fixture")
        check(evidence.match()?.product?.title=="Samsung Galaxy S23 Ultra","Manufacturer may arrive after the model")
        check(evidence.match()!!.source.contains("Current manufacturer fixture") && evidence.match()!!.input.contains("manufacturer=samsung"),"Both required inputs retain provenance")
        val snapshot=evidence.copy();evidence.offer("Manufacturer","Fixture rival","Conflicting fixture")
        repeat(30){evidence.offer("Manufacturer","Samsung","Later fixture $it")}
        check(evidence.match()==null && snapshot.match()!=null,"A broad manufacturer conflict survives later corroboration and copying")
        val printer=DeviceCatalog.Evidence().apply {offer("Manufacturer","HP Inc.","Current manufacturer");offer("Model","HP LaserJet P4015","Current IPP model")}
        check(printer.match()?.product?.let {it.title=="HP LaserJet P4015" && it.familyOnly}==true,"A CPE product code cannot invent its English-title SKU or revision")
        printer.offer("Model","LaserJet P4015","Current model-only response")
        check(printer.match()?.product?.title=="HP LaserJet P4015","Corroborating make-and-model and model-only reports do not create a false conflict")
        val wrong=DeviceCatalog.Evidence().apply {offer("Manufacturer","Fixture rival","Current fixture");offer("Model","SM-S918B","Current fixture")}
        check(wrong.match()==null,"A foreign manufacturer cannot match a globally distinctive model code")
        val ps5=DeviceCatalog.Evidence().apply {offer("Model","PS5","Unrelated current model")}
        check(ps5.match()==null,"A reusable PS5 model string needs Sony evidence or the console protocol")
        ps5.offer("Manufacturer","Sony","Current manufacturer")
        check(ps5.match()?.product?.title=="Sony PlayStation 5","Reported Sony can corroborate an explicit PS5 model")
        val collision=parse(header+"hp\tfoo\tHP\tHP Foo\tNIST\nhp\thp foo\tHP\tHP Different\tNIST\n")
        DeviceCatalog.install(collision)
        val disputed=DeviceCatalog.Evidence().apply {offer("Manufacturer","HP","Current maker");offer("Model","foo","Current model")}
        check(disputed.match()?.product?.title=="HP Foo","An explicit table entry supplies its own title")
        disputed.offer("Model","HP foo","A different explicit catalogue model")
        check(disputed.match()==null,"Brand-prefix normalization cannot override a different explicit model mapping")
        DeviceCatalog.install(index)
        val start=System.nanoTime()
        var exact=true
        repeat(50000){exact=exact && index.lookup("Samsung","SM-S918B")?.title=="Samsung Galaxy S23 Ultra"}
        check(exact,"Large catalogue lookups remain exact")
        println("PASS · $checks global catalogue checks; 50,000 qualified lookups in ${(System.nanoTime()-start)/1000000} ms (host timing only)")
    }
}
