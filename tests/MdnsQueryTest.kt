import io.github.kazands.networklens.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** Synthetic traffic-reduction wire fixtures. Queries never become identity or presence. */
object MdnsQueryTest {
    private var checks = 0
    private fun check(value: Boolean, reason: String) { checks++; if (!value) throw AssertionError(reason) }
    private val type = listOf("_http", "_tcp", "local")
    private val instance = listOf("Fixture.with.dot", "_http", "_tcp", "local")
    private val host = listOf("fixture", "local")
    private val peer = InetAddress.getByName("198.18.0.2")
    private fun u16(bytes: ByteArray, offset: Int) = ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset + 1].toInt() and 255)
    private fun name(labels: List<String>): ByteArray = ByteArrayOutputStream().apply {
        for (label in labels) { val bytes=label.toByteArray(StandardCharsets.UTF_8); write(bytes.size); write(bytes) }; write(0)
    }.toByteArray()
    private fun rr(owner: List<String>, kind: Int, data: ByteArray, ttl: Long = 120): ByteArray = ByteArrayOutputStream().apply {
        val out=DataOutputStream(this);out.write(name(owner));out.writeShort(kind);out.writeShort(1);out.writeInt(ttl.toInt());out.writeShort(data.size);out.write(data)
    }.toByteArray()
    private fun response(vararg records: ByteArray): MdnsPacket.Result {
        val bytes=ByteArrayOutputStream();val out=DataOutputStream(bytes)
        out.writeShort(0);out.writeShort(0x8400);out.writeShort(0);out.writeShort(records.size);out.writeInt(0)
        records.forEach(out::write)
        val packet=bytes.toByteArray();return MdnsPacket.parse(packet,packet.size)
    }
    private fun complete(ttl: Long = 120, address: ByteArray = peer.address): MdnsPacket.Result {
        val srv=ByteArrayOutputStream();DataOutputStream(srv).apply { writeInt(0);writeShort(8080);write(name(host)) }
        return response(rr(type,12,name(instance),ttl),rr(instance,33,srv.toByteArray()),rr(instance,16,byteArrayOf(0)),rr(host,1,address))
    }
    @JvmStatic fun main(args: Array<String>) {
        val query=MdnsPacket.query(listOf("_http._tcp.local"))
        val questions=MdnsPacket.multicastQuestions(query,query.size)
        check(questions==listOf(MdnsPacket.Question(type,12)),"Parse only a complete multicast question")
        for (bad in listOf(query.clone().apply { this[0]=1 },query.clone().apply { this[2]=2 },query.clone().apply { this[7]=1 },query.clone().apply { this[this.size-2]=0x80.toByte() },query+byteArrayOf(0),ByteArray(1201))) {
            check(MdnsPacket.multicastQuestions(bad,bad.size).isEmpty(),"Do not coalesce legacy, truncated, answered, QU, trailing or oversized queries")
        }
        check(!MdnsPacket.parse(query,query.size).freshResponse,"A parsed question cannot supply presence")
        val suppression=MdnsQuestionSuppression();val q=questions.single();val now=TimeUnit.SECONDS.toNanos(1)
        check(!suppression.suppress(false,q,now),"No suppression without an observed question")
        suppression.observe(false,questions,now)
        check(suppression.suppress(false,q,now+1),"Identical recent multicast question replaces a scheduled question")
        check(suppression.suppress(false,q.copy(labels=type.map { it.uppercase() }),now+1),"ASCII DNS case folding is consistent")
        check(!suppression.suppress(true,q,now+1),"IPv4 observations never suppress IPv6 questions")
        check(!suppression.suppress(false,q.copy(kind=33),now+1),"Different record types are independent")
        check(!suppression.suppress(false,q,now-1) && !suppression.suppress(false,q,now+TimeUnit.SECONDS.toNanos(1)),"Suppression has a strict one-second lifetime")
        suppression.close();suppression.observe(false,questions,now)
        check(!suppression.suppress(false,q,now),"Closed scan cannot acquire later question state")
        check(!MdnsQuestionSuppression().suppress(false,q,now),"New scans inherit no observed questions")
        val bounded=MdnsQuestionSuppression()
        repeat(256) { bounded.observe(false,listOf(q.copy(labels=listOf("fixture-$it","local"))),now) }
        val extra=q.copy(labels=listOf("fixture-extra","local"));bounded.observe(false,listOf(extra),now)
        check(!bounded.suppress(false,extra,now),"Question churn cannot exceed fixed scan admission")
        val assembly=MdnsAssembly();val names=listOf("_http._tcp.local")
        check(assembly.knownAnswers(false,names,now).isEmpty(),"No cross-scan known answers")
        assembly.accept(peer,complete(),now)
        val known=assembly.knownAnswers(false,names,now)
        check(known.size==1 && known.single().target==instance && known.single().ttl==120L,"Only a complete current same-sender PTR can suppress repeated answers")
        check(assembly.knownAnswers(false,names,now+TimeUnit.MILLISECONDS.toNanos(500)).single().ttl==119L,"Age advertised TTL conservatively instead of substituting local cache lifetime")
        check(assembly.knownAnswers(true,names,now).isEmpty(),"Known answers do not cross address families")
        check(assembly.knownAnswers(false,listOf("_ipp._tcp.local"),now).isEmpty(),"Known answers do not cross question owners")
        val one=MdnsPacket.queryPackets(names,known).single()
        check(one.size<=1200 && u16(one,2)==0 && u16(one,4)==1 && u16(one,6)==1,"Single-packet known-answer query has correct bounded framing")
        check(!MdnsPacket.parse(one,one.size).freshResponse && MdnsPacket.parse(one,one.size).records.isEmpty(),"Query known-answer sections cannot enter the response cache")
        check(MdnsPacket.multicastQuestions(one,one.size).isEmpty(),"Do not coalesce queries whose known-answer lists have not been compared")
        val asResponse=one.clone().apply { this[2]=0x84.toByte() }
        val decoded=MdnsPacket.parse(asResponse,asResponse.size)
        check(decoded.records.single().target==instance && decoded.records.single().ttl==120L,"Known PTR answers retain actual label boundaries and TTL on the wire")
        check(assembly.knownAnswers(false,names,now+TimeUnit.SECONDS.toNanos(10)).isEmpty(),"Expired current-scan assembly does not suppress discovery")
        val short=MdnsAssembly();short.accept(peer,complete(4),now)
        check(short.knownAnswers(false,names,now+TimeUnit.SECONDS.toNanos(1)).single().ttl==3L,"Live shared answers can suppress duplicates")
        check(short.knownAnswers(false,names,now+TimeUnit.SECONDS.toNanos(3)).isEmpty(),"Answers below half their advertised TTL are omitted")
        val goodbye=MdnsAssembly();goodbye.accept(peer,complete(),now);goodbye.accept(peer,response(rr(type,12,name(instance),0)),now+1)
        check(goodbye.knownAnswers(false,names,now+1).isEmpty(),"Goodbye records revoke known-answer suppression")
        val proxy=MdnsAssembly();proxy.accept(peer,complete(address=byteArrayOf(198.toByte(),18,0,3)),now)
        check(proxy.knownAnswers(false,names,now).isEmpty(),"Proxy-only address records cannot suppress the actual endpoint's discovery")
        val ptrOnly=MdnsAssembly();ptrOnly.accept(peer,response(rr(type,12,name(instance))),now)
        check(ptrOnly.knownAnswers(false,names,now).isEmpty(),"Incomplete service data does not suppress further responses")
        val many=(0 until 24).map { known.single().copy(target=listOf("Fixture-$it-"+"x".repeat(40))+type) }
        val packets=MdnsPacket.queryPackets(names,many)
        check(packets.size in 2..4 && packets.all { it.size<=1200 },"Known answers fit a bounded multi-packet sequence")
        check(packets.sumOf { u16(it,6) }==many.size,"No known answer is silently dropped from a fragmented sequence")
        for ((index,packet) in packets.withIndex()) {
            check(u16(packet,2)==if(index==packets.lastIndex)0 else 0x0200,"TC remains set until the complete known-answer sequence ends")
            check(u16(packet,4)==if(index==0)1 else 0,"Only first known-answer fragment repeats questions")
            val copy=packet.clone().apply { this[2]=0x84.toByte() }
            check(MdnsPacket.parse(copy,copy.size).records.size==u16(packet,6),"Every known-answer fragment independently preserves record framing")
        }
        val overflow=MdnsPacket.queryPackets(names,(0 until 64).map { many[0].copy(target=listOf("Fixture-$it-"+"x".repeat(40))+type) })
        check(overflow.size==1 && overflow.single().contentEquals(query),"Oversized complete sequence falls back to ordinary query without dangling TC")
        check(MdnsPacket.queryPackets(names,listOf(known.single().copy(target=emptyList()))).single().contentEquals(query),"Malformed cached data fails back to bounded unsuppressed query")
        assembly.close();check(assembly.knownAnswers(false,names,now).isEmpty(),"Closed assembly exposes no known answers")
        val random=java.util.Random(8305)
        repeat(10000) { val data=ByteArray(random.nextInt(1300));random.nextBytes(data);MdnsPacket.multicastQuestions(data,data.size) }
        check(true,"Malformed observed questions remain bounded and non-authoritative")
        println("PASS · $checks current-scan mDNS question, known-answer and wire-budget checks")
    }
}
