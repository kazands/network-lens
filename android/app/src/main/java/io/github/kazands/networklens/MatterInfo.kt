package io.github.kazands.networklens

import java.io.InputStream

/** Public commissionable DNS-SD fields only; no pairing, fabric access or runtime DCL query. */
internal object MatterInfo {
    data class Report(val vendor: Int?, val product: Int?, val deviceType: Long?, val name: String)
    fun parse(attrs: Map<String,ByteArray>): Report? {
        val values=DiscoveryMatchers.textAttributes(attrs) ?: return null
        val vp=values["vp"]
        var vendor:Int?=null;var product:Int?=null
        if (vp!=null) {
            if (!vp.matches(Regex("[0-9]{1,5}(?:\\+[0-9]{1,5})?"))) return null
            val ids=vp.split('+');vendor=ids[0].toInt().takeIf {it in 1..65535} ?: return null
            if(ids.size==2) product=ids[1].toInt().takeIf {it in 0..65535} ?: return null
        }
        val dt=values["dt"]?.let { if (!it.matches(Regex("[0-9]{1,10}"))) return null else it.toLong().takeIf {n->n<=0xffffffffL} ?: return null }
        return Report(vendor,product,dt,SafeText.label(values["dn"],128))
    }
    fun category(type: Long?) = when(type) {
        0x100L,0x101L,0x10cL,0x10dL -> DeviceCatalog.Category.LIGHT
        0x10aL,0x10bL -> DeviceCatalog.Category.OUTLET
        0x301L -> DeviceCatalog.Category.THERMOSTAT
        0xaL -> DeviceCatalog.Category.LOCK
        0x2bL -> DeviceCatalog.Category.FAN
        0x202L -> DeviceCatalog.Category.WINDOW_COVERING
        else -> DeviceCatalog.Category.UNSPECIFIED
    }
    data class Entry(val vendor: Int,val product: Int,val brand: String,val name: String,val part: String,val type: Long)
    class Index private constructor(val height: String,private val entries: Map<Long,Entry>) {
        fun lookup(vendor: Int, product: Int): DeviceCatalog.Match? {
            if (vendor !in 1..65535 || product !in 0..65535) return null
            val row=entries[(vendor.toLong() shl 16) or product.toLong()] ?: return null
            val title=if(row.name.startsWith(row.brand,true)) row.name else "${row.brand} ${row.name}"
            val productInfo=DeviceCatalog.Product("matter:${row.vendor}:${row.product}",row.brand,title,category(row.type),
                setOf(row.name,row.part).filter {it.isNotEmpty()}.map(DeviceCatalog::key).toSet(), titleSource = DeviceCatalog.TitleSource.CATALOGUE)
            return DeviceCatalog.Match(productInfo,"Matter DCL · snapshot $height","VID=${row.vendor}; PID=${row.product}")
        }
        companion object {
            val EMPTY=Index("",emptyMap())
            fun read(input: InputStream): Index {
                val bytes=input.readBytesLimited(2*1024*1024)
                val lines=bytes.toString(Charsets.UTF_8).split('\n')
                require(lines.size in 2..20002 && lines[0].matches(Regex("DCL1\\t[0-9]{1,20}")))
                val rows=LinkedHashMap<Long,Entry>()
                for(line in lines.drop(1).filter {it.isNotEmpty()}) {
                    val p=line.split('\t');require(p.size==6)
                    val v=p[0].toInt();val id=p[1].toInt();val type=p[5].toLong()
                    require(v in 1 until 0xfff0 && id in 0..65535 && type in 0..0xffffffffL)
                    require(p.slice(2..4).all {it.length<=160 && it.all {c->c.code>=32 && c.code!=127}} && p[2].isNotBlank() && p[3].isNotBlank())
                    require(rows.put((v.toLong() shl 16) or id.toLong(),Entry(v,id,p[2],p[3],p[4],type))==null)
                }
                return Index(lines[0].substringAfter('\t'),rows)
            }
            private fun InputStream.readBytesLimited(limit:Int):ByteArray {
                val out=java.io.ByteArrayOutputStream();val b=ByteArray(4096)
                while(true) {val n=read(b);if(n<0)break;require(out.size()+n<=limit);out.write(b,0,n)}
                return out.toByteArray()
            }
        }
    }
    @Volatile var index=Index.EMPTY
}
