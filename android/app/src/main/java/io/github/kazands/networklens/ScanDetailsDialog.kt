package io.github.kazands.networklens

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Collections
import java.util.Date
import java.util.function.Supplier

/** Read-only scan disclosure using the app's existing native surfaces and type. */
class ScanDetailsDialog(context: Context, colors: AppDialog.Colors, private val read: Supplier<Snapshot>) :
    AppDialog(context, colors, "Scan details") {

    class Snapshot(sample: Boolean, running: Boolean, report: ScanReport?, activeScope: String,
        checked: Int, total: Int, message: String, sources: Map<String, String>) {
        @JvmField val sample = sample
        @JvmField val running = !sample && running
        @JvmField val complete = !sample && !running && report != null && report.complete
        @JvmField val scope = when { sample -> ""; running -> activeScope; report == null -> ""; else -> report.scope }
        @JvmField val reason = if (sample || running || report == null) "" else report.reason
        @JvmField val message = if (sample) "" else message
        @JvmField val checked = when { sample -> 0; running -> checked; report == null -> 0; else -> report.checked }
        @JvmField val total = when { sample -> 0; running -> total; report == null -> 0; else -> report.total }
        @JvmField val finished = if (sample || running || report == null) 0L else report.finished
        @JvmField val sources: Map<String, String> =
            if (sample) Collections.emptyMap() else Collections.unmodifiableMap(LinkedHashMap(sources))

        fun same(other: Snapshot?): Boolean = other != null && sample == other.sample &&
            running == other.running && complete == other.complete && checked == other.checked &&
            total == other.total && finished == other.finished && scope == other.scope && reason == other.reason &&
            message == other.message && sources == other.sources
    }

    private val handler = Handler(Looper.getMainLooper())
    private var snapshot: Snapshot? = null
    private lateinit var methods: LinearLayout
    private lateinit var disclosure: TextView
    private var expanded = false
    private val refresh = object : Runnable {
        override fun run() {
            if (!isShowing) return
            update()
            if (snapshot?.running == true) handler.postDelayed(this, 750)
        }
    }

    init { primary("Done") { true } }
    override fun onCreate(state: Bundle?) { super.onCreate(state); update() }
    override fun onStart() { super.onStart(); handler.post(refresh) }
    override fun onStop() { handler.removeCallbacks(refresh); super.onStop() }

    private fun update() {
        val next = read.get()
        if (next.same(snapshot)) return
        snapshot = next
        val y = scroll.scrollY
        body.removeAllViews()
        buildSummary()
        buildMethods()
        space(body, 16)
        body.addView(text(if (next.sample)
            "Sample devices show how Network Lens looks. They do not describe your Wi-Fi."
            else "Silent or isolated devices may be missing. A device that does not reply is not necessarily offline.",
            12, colors.muted, false))
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun buildSummary() {
        val snapshot = checkNotNull(snapshot)
        val saved = snapshot.finished > 0
        val summary = card()
        summary.setPadding(dp(18), dp(18), dp(18), dp(18))
        val status = when {
            snapshot.sample -> "Sample results"
            snapshot.running -> "Scan in progress"
            saved -> if (snapshot.complete) "Complete scan" else "Partial scan"
            else -> "Ready to scan"
        }
        val statusView = text(status, 21, colors.text, true)
        statusView.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        summary.addView(statusView)
        space(summary, 7)
        val explanation = when {
            snapshot.sample -> "An example of a scan, using sample devices."
            snapshot.running -> "Network Lens is checking addresses and listening for device details."
            saved -> if (snapshot.complete) "The planned checks finished. This does not guarantee that every device was found."
                else "Some checks did not finish successfully. Devices or details may be missing."
            else -> "Run Scan Wi-Fi to collect device and scan information."
        }
        summary.addView(text(explanation, 14, colors.muted, false))
        if (saved && !snapshot.sample && !snapshot.running && !snapshot.complete) {
            val attention = snapshot.sources.filterValues(::needsAttention).keys.map { name ->
                val label = friendly(name)
                if (label == "Local service discovery") name.removePrefix("mDNS ") else label
            }.distinct()
            if (attention.isNotEmpty()) {
                val labels = attention.take(2).joinToString(", ") +
                    if (attention.size > 2) " and " + (attention.size - 2) +
                        " other method" + (if (attention.size == 3) "" else "s") else ""
                space(summary, 8)
                summary.addView(text("Needs attention: " + labels, 13, colors.text, true))
            }
        }
        if (saved) {
            space(summary, 10)
            summary.addView(text("Scanned " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(snapshot.finished)), 12, colors.muted, false))
        }
        if (!snapshot.sample && (snapshot.total > 0 || snapshot.scope.isNotEmpty())) {
            space(summary, 16)
            label(summary, "NETWORK COVERAGE")
            if (snapshot.total > 0) {
                space(summary, 7)
                summary.addView(text(snapshot.checked.toString() + " / " + snapshot.total + " addresses checked", 17, colors.accent, true))
                space(summary, 5)
                summary.addView(text("Each check tests an IPv4 address. The number of addresses is not a device count.", 12, colors.muted, false))
            }
            if (snapshot.scope.isNotEmpty()) {
                space(summary, 12)
                val parts = snapshot.scope.javaSplit(" · ", 2)
                val range = text(parts[0], 14, colors.text, false)
                AppTypography.identifier(range, 14)
                range.setTextIsSelectable(true)
                summary.addView(range)
                if (snapshot.scope.contains("IPv6")) {
                    space(summary, 6)
                    summary.addView(text("IPv6 devices are also included when they advertise a service.", 12, colors.muted, false))
                } else if (parts.size > 1) {
                    space(summary, 5)
                    summary.addView(text(parts[1], 12, colors.muted, false))
                }
            }
        }
        body.addView(summary)
        val reason = snapshot.reason.replace(" Tap scan status for details.", "")
        val explainedBySources = reason.startsWith("Device details are partial:") && snapshot.sources.values.any(::needsAttention)
        if (!snapshot.sample && !snapshot.running && !snapshot.complete && reason.isNotEmpty() && !explainedBySources) {
            space(body, 14); body.addView(text(reason, 14, colors.text, false))
        }
        if (!snapshot.sample && snapshot.message.isNotEmpty() && snapshot.message != snapshot.reason) {
            space(body, 12); body.addView(text(snapshot.message, 14, colors.text, false))
        }
        if (saved) {
            space(body, 12)
            body.addView(text("Results reflect responses during this scan. Scan again to refresh them.", 12, colors.muted, false))
        }
    }

    private fun buildMethods() {
        val snapshot = checkNotNull(snapshot)
        if (snapshot.sample) return
        if (snapshot.sources.isEmpty()) {
            space(body, 20)
            val title = text("Discovery methods", 16, colors.text, true)
            heading(title); body.addView(title); space(body, 7)
            body.addView(text(if (snapshot.running) "Discovery methods will appear as the scan starts."
                else "This scan has no method-by-method results. Run a new scan to collect them.", 14, colors.muted, false))
            return
        }
        var failed = 0; var incomplete = 0; var finished = 0; var running = 0; var unused = 0
        for (value in snapshot.sources.values) when {
            value.startsWith("Failed") -> failed++
            value.startsWith("Truncated") -> incomplete++
            value.startsWith("OK") -> finished++
            value.startsWith("Running") -> running++
            else -> unused++
        }
        if (failed + incomplete > 0) {
            space(body, 20)
            val attention = text("What may be missing", 16, colors.text, true)
            heading(attention); body.addView(attention); space(body, 10)
            val issues = card()
            issues.setPadding(dp(15), dp(14), dp(15), dp(2))
            for ((name, value) in snapshot.sources) if (needsAttention(value)) {
                issues.addView(text(friendly(name), 14, colors.text, true))
                space(issues, 5)
                issues.addView(text(outcome(name, value), 13, colors.muted, false))
                space(issues, 13)
            }
            body.addView(issues)
        }
        space(body, 20)
        val title = text("Discovery methods", 16, colors.text, true)
        heading(title); body.addView(title); space(body, 7)
        val counts = finished.toString() + " finished" + (if (failed > 0) " · " + failed + " failed" else "") +
            (if (incomplete > 0) " · " + incomplete + " incomplete" else "") +
            (if (running > 0) " · " + running + " in progress" else "") +
            (if (unused > 0) " · " + unused + " not used" else "")
        body.addView(text(counts, 13, colors.muted, false)); space(body, 7)
        body.addView(text("Different methods help Network Lens find devices, names and model information.", 13, colors.muted, false))
        space(body, 12)
        disclosure = action("", false)
        disclosure.setOnClickListener { expanded = !expanded; applyExpansion() }
        body.addView(disclosure)
        methods = column()
        methods.setPadding(0, dp(12), 0, 0)
        body.addView(methods)
        for ((name, value) in snapshot.sources) {
            val row = card()
            row.setPadding(dp(15), dp(14), dp(15), dp(14))
            row.addView(text(friendly(name), 14, colors.text, true)); space(row, 6)
            row.addView(text(stateName(value), 12, colors.accent, true)); space(row, 6)
            row.addView(text(outcome(name, value), 13, colors.muted, false)); space(row, 10)
            val raw = text(name + "\n" + value, 12, colors.muted, false)
            raw.setTextIsSelectable(true)
            row.addView(raw)
            methods.addView(row); space(methods, 8)
        }
        applyExpansion()
    }

    private fun applyExpansion() {
        methods.visibility = if (expanded) View.VISIBLE else View.GONE
        disclosure.text = if (expanded) "Hide method details" else "Show method details (" + snapshot!!.sources.size + ")"
        disclosure.contentDescription = disclosure.text.toString() + ", " + if (expanded) "expanded" else "collapsed"
        if (Build.VERSION.SDK_INT >= 30) disclosure.stateDescription = if (expanded) "Expanded" else "Collapsed"
    }

    private fun heading(view: TextView) { if (Build.VERSION.SDK_INT >= 28) view.isAccessibilityHeading = true }
    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView =
        AppTypography.text(context, value, size, color, bold)
    private fun action(value: String, filled: Boolean): TextView = text(value, 14, if (filled) colors.surface else colors.accent, true).also {
        it.gravity = Gravity.CENTER; it.minHeight = dp(52); it.setPadding(dp(14), dp(14), dp(14), dp(14))
        it.background = RippleDrawable(ColorStateList.valueOf(0x22336644),
            shape(if (filled) colors.accent else colors.surface, if (filled) colors.accent else colors.border, 14), null)
        it.isFocusable = true
        it.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
            }
        }
    }
    private fun label(parent: LinearLayout, value: String) {
        parent.addView(text(value, AppTypography.LABEL, colors.muted, true))
    }
    private fun column(): LinearLayout = LinearLayout(context).also { it.orientation = LinearLayout.VERTICAL }
    private fun card(): LinearLayout = column().also { it.background = shape(colors.surface, colors.border, 18) }
    private fun shape(fill: Int, border: Int, radius: Int): GradientDrawable = GradientDrawable().also {
        it.setColor(fill); it.cornerRadius = dp(radius).toFloat(); it.setStroke(dp(1), border)
    }
    private fun space(parent: LinearLayout, height: Int) { parent.addView(View(context), LinearLayout.LayoutParams(1, dp(height))) }
    private fun dp(value: Float): Int = Math.round(value * context.resources.displayMetrics.density)
    private fun dp(value: Int): Int = dp(value.toFloat())

    companion object {
        private fun needsAttention(value: String): Boolean = value.startsWith("Failed") || value.startsWith("Truncated")
        private fun stateName(value: String): String = when {
            value.startsWith("OK") -> "Finished"
            value.startsWith("Failed") -> "Failed"
            value.startsWith("Truncated") -> "Incomplete"
            value.startsWith("Running") -> "In progress"
            value.startsWith("Not used") -> "Not used"
            else -> "Recorded"
        }
        private fun friendly(name: String): String = when (name) {
            "IPv4 checks" -> "Network address checks"
            "ICMP echo" -> "Ping response checks"
            "IPv6 service checks" -> "IPv6 and routed accessory checks"
            "Reverse DNS" -> "Device name lookup"
            "Device port and name checks" -> "Extra device details"
            "Service address checks" -> "Advertised address confirmation"
            "Cast device names" -> "TV and speaker names"
            "SADP device discovery" -> "Camera and recorder discovery"
            "SSDP / UPnP" -> "Device advertisements"
            "UPnP descriptions" -> "Device descriptions"
            "Unanswered device checks" -> "Second-pass checks"
            "WS-Discovery" -> "Printer and camera discovery"
            "mDNS type and hostname queries" -> "Advertised services and hostnames"
            else -> if (name.startsWith("mDNS ")) when (name.substring(5)) {
                "_googlecast._tcp." -> "Google Cast devices"
                "_airplay._tcp." -> "AirPlay devices"
                "_raop._tcp." -> "AirPlay audio"
                "_spotify-connect._tcp." -> "Spotify Connect devices"
                "_ipp._tcp." -> "Network printing"
                "_ipps._tcp." -> "Secure network printing"
                "_printer._tcp." -> "Printer advertisements"
                "_pdl-datastream._tcp." -> "Printer data services"
                "_http._tcp." -> "Web services"
                "_https._tcp." -> "Secure web services"
                "_smb._tcp." -> "File sharing"
                "_ssh._tcp." -> "SSH services"
                "_sftp-ssh._tcp." -> "SFTP file transfer"
                "_hap._tcp." -> "HomeKit accessories"
                "_hap._udp." -> "HomeKit over Thread"
                "_matter._tcp." -> "Matter devices"
                "_matterc._udp." -> "Matter setup advertisements"
                "_meshcop._udp." -> "Thread border routers"
                "_androidtvremote2._tcp." -> "Android TV remote services"
                "_companion-link._tcp." -> "Companion services"
                "_device-info._tcp." -> "Device information"
                "_mieleathome._tcp." -> "Miele appliances"
                "_workstation._tcp." -> "Computer services"
                else -> "Local service discovery"
            } else name
        }

        private fun outcome(name: String, value: String): String {
            if (value.startsWith("Failed")) return when {
                name == "ICMP echo" -> "Some ping checks could not run. Other methods still tried to find devices. Open method details for the recorded reason."
                name == "UPnP descriptions" -> "A device answered, but its description could not be read. Some name or model details may be missing."
                name == "IPv4 checks" -> "Some network addresses could not be checked. Devices may be missing from the results."
                value.contains("could not start") -> "Android could not start this method. Devices or details normally found this way may be missing."
                else -> "This method could not collect all of its results. Some devices or identifying details may be missing."
            }
            if (value.startsWith("Truncated")) return "This method stopped before all of its work finished. It may have reached a time or safety limit. Open method details for the recorded reason."
            if (value.startsWith("Running")) return "This method is still checking for devices or collecting their details."
            if (value.startsWith("Not used")) return if (name == "IPv6 service checks")
                "No separate IPv6 echo checks ran. IPv6 replies received by other discovery methods can still appear in the results."
                else "This method was not used in this scan. Its recorded reason appears below."
            if (!value.startsWith("OK")) return "This scan recorded the result shown below for this method."
            return when (name) {
                "IPv4 checks" -> "The planned IPv4 address checks finished. An address can be checked even when no device replies."
                "ICMP echo" -> "Ping requests were checked, with a second attempt for unanswered requests. A device can be present even when it does not answer a ping."
                "IPv6 service checks" -> "Advertised IPv6 addresses were checked for fresh echo replies, with two attempts and extra time for sleeping accessories. Routed addresses require a specific route on this Wi-Fi. Unconfirmed addresses were left out; excluded counts identify addresses outside the supported routes."
                "UPnP descriptions" -> "Description requests finished. Available names and model details were added to the responding devices."
                "Reverse DNS" -> "Name lookups finished using the local name servers provided by your Wi-Fi."
                "Device port and name checks" -> "Optional service-port and computer-name checks finished separately from finding active addresses."
                "Service address checks" -> "Advertised addresses were checked for a fresh response. Silent addresses were not added solely because a service listed them."
                "Cast device names" -> "Unnamed Cast devices were asked for a friendly name. Some devices do not share one; no extra name does not mean they are offline."
                "Unanswered device checks" -> "A second pass checked for replies or details missed on the first attempt."
                else -> "The discovery window finished. Any replies received were used to identify devices; no reply does not mean a device is offline."
            }
        }
    }
}

