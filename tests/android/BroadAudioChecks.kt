package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.Intent
import android.content.ClipboardManager
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.kazands.networklens.*
import io.github.kazands.networklens.AudioPlayers.Protocol
import io.github.kazands.networklens.AudioPlayers.Relation
import io.github.kazands.networklens.AudioPlayers.Group
import io.github.kazands.networklens.AudioPlayers.Player
import io.github.kazands.networklens.AudioPlayers.Current
import io.github.kazands.networklens.AudioPlayers.Result
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** No Android Network is provided; fixtures never contact a household device. */
internal object BroadAudioChecks {
    private fun get(o:Any,n:String):Any=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)!!}
    private fun set(o:Any,n:String,v:Any?)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.set(o,v)}
    private fun texts(v:View):List<TextView> = buildList {if(v is TextView)add(v);if(v is ViewGroup)repeat(v.childCount){addAll(texts(v.getChildAt(it)))}}
    fun run(test:Instrumentation,check:(Boolean,String)->Unit) {
        val net=NetworkState().apply {ip="198.18.0.1";prefix=24;key="synthetic-broad-audio";prefixes[ip]=prefix;addresses.add(ip)}
        val op=Discovery(test.targetContext,net)
        set(op,"accepting",true);set(op,"enriching",true)
        val found=get(op,"found") as MutableMap<String,Device>;val fresh=get(op,"freshResponders") as MutableSet<String>
        val current=Discovery::class.java.getDeclaredMethod("applyCurrentService",Device::class.java,MdnsPacket.Service::class.java).apply{isAccessible=true}
        val cached=Discovery::class.java.getDeclaredMethod("applyService",Device::class.java,MdnsPacket.Service::class.java,String::class.java).apply{isAccessible=true}
        val request=Discovery::class.java.getDeclaredMethod("requestBroadAudio",Device::class.java,BroadAudioInfo.Plan::class.java).apply{isAccessible=true}
        val apply=Discovery::class.java.getDeclaredMethod("applyCurrentBroadAudio",Device::class.java,BroadAudioInfo.Plan::class.java,Result::class.java).apply{isAccessible=true}
        val work=get(op,"enrichment") as DiscoveryWork;val gate=CountDownLatch(1);val started=CountDownLatch(2)
        repeat(2){work.submit(DiscoveryWork.Lane.METADATA){started.countDown();try{gate.await()}catch(_:InterruptedException){Thread.currentThread().interrupt()}}}
        check(started.await(3,TimeUnit.SECONDS),"Broader audio shares bounded metadata workers")
        fun peer(n:Int,live:Boolean=true)=Device().apply{ip="198.18.0.$n";found[ip]=this;if(live)fresh.add(ip)}
        fun service(port:Int=11000)=MdnsPacket.Service("Synthetic room","_musc._tcp.").apply{this.port=port;host="synthetic.local";attributes["id"]="synthetic-player".toByteArray()}
        val plan=BroadAudioInfo.Plan(Protocol.BLUOS,11000)
        fun result(d:Device,port:Int=11000)=Result(Player(Protocol.BLUOS,port,AudioPlayers.addressKey(d.ip,port)!!,model="Synthetic amplifier",maker="Synthetic Audio"),mapOf("Model" to "Synthetic amplifier","Manufacturer" to "Synthetic Audio"))
        var fixture:Device?=null
        try {
            val candidate=peer(2,false);current.invoke(op,candidate,service())
            check(candidate.audioPlayerStatuses.isEmpty(),"Unconfirmed service cannot schedule broader audio")
            val c=peer(3);cached.invoke(op,c,service(),"Android cached")
            check(c.audioPlayerStatuses.isEmpty() && c.audioPlayers.isEmpty(),"Cached service cannot query or assign audio metadata")
            check(c.links().isEmpty() && c.name.isEmpty(),"BluOS player instance and host do not become physical identity")
            val one=peer(4);current.invoke(op,one,service());current.invoke(op,one,service(11010))
            check(one.audioPlayerStatuses.size==2,"Same-IP advertised BluOS ports get distinct lookups")
            current.invoke(op,one,service());check(one.audioPlayerStatuses.size==2,"Duplicate service does not reschedule lookup")
            check(apply.invoke(op,one,plan,result(one))==true,"Current matching audio result accepted")
            val secondPlan=BroadAudioInfo.Plan(Protocol.BLUOS,11010)
            check(apply.invoke(op,one,secondPlan,result(one,11010))==true && one.audioPlayers.size==2,"Same-IP player results retained separately")
            check(one.mac.isEmpty() && one.links().isEmpty(),"Player identity creates neither hardware MAC nor grouping link")
            check(one.copy().audioPlayers==one.audioPlayers && one.copy().audioPlayerStatuses==one.audioPlayerStatuses,"In-memory device copy preserves players and lookup statuses")
            check(op.snapshot().single{it.ip==one.ip}.audioRows.any{it.second.contains("11010")},"Player port visible in scan details")
            val copy=one.copy();copy.audioPlayers.clear();check(one.audioPlayers.size==2,"Copy maps are independent")
            check(!op.snapshot().single{it.ip==one.ip}.json().toString().contains(result(one).player.id),"Internal player key excluded from export")
            val replacement=peer(5);current.invoke(op,replacement,service());peer(5)
            check(apply.invoke(op,replacement,plan,result(replacement))==false,"Late result rejected for replaced endpoint")
            val missing=peer(6);current.invoke(op,missing,service());fresh.remove(missing.ip)
            check(apply.invoke(op,missing,plan,result(missing))==false,"Result cannot revive an endpoint without current presence")
            val outside=Device().apply{ip="198.19.0.2";found[ip]=this;fresh.add(ip)};request.invoke(op,outside,plan)
            check(outside.audioPlayerStatuses.isEmpty(),"Out-of-scope endpoint cannot query audio")
            val self=peer(7).apply{self=true};request.invoke(op,self,plan)
            check(self.audioPlayerStatuses.isEmpty(),"Local scanning device is not probed")
            val capped=peer(8);repeat(10){current.invoke(op,capped,service(11000+it*10))}
            check(capped.audioPlayerStatuses.size==8,"Eight per-endpoint query limit")
            val group="00000000-0000-4000-8000-000000000001"
            fun air(leader:Boolean)=MdnsPacket.Service("Synthetic room","_airplay._tcp.").apply{port=7000;attributes["gid"]=group.toByteArray();attributes["igl"]=(if(leader)"1" else "0").toByteArray()}
            val a=peer(9);val b=peer(10);current.invoke(op,a,air(true));current.invoke(op,b,air(false))
            check(a.audioPlayers.size==1 && a.audioPlayerStatuses.isEmpty(),"Current AirPlay grouping needs no new query")
            check(op.snapshot().single{it.ip==a.ip}.audioRows.any{it.second.contains(b.ip)},"Current AirPlay peer association reaches details")
            check(Device.grouped(listOf(a,b)).size==2 && a.links().isEmpty(),"Shared AirPlay group never collapses physical devices")
            fresh.remove(b.ip)
            check(op.snapshot().single{it.ip==a.ip}.audioRows.none{it.second.contains(b.ip)},"Absent peer removed from audio relationships")
            val cachedAir=peer(11);cached.invoke(op,cachedAir,air(false),"Android cached")
            check(cachedAir.audioPlayers.isEmpty(),"Cached AirPlay group is not current membership evidence")
            val virtual=MdnsPacket.Service("Synthetic Cast group","_googlecast._tcp.").apply{port=32123;attributes["ca"]="32".toByteArray();attributes["id"]=group.toByteArray();attributes["fn"]="Synthetic group".toByteArray()}
            val host=peer(12);current.invoke(op,host,virtual)
            check(host.audioPlayers.size==1 && host.audioPlayerStatuses.isEmpty() && host.name.isEmpty() && host.links().isEmpty(),"Virtual Cast group preserves descriptor without changing host identity or querying receiver")
            fixture=op.snapshot().single{it.ip==one.ip}
            current.invoke(op,a,MdnsPacket.Service("Synthetic room","_airplay._tcp.").apply{port=7000})
            check(a.audioPlayers.isEmpty(),"Current advertisement withdrawing group removes previous group report")
            val stop=peer(13);current.invoke(op,stop,service());op.cancel()
            check(stop.audioPlayerStatuses.values.single().contains("Stopped") && apply.invoke(op,stop,plan,result(stop))==false,"Stop seals broader lookup and rejects late reply")
            check(Device().audioPlayers.isEmpty() && Device().audioPlayerStatuses.isEmpty(),"Fresh scan starts without audio state")
        } finally {op.cancel();gate.countDown();work.awaitTermination(3,TimeUnit.SECONDS)}
        ComposeFixtureViews.runAudio(test,check,listOf(fixture!!),listOf("port 11000","port 11010"),fixture!!.audioPlayers.values.first().id)
    }
}
