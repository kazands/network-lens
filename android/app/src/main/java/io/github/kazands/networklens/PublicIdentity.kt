package io.github.kazands.networklens

/** Public observations stay separate from device-reported hardware fields and grouping. */
internal object PublicIdentity {
    val PUBLIC_FIELDS = setOf("HTTP response status","HTTP body capture","Authentication challenges","TLS capture policy","TLS selected fields")
    enum class Kind(val source: String) { HTTP("Public web information"), TLS("Presented TLS certificate"), SSH("SSH service information"), LLMNR("Direct LLMNR hostname") }
    data class Request(val kind: Kind,val port: Int) {
        init {require(when(kind) {Kind.HTTP->port in setOf(80,443,8080,8443);Kind.TLS->port in setOf(443,8443,8009);Kind.SSH->port==22;Kind.LLMNR->port==5355})}
    }
    enum class State(val label: String) { QUEUED("Waiting or checking"), OBSERVED("Public clues received; no hardware model inferred"),
        NO_RESPONSE("No supported public response"), FAILED("Public information could not be read"), LIMITED("Not attempted: work limit reached"),
        STOPPED("Stopped before lookup finished"), INCOMPLETE("Lookup did not finish"), NOT_NEEDED("Not attempted: stronger identity already available") }
    data class Result(val outcome: String,val fields: Map<String,String> = emptyMap(),val software: PublicHttpInfo.Software? = null,
                      val hostname: String = "")
    data class Entry(val request: Request,val queuedAt: Long,val state: State = State.QUEUED,val startedAt: Long = 0,
                     val queueWaitMs: Long? = null,val budgetAtStartMs: Long? = null,val elapsedMs: Long? = null,
                     val result: Result? = null,val reason: MetadataHttp.Reason? = null) {
        fun timing(): String = listOfNotNull(queueWaitMs?.let {"queue wait ${it}ms"},budgetAtStartMs?.let {"budget at start ${it}ms"},elapsedMs?.let {"request elapsed ${it}ms"}).joinToString("; ")
        fun outcome(): String = result?.outcome ?: reason?.detail ?: state.label
    }
    fun candidates(ports: Collection<Int>,hostnameMissing: Boolean): List<Request> = buildList {
        // Only observed/advertised web, TLS and SSH ports. No additional web-port sweep.
        ports.distinct().filter {it in setOf(80,8080,443,8443)}.sortedBy {listOf(80,8080,443,8443).indexOf(it)}.take(2).forEach {add(Request(Kind.HTTP,it))}
        ports.firstOrNull {it in setOf(443,8443,8009)}?.let {add(Request(Kind.TLS,it))}
        if(22 in ports) add(Request(Kind.SSH,22))
        if(hostnameMissing) add(Request(Kind.LLMNR,5355))
    }
    class Journal {
        private val entries=linkedMapOf<Request,Entry>()
        private var closed=false
        private var omissions=0
        @Synchronized fun queue(request: Request,now: Long=System.nanoTime()): Boolean {
            if(closed || entries.containsKey(request)) return false
            if(entries.size>=8) {omissions++;return false}
            entries[request]=Entry(request,now);return true
        }
        @Synchronized fun begin(request: Request,now: Long,deadline: Long): Boolean {
            val old=entries[request] ?: return false
            if(closed || old.state!=State.QUEUED || old.startedAt!=0L) return false
            entries[request]=old.copy(startedAt=now,queueWaitMs=((now-old.queuedAt)/1_000_000).coerceAtLeast(0),budgetAtStartMs=((deadline-now)/1_000_000).coerceAtLeast(0));return true
        }
        @Synchronized fun finish(request: Request,state: State,result: Result?=null,reason: MetadataHttp.Reason?=null,now: Long=System.nanoTime()): Boolean {
            val old=entries[request] ?: return false
            if(closed || old.state!=State.QUEUED || state==State.QUEUED) return false
            val safe=result?.copy(fields=result.fields.entries.take(12).associate {SafeText.label(it.key,64) to SafeText.label(it.value,256)},hostname=SafeText.label(result.hostname,253))
            entries[request]=old.copy(state=state,result=safe,reason=reason,elapsedMs=if(old.startedAt==0L) null else ((now-old.startedAt)/1_000_000).coerceAtLeast(0));return true
        }
        @Synchronized fun close(stopped: Boolean,now: Long=System.nanoTime()) {if(!closed) {entries.replaceAll {_,e->if(e.state==State.QUEUED) e.copy(state=if(stopped) State.STOPPED else State.INCOMPLETE,elapsedMs=if(e.startedAt==0L) null else ((now-e.startedAt)/1_000_000).coerceAtLeast(0)) else e};closed=true}}
        @Synchronized fun snapshot(): List<Entry> = entries.values.toList()
        @Synchronized fun omitted(): Int = omissions
        @Synchronized fun copy(): Journal = Journal().also {it.entries.putAll(entries);it.closed=closed;it.omissions=omissions}
    }
    /** Shared original per-peer window; late advertisements never extend it. */
    class Plan {
        enum class Offer { START, QUEUED, DUPLICATE, LIMIT, CLOSED }
        private val seen=linkedSetOf<Request>();private val pending=ArrayDeque<Request>()
        private var running=false;private var closed=false;private var deadline=0L
        @Synchronized fun offer(request: Request): Offer {
            if(closed) return Offer.CLOSED
            if(request in seen) return Offer.DUPLICATE
            if(seen.size>=5) return Offer.LIMIT
            seen.add(request);pending.addLast(request)
            if(running) return Offer.QUEUED
            running=true;return Offer.START
        }
        @Synchronized fun next(now: Long,scanDeadline: Long): Request? {
            if(closed) return null
            if(deadline==0L) deadline=minOf(scanDeadline,now+4_000_000_000L)
            if(now>=deadline) {closed=true;pending.clear();return null}
            return pending.removeFirstOrNull().also {if(it==null) running=false}
        }
        @Synchronized fun deadline(): Long = deadline
        @Synchronized fun close() {closed=true;pending.clear()}
    }
}
