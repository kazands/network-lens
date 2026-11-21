package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.kazands.networklens.*

/** Retirement regression: no manual action or dedicated alarm-service transport remains. */
internal object EnvisalinkChecks {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun texts(view: View): List<TextView> = buildList {
        if (view is TextView) add(view)
        if (view is ViewGroup) for (n in 0 until view.childCount) addAll(texts(view.getChildAt(n)))
    }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val process = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", "ro.kernel.qemu"))
        check(process.inputStream.bufferedReader().use { it.readText().trim() } == "1", "Alarm retirement fixtures reject physical devices")
        check(Discovery::class.java.declaredMethods.none { it.name.contains("envisalink", true) }, "Discovery has no dedicated TPI scheduler or reader")
        check(Discovery::class.java.declaredConstructors.none { it.parameterTypes.contains(String::class.java) }, "No per-address alarm-check entry point remains")
        check(EnvisalinkInfo::class.java.declaredMethods.none { it.parameterTypes.any { type -> java.net.Socket::class.java.isAssignableFrom(type) } }, "Legacy evidence vocabulary exposes no socket reader")
        val op = Discovery(test.targetContext, NetworkState().apply {
            ip = "198.18.0.1"; prefix = 24; key = "synthetic-alarm-retirement"; addresses.add(ip); prefixes[ip] = prefix
        })
        try {
            field(op, "accepting").set(op, true)
            val observe = Discovery::class.java.getDeclaredMethod("observe", String::class.java, String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            val peer = observe.invoke(op, "198.18.0.72", "Synthetic current response", true) as Device
            check(peer.seen && peer.identifiedProduct() == null && peer.ports.isEmpty(), "A fresh responder neither supplies an alarm identity nor initiates a TPI check")
            check(op.sources().keys.none { it.contains("Envisalink", true) }, "Ordinary discovery offers no Envisalink lookup")
        } finally { op.cancel() }
        ComposeDriver(test,check).use { driver ->
            driver.start()
            val device=Device().apply {ip="192.0.2.72";seen=true}
            driver.fixture(listOf(device));driver.clickTag("device:${device.ip}")
            driver.reveal("Show technical details");driver.click("Show technical details")
            driver.main {
                check(driver.words().none {it.contains("Check for Envisalink") || it=="Scan and check"},"Compose evidence contains no manual alarm action")
                check(!driver.activity.uiState().scanning,"Opening details cannot start a scan")
            }
        }
        Store(test.targetContext).clear()
    }
}
