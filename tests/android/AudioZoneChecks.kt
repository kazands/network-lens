package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.Intent
import android.content.ClipboardManager
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.kazands.networklens.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic current endpoints; absent Android Network prevents any LAN connection. */
internal object AudioZoneChecks {
    private fun get(o: Any, n: String): Any = o.javaClass.getDeclaredField(n).let { it.isAccessible=true; it.get(o)!! }
    private fun set(o: Any, n: String, v: Any?) = o.javaClass.getDeclaredField(n).let { it.isAccessible=true; it.set(o,v) }
    private fun texts(v: View): List<TextView> = buildList { if(v is TextView)add(v); if(v is ViewGroup)repeat(v.childCount){addAll(texts(v.getChildAt(it)))} }
    fun run(test: Instrumentation, check: (Boolean,String)->Unit) {
        DeviceCatalogLoader.load(test.targetContext)
        val net=NetworkState().apply { ip="198.18.0.1";prefix=24;key="synthetic-audio";prefixes[ip]=prefix;addresses.add(ip) }
        val op=Discovery(test.targetContext,net)
        set(op,"accepting",true);set(op,"enriching",true)
        val found=get(op,"found") as MutableMap<String,Device>;val fresh=get(op,"freshResponders") as MutableSet<String>
        val current=Discovery::class.java.getDeclaredMethod("applyCurrentService",Device::class.java,MdnsPacket.Service::class.java).apply { isAccessible=true }
        val request=Discovery::class.java.getDeclaredMethod("requestAudio",Device::class.java,AudioZones.Kind::class.java,String::class.java).apply { isAccessible=true }
        val apply=Discovery::class.java.getDeclaredMethod("applyCurrentAudio",Device::class.java,AudioZones.Report::class.java).apply { isAccessible=true }
        val work=get(op,"enrichment") as DiscoveryWork;val gate=CountDownLatch(1);val started=CountDownLatch(2)
        repeat(2){work.submit(DiscoveryWork.Lane.METADATA){started.countDown();try{gate.await()}catch(_:InterruptedException){Thread.currentThread().interrupt()}}}
        check(started.await(3,TimeUnit.SECONDS),"Audio shares bounded metadata worker lane")
        fun peer(n:Int,live:Boolean=true)=Device().apply{ip="198.18.0.$n";found[ip]=this;if(live)fresh.add(ip)}
        fun service()=MdnsPacket.Service("Synthetic music zone","_airplay._tcp.").apply {port=7000;attributes["manufacturer"]="VSSL".toByteArray();attributes["model"]="A6x".toByteArray()}
        val r=AudioZones.Report(AudioZones.Kind.VSSL,AudioZones.token("synthetic chassis"),"A.6x",1)
        var fixtures:List<Device> = emptyList()
        try {
            val candidate=peer(2,false);current.invoke(op,candidate,service())
            check(candidate.audioStatus.isEmpty(),"Unconfirmed advertisement cannot query audio")
            val one=peer(3);val two=peer(4)
            current.invoke(op,one,service());current.invoke(op,two,service())
            check(one.audioStatus.endsWith("Waiting or checking") && two.audioStatus.endsWith("Waiting or checking"),"Current VSSL advertisements admit status queries")
            check(apply.invoke(op,one,r)==true && apply.invoke(op,two,r.copy(zone=2))==true,"Direct reports enrich their own zones")
            check(one.mac.isEmpty() && one.links().none { it.contains(r.hardware) },"Chassis key is neither interface MAC nor endpoint grouping link")
            check(one.presentation().title=="VSSL A.6x" && one.presentation().subtitle.contains("Streaming amplifier"),"Amplifier identity overrides playback services while retaining zone name")
            check(Device.grouped(listOf(one,two)).size==2,"Separate audio zone addresses are not collapsed")
            fixtures=op.snapshot().filter{it.audioReport!=null}
            check(fixtures.size==2 && fixtures[0].audioRows.toString().contains(two.ip),"Fresh snapshots resolve same-amplifier relationships")
            fresh.remove(two.ip)
            check(op.snapshot().single { it.ip==one.ip }.audioRows.none { it.second.contains(two.ip) },"Missing current peer disappears from zone mapping")
            fresh.add(two.ip)
            check(!one.json().toString().contains(r.hardware),"Internal chassis key excluded from exported details")
            check(fixtures[0].copy().audioRows==fixtures[0].audioRows,"Audio mapping survives in-memory copies")
            val replaced=peer(5);current.invoke(op,replaced,service());peer(5)
            check(apply.invoke(op,replaced,r)==false && replaced.audioReport==null,"Late reply cannot enrich replaced endpoint")
            val a=peer(6);val b=peer(7);val aid=AudioZones.token("synthetic player A");val bid=AudioZones.token("synthetic player B")
            val sr=AudioZones.Report(AudioZones.Kind.SONOS,aid,group="synthetic group",coordinator=aid,members=listOf(AudioZones.Member(aid,"Synthetic lounge"),AudioZones.Member(bid,"Synthetic kitchen",aid)))
            request.invoke(op,a,AudioZones.Kind.SONOS,aid);request.invoke(op,b,AudioZones.Kind.SONOS,bid)
            check(apply.invoke(op,a,sr)==true && apply.invoke(op,b,sr.copy(hardware=bid))==true,"Sonos self-matched current reports admitted")
            check(Device.grouped(listOf(a,b)).size==2 && a.links().isEmpty(),"Sonos group never becomes a physical identity link")
            check(op.snapshot().single{it.ip==b.ip}.audioRows.toMap()["Playback role"]=="Bonded player","Bonded player role reaches details")
            request.invoke(op,a,AudioZones.Kind.SONOS,bid)
            check(a.audioReport==null && a.audioStatus.contains("Conflicting"),"Changing root UDN invalidates prior mapping")
            val stopped=peer(8);current.invoke(op,stopped,service());op.cancel()
            check(apply.invoke(op,stopped,r)==false && stopped.audioStatus.contains("Stopped"),"Stop seals queued audio lookups and rejects late reports")
            check(Device().audioReport==null && Device().audioRows.isEmpty(),"New scan devices start without audio mapping")
        } finally {op.cancel();gate.countDown();work.awaitTermination(3,TimeUnit.SECONDS)}
        Store(test.targetContext).clear()
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo",true)) as MainActivity
        val original=Configuration(activity.resources.configuration)
        fun main(action:()->Unit){var fail:Throwable?=null;test.runOnMainSync{try{action()}catch(e:Throwable){fail=e}};fail?.let{throw it}}
        try {
            for(dark in listOf(false,true)) for(scale in listOf(1f,2f)) {
                main {
                    activity.resources.updateConfiguration(Configuration(original).apply{fontScale=scale;screenWidthDp=320},activity.resources.displayMetrics)
                    Store(activity).dark(dark);set(activity,"devices",fixtures);set(activity,"selected",fixtures.first());set(activity,"groupedSource",null)
                    MainActivity::class.java.getDeclaredMethod("render").apply{isAccessible=true}.invoke(activity)
                }
                test.waitForIdleSync()
                main {
                    val all=texts(activity.window.decorView)
                    check(all.any{it.text.toString()=="Audio system"} && all.any{it.text.toString()=="Zone 2 · 198.18.0.4"},"Audio mapping visible in both themes and text scales")
                    all.single{it.text.toString()=="Copy device details"}.performClick()
                    val copied=(activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString()
                    check(copied.contains("VSSL zone status") && copied.contains("Zone 2 · 198.18.0.4") && !copied.contains(r.hardware),"Copied mapping retains source and zones, excludes chassis key")
                }
            }
        } finally {main{activity.resources.updateConfiguration(original,activity.resources.displayMetrics);activity.finish()};Store(test.targetContext).clear()}
    }
}
