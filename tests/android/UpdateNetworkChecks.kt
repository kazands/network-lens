package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*
import java.io.File

/** Real reconciliation and Compose actions, with synthetic network/update state and no requests. */
internal object UpdateNetworkChecks {
    fun run(test: Instrumentation,check: (Boolean,String)->Unit) {
        ComposeDriver(test,check).use { d ->
            Store(test.targetContext).clear();d.start();d.configure(411,1f)
            val store=Store(test.targetContext)
            val flow=d.field("updater") as UpdateFlow
            fun field(name: String)=flow.javaClass.getDeclaredField(name).apply {isAccessible=true}
            val setView=flow.javaClass.getDeclaredMethod("set",UpdateFlow.View::class.java).apply {isAccessible=true}
            val reconcile=MainActivity::class.java.getDeclaredMethod("reconcileNetwork",NetworkState::class.java).apply {isAccessible=true}
            val stopWatching=MainActivity::class.java.getDeclaredMethod("stopWatchingConnection").apply {isAccessible=true}
            val release=AppUpdates.Release(AppUpdates.Version(0,99,0),true,"https://github.com/kazands/network-lens/releases/download/v0.99.0/Network-Lens-0.99.0.apk",1024,"a".repeat(64))
            val before=network("synthetic-update-network-a",true)
            val transitions=listOf("Wi-Fi disconnect" to null,
                "Wi-Fi replacement" to network("synthetic-update-network-b",true),
                "IPv6 scope withdrawal" to network(before.key,false))
            val artifact=File.createTempFile("synthetic-update-network-",".bin",test.targetContext.cacheDir)
            fun seedNetwork() {
                store.clear();store.observeConnection(before)
                val peers=listOf(Device().apply {ip="192.0.2.8";seen=true},Device().apply {ip="2001:db8:1::8";seen=true})
                store.retainIfCurrent(store.token(),before.key,peers,true)
                d.set("network",before);d.set("devices",peers);d.set("groupedSource",null)
            }
            fun changeNetwork(after: NetworkState?) {
                reconcile.invoke(d.activity,after);d.render()
            }
            try {
                d.main {stopWatching.invoke(d.activity);d.set("demo",false);field("release").set(flow,release)}
                for((label,after) in transitions) {
                    var dialog: Any?=null;var cancel=0;var generation=0L
                    d.main {
                        seedNetwork();d.set("modal",UiModal.UPDATES);d.set("updatesOpen",true)
                        setView.invoke(flow,UpdateFlow.View(UpdateFlow.Step.DOWNLOADING,percent=35))
                        generation=field("generation").getLong(flow)
                    };d.settle()
                    d.main {dialog=d.root();cancel=d.text("Cancel")!!.id;changeNetwork(after)};d.settle()
                    d.main {
                        check(d.activity.uiState().modal==UiModal.UPDATES && d.field("updatesOpen")==true,"$label preserves the open updater")
                        check(d.root()===dialog && d.text("Cancel")?.id==cancel && d.words().contains("Downloading · 35%"),"$label retains the actual progress dialog and Cancel action")
                        check(flow.view.step==UpdateFlow.Step.DOWNLOADING && field("generation").getLong(flow)==generation,"$label does not cancel or restart the update")
                        check(store.devices(before.key).size==(if(after?.key==before.key) 1 else 0),"$label still retires stale scan endpoints")
                        setView.invoke(flow,UpdateFlow.View(UpdateFlow.Step.DOWNLOADING,percent=71))
                    };d.settle()
                    d.main {
                        check(d.words().contains("Downloading · 71%"),"$label continues to display updater callbacks")
                        artifact.writeBytes(byteArrayOf(1,2,3));field("apk").set(flow,artifact)
                        setView.invoke(flow,UpdateFlow.View(UpdateFlow.Step.READY))
                    };d.settle()
                    d.main {
                        check(d.words().contains("Install update") && d.words().contains("Update verified · 0.99.0"),"$label exposes the completed update without reopening the menu")
                        seedNetwork();changeNetwork(after)
                    };d.settle()
                    d.main {
                        check(d.activity.uiState().modal==UiModal.UPDATES && d.words().contains("Install update"),"$label also preserves a verified update")
                        check(artifact.isFile && field("apk").get(flow)===artifact && flow.release===release && field("generation").getLong(flow)==generation,"$label keeps the downloaded artifact without another check or download")
                    }
                    d.click("Later")
                    d.main {check(d.activity.uiState().modal==null && d.field("updatesOpen")==false,"$label still permits explicit dismissal")}
                }
                d.main {
                    seedNetwork();d.set("modal",UiModal.UPDATES);d.set("updatesOpen",true)
                    field("apk").set(flow,null);setView.invoke(flow,UpdateFlow.View(UpdateFlow.Step.DOWNLOADING,percent=35))
                    changeNetwork(null)
                };d.settle();d.click("Cancel")
                d.main {check(flow.view.step==UpdateFlow.Step.AVAILABLE && d.activity.uiState().modal==null && d.field("updatesOpen")==false,"Cancel after network loss still stops the updater through its normal action")}
                for(modal in listOf(UiModal.SCAN,UiModal.RANGE,UiModal.NETWORK,UiModal.REPORT_OPTIONS,UiModal.REPORT)) {
                    d.main {
                        seedNetwork();d.set("modal",modal)
                        d.set("sharedReport",TroubleshootingReport.Document(listOf("Synthetic obsolete report")))
                        changeNetwork(null)
                    };d.settle()
                    d.main {check(d.activity.uiState().modal==null && d.activity.uiState().document==null && store.devices(before.key).isEmpty(),"Network loss still dismisses $modal and retires its report/results")}
                }
                d.main {check(field("transfer").get(flow)==null,"Network-change regressions never make a live update request")}
            } finally {
                d.main {field("apk").set(flow,null)};artifact.delete();store.clear()
            }
        }
    }
    private fun network(key: String,ipv6: Boolean)=NetworkState().apply {
        this.key=key;ip="192.0.2.1";prefix=24;iface="fixture0"
        addresses.add(ip);prefixes[ip]=prefix
        if(ipv6) {addresses.add("2001:db8:1::1");prefixes["2001:db8:1::1"]=64}
    }
}
