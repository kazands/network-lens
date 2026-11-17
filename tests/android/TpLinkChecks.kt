package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*
import java.net.InetAddress

/** Synthetic callbacks only, on the emulator enforced by the containing identity suite. */
internal object TpLinkChecks {
    @Suppress("UNCHECKED_CAST")
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        fun set(owner: Any, key: String, value: Any?) = owner.javaClass.getDeclaredField(key).let { it.isAccessible = true; it.set(owner,value) }
        val fake = Discovery(test.targetContext, NetworkState().apply {
            ip = "198.18.0.1"; prefix = 24; key = "synthetic-tplink"; prefixes[ip] = prefix; addresses.add(ip)
        }).also { set(it,"accepting",true); set(it,"enriching",true); set(it,"range",IpRange("198.18.0.1",25)) }
        val selfOnly = Discovery(test.targetContext, NetworkState().apply {
            ip = "198.18.0.1"; prefix = 24; key = "synthetic-tplink-self"; prefixes[ip] = prefix; addresses.add(ip)
        }).also {set(it,"accepting",true);set(it,"range",IpRange("198.18.0.1",32))}
        try {
            Discovery::class.java.getDeclaredMethod("startTpLink").apply {isAccessible=true}.invoke(selfOnly)
            check(selfOnly.sources()[TpLinkDiscovery.SOURCE]?.startsWith("Not used") == true,
                "Self-only scan skips TP-Link discovery without leaving pending work")
        } finally {selfOnly.cancel()}
        val receive = Discovery::class.java.getDeclaredMethod("tpLinkResolved",InetAddress::class.java,Int::class.javaPrimitiveType,TpLinkDiscovery.Response::class.java).apply {isAccessible=true}
        fun response(model: String) = TpLinkDiscovery.Response(model,"IOT.SMARTPLUGSWITCH","Fixture accessory","1.0","1.0","")
        try {
            receive.invoke(fake,InetAddress.getByName("198.18.0.20"),9999,response("HS103(US)"))
            val plug=fake.snapshot().single()
            check(plug.seen && plug.presentation().title=="TP-Link Kasa HS103" && plug.presentation().subtitle=="Smart plug · Fixture accessory","Current plug response supplies presence and clean identity")
            check(plug.model=="HS103(US)" && plug.manufacturer.isEmpty() && plug.mac.isEmpty(),"Raw model retained; absent manufacturer and MAC stay unknown")
            check(DeviceIcon.forGroup(plug).drawable==R.drawable.ic_device_plug,"Smart plug uses plug icon")
            check(plug.ports.isEmpty() && plug.services.contains(TpLinkDiscovery.SOURCE),"UDP reply does not claim a TCP listener")
            check(plug.observations().any {it.source==TpLinkDiscovery.MODEL_SOURCE} && plug.copy().identifiedProduct()==plug.identifiedProduct(),"Recognition source and progress copies preserved")
            receive.invoke(fake,InetAddress.getByName("198.18.0.20"),20002,response("HS103"))
            check(fake.snapshot().size==1 && fake.snapshot().single().identifiedProduct()!=null,"Legacy and modern replies merge without duplicate rows or alias conflicts")
            receive.invoke(fake,InetAddress.getByName("198.18.0.21"),9999,response("HS220(US)"))
            val dimmer=fake.snapshot().single {it.ip.endsWith(".21")}
            check(dimmer.presentation().title=="TP-Link Kasa HS220" && dimmer.presentation().subtitle.startsWith("Dimmer switch") && DeviceIcon.forGroup(dimmer).drawable==R.drawable.ic_device_switch,"Dimmer shares the wire family but uses switch classification")
            receive.invoke(fake,InetAddress.getByName("198.18.0.22"),20002,response("X999"))
            check(fake.snapshot().single {it.ip.endsWith(".22")}.identifiedProduct()==null,"Unknown TP-Link model not forced into plug catalogue")
            receive.invoke(fake,InetAddress.getByName("198.18.0.200"),9999,response("HS103"))
            receive.invoke(fake,InetAddress.getByName("198.19.0.20"),9999,response("HS103"))
            check(fake.snapshot().size==3,"Reject narrowed-range and other-network replies")
            val conflict=plug.copy().apply {hint("Model","HS220",TpLinkDiscovery.MODEL_SOURCE,80)}
            check(conflict.identifiedProduct()==null && conflict.classification().conflicted,"Plug versus switch evidence remains explicit conflict")
            check(Device().apply {ip=plug.ip}.identifiedProduct()==null,"Fresh occupant inherits no TP-Link identity")
            fake.cancel()
            receive.invoke(fake,InetAddress.getByName("198.18.0.23"),9999,response("HS103"))
            check(fake.snapshot().size==3,"Late reply after Stop rejected")
        } finally {fake.cancel()}
    }
}
