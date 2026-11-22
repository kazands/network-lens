package io.github.kazands.networklens

import android.app.job.JobScheduler
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.*
import android.os.*
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.DateFormat
import java.util.*
import java.util.concurrent.*

/** Owns the foreground scan session. Compose renders snapshots; it never performs discovery. */
class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private var network: NetworkState? = null
    private var wifiName: WifiName? = null
    private var networkChanges: ConnectivityManager.NetworkCallback? = null
    private val networkRefresh = Runnable { refreshConnection() }
    @Volatile private var discovery: Discovery? = null
    private var devices: List<Device> = ArrayList()
    private var displayGroups: List<Device> = ArrayList()
    private var groupedSource: List<Device>? = null
    private val presentation = Executors.newSingleThreadScheduledExecutor()
    private val updateLock = Any()
    private var queuedOperation: Discovery? = null
    private var terminalQueued = false
    private var pendingInput: ScanUpdate? = null
    private var pendingDisplay: ScanUpdate? = null
    private var preparing = false
    private var displayPosted = false
    private var nextSequence = 0L
    private var displaySequence = 0L
    private var lastPrepared = 0L
    private class ScanUpdate(val operation: Discovery, val token: Long, val sequence: Long, val done: Int, val total: Int, val snapshot: () -> List<Device>, val finished: Boolean, val message: String) {
        var prepared: ScanPresentation? = null
        constructor(operation: Discovery, token: Long, sequence: Long, done: Int, total: Int, raw: List<Device>, finished: Boolean, message: String) :
            this(operation, token, sequence, done, total, { raw }, finished, message) { prepared = ScanPresentation.capture(raw) }
    }
    private var selected: Device? = null
    private var showingSettings = false
    private var demo = false
    private var scanning = false
    @Volatile private var destroyed = false
    private var query = ""
    private var range = ""
    private var message = ""
    private var done = 0
    private var total = 0
    private var reportGeneration = 0L
    private var reportPreparing = false
    private val main = Handler(Looper.getMainLooper())

    private var updater: UpdateFlow? = null
    private var updatesOpen = false
    private val revision = mutableIntStateOf(0)
    internal var uiMemory = UiMemory(); private set
    private var modal: UiModal? = null
    private var sharedReport: TroubleshootingReport.Document? = null
    private var sharedToken = 0L
    private var sharedNetwork = ""

    public override fun onCreate(saved: Bundle?) {
        store = Store(this); setTheme(if (store.dark()) R.style.AppTheme_Dark else R.style.AppTheme)
        // No framework or Compose saved state is restored: scan data and choices are RAM-only.
        super.onCreate(null)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        (getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler).cancel(7401)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).let { it.cancelAll(); it.deleteNotificationChannel("network-changes") }
        updater = UpdateFlow(this) { refreshUpdateDialog() }
        val retained = lastCustomNonConfigurationInstance as? ViewState
        demo = retained?.demo ?: intent.getBooleanExtra("demo", false)
        if (retained != null) {
            query = retained.query; range = retained.range; showingSettings = retained.settings
            network = retained.networkState; uiMemory = retained.memory
        }
        wifiName = WifiName(this) { if (!destroyed) render() }
        if (store.ready()) loadNetwork()
        if (retained != null && retained.networkKey == network?.key) selected = retained.selection?.let { Device.currentGroup(devices,it) }
        UiPrivacy.protect(window.decorView)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        setContent { revision.intValue; NetworkLensApp(uiState(), uiMemory, ::handle) }
        render()
        if (store.ready()) presentation.execute { MacVendors.load(applicationContext); DeviceCatalogLoader.load(applicationContext); ui { refreshCataloguePresentation() } }
    }
    private class ViewState(a: MainActivity) {
        val demo = a.demo; val query = a.query; val range = a.range; val settings = a.showingSettings
        val networkKey = a.network?.key; val networkState = a.network; val selection = a.selected?.copy()
        val memory = a.uiMemory
    }
    override fun onRetainCustomNonConfigurationInstance(): Any = ViewState(this)
    override fun onSaveInstanceState(state: Bundle) { super.onSaveInstanceState(state); state.clear() }
    override fun onResume() {
        super.onResume(); updater?.resume()
        if (::store.isInitialized && store.ready()) { watchConnection(); wifiName?.start(); loadNetwork(); render() }
        if (updatesOpen) modal = UiModal.UPDATES
    }
    override fun onConfigurationChanged(config: Configuration) { super.onConfigurationChanged(config); render() }
    override fun onPause() { super.onPause(); leaveForeground() }
    override fun onStop() { super.onStop(); leaveForeground() }
    private fun leaveForeground() {
        updater?.pause(); invalidateTroubleshooting(false)
        stopWatchingConnection(); wifiName?.stop(); if (modal != UiModal.UPDATES) modal = null
        uiMemory.menuOpen = false; discovery?.cancel(); render()
    }
    override fun onDestroy() {
        destroyed = true; updater?.close(); presentation.shutdownNow()
        synchronized(updateLock) { pendingInput = null; pendingDisplay = null }
        discovery?.cancel(); main.removeCallbacksAndMessages(null); super.onDestroy()
    }
    private fun ui(task: Runnable) { main.post { if (!destroyed) task.run() } }
    private fun render() {
        if (destroyed) return
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window,window.decorView).let { it.isAppearanceLightStatusBars = !store.dark(); it.isAppearanceLightNavigationBars = !store.dark() }
        revision.intValue++
    }
    private fun updateScanButton() = render()
    private fun updateProgress() = render()
    private fun fillDevices() = render()
    private fun resetListPosition() { uiMemory.resetList();uiMemory.resetDetails() }
    private fun backToPreviousPage() { if (showingSettings) showingSettings = false else backToDevices(); render() }
    private fun backToDevices() { selected = null; uiMemory.resetDetails(); render() }
    private fun dismissConnectionDialogs() {
        invalidateTroubleshooting()
        // App updates do not belong to the scan's Wi-Fi session. Keep their
        // progress and Cancel action visible while retiring network-bound UI.
        if (modal != UiModal.UPDATES) modal = null
        uiMemory.menuOpen = false
    }
    private fun openAndroidSettings() { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+packageName))) }
    private fun suspendUpdateDialog() { modal = null }
    private fun refreshUpdateDialog() { if (!destroyed) render() }
    private fun showUpdates(check: Boolean) { updatesOpen = true; modal = UiModal.UPDATES; render(); if (check) updater?.check() }
    private fun invalidateTroubleshooting(clearShared: Boolean = true) {
        reportGeneration++; reportPreparing = false; sharedReport = null
        if (modal == UiModal.REPORT || modal == UiModal.REPORT_OPTIONS) modal = null
        if (clearShared) ReportShareProvider.clear()
    }
    private fun troubleshootingOptions() {
        refreshConnection()
        if (demo || scanning || network?.let { store.report(it.key) } == null) { toast("Finish or stop a real scan first."); return }
        modal = UiModal.REPORT_OPTIONS; render()
    }
    private fun scanDetails() { modal = UiModal.SCAN; render() }
    private fun openSettings() { if (!showingSettings) {uiMemory.settingsOffset=0;uiMemory.settingsVersion++}; showingSettings = true; modal = null; render() }
    private fun confirmClearResults() { modal = UiModal.CLEAR; render() }
    private fun privacy() { modal = UiModal.PRIVACY; render() }
    private fun networkInfo() { modal = UiModal.NETWORK; render() }
    private fun wifiNameAccess() { modal = UiModal.WIFI_NAME; render() }
    private fun scanRange() { modal = UiModal.RANGE; render() }
    private fun loadNetwork() {
        if (demo) { devices = samples(); network = null; return }
        val current = try { NetworkState.current(this) } catch (_: RuntimeException) { null }
        reconcileNetwork(current)
    }
    private fun reconcileNetwork(current: NetworkState?) {
        val prior = network ?: store.connection()
        if (prior != null && prior.sameConnection(current) && !store.belongsToOtherNetwork(current!!.key)) {
            val operation = discovery
            val retained = prior.retainsScope(current) && (operation == null || operation.refreshNetwork(current!!))
            val observed = if (operation == null) store.devices(current!!.key) else devices
            val kept = observed.filter { if (!retained) current!!.contains(it) else !it.self || current!!.owns(it.ip) }
            if (!retained) {
                val report = store.report(prior.key)
                val replacement = ScanReport(operation?.scope() ?: report?.scope ?: "", false,
                    "Wi-Fi IPv4 scope changed. Unaffected responses remain; scan again to refresh.",
                    if (operation == null) report?.checked ?: 0 else done,
                    if (operation == null) report?.total ?: 0 else total,
                    System.currentTimeMillis(), (operation?.sources() ?: report?.sources ?: emptyMap()).mapValues {
                        if (it.value.startsWith("Running")) "Truncated · Wi-Fi IPv4 scope changed" else it.value
                    }, if (operation == null) report?.omittedSources ?: 0 else 0)
                operation?.cancel(); discovery = null; scanning = false; selected = null
                store.replaceScope(current!!.key, kept, replacement)
                message = ""; dismissConnectionDialogs()
            } else store.pruneRetiredSelf(current!!)
            network = current; store.observeConnection(current); devices = kept
            selected?.let { previous ->
                selected = Device.currentGroup(kept, previous)
            }
            return
        }
        val changed = prior != null || store.belongsToOtherNetwork(current?.key ?: "")
        if (changed) {
            discovery?.cancel(); discovery = null; scanning = false; store.clear()
            selected = null; query = ""; range = ""; message = ""; done = 0; total = 0
            resetListPosition(); dismissConnectionDialogs()
        }
        if (prior == null && current != null) message = ""
        network = current; store.observeConnection(current); devices = current?.let { store.devices(it.key) } ?: emptyList()
    }
    private fun watchConnection() {
        if (networkChanges != null) return
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        networkChanges = object : ConnectivityManager.NetworkCallback() {
            private fun changed() { main.removeCallbacks(networkRefresh); main.postDelayed(networkRefresh, 200) }
            override fun onAvailable(n: Network) { changed() }
            override fun onLost(n: Network) { changed() }
            override fun onLinkPropertiesChanged(n: Network, p: android.net.LinkProperties) { changed() }
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) { changed() }
        }
        try { manager.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), networkChanges!!, main) }
        catch (_: RuntimeException) { networkChanges = null }
    }
    private fun stopWatchingConnection() {
        main.removeCallbacks(networkRefresh); val prior = networkChanges; networkChanges = null
        if (prior != null) try { (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(prior) } catch (_: RuntimeException) {}
    }
    private fun refreshConnection() {
        if (destroyed || networkChanges == null || demo) return
        val current = try { NetworkState.current(this) } catch (_: RuntimeException) { null }
        // Android also notifies us about capabilities that do not change this page.
        // Replacing its ScrollView on those callbacks interrupts a drag/fling and text selection.
        val unchanged = network?.sameConfiguration(current) ?: (current == null)
        val token = store.token()
        val wasSelected = selected != null
        reconcileNetwork(current)
        if (!unchanged || !store.isCurrent(token) || wasSelected != (selected != null)) render()
    }
    // Only the first address pass has a fixed denominator. Discovery and identification
    // continue after it, so a full address bar must never suggest the scan is finished.
    private fun checkingAddresses(): Boolean = total > 0 && done < total
    private fun statusText(): String {
        if (scanning) return (if (checkingAddresses()) {
            "Checking addresses · " + (done.coerceAtLeast(0).toLong() * 100 / total) + "%\n" + done.coerceAtLeast(0) + " of " + total + " addresses checked"
        } else "Identifying devices…\nReading names and device details")
        if (demo) return "Sample results · not your network"
        if (network?.ip?.isEmpty() == true) return ScanPlan.IPV4_REQUIRED
        val report = network?.let { store.report(it.key) }
        if (message.isNotEmpty() && (report == null || message != report.reason)) return message
        if (report == null) return if (network == null) "Not connected to Wi-Fi" else "Ready to scan"
        val note = if (report.statusLabel == "Scan stopped") "Results collected before Stop are shown."
            else if (!report.complete) {
                val review = report.sources.values.count { it.startsWith("Failed") || it.startsWith("Truncated") || it.startsWith("Running") }
                if (review > 0) "$review check" + (if (review == 1) "" else "s") + " to review" else report.reason.ifEmpty { "View scan details for checks to review." }
            } else ""
        return report.statusLabel + " · " + scanTime(report.finished) + if (note.isEmpty()) "" else "\n$note"
    }
    private fun scanTime(value: Long): String {
        if (value <= 0) return "time not recorded"
        val now = Calendar.getInstance(); val then = Calendar.getInstance(); then.timeInMillis = value
        return if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) "Today, " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(value)) else time(value)
    }
    internal fun uiState(): UiState {
        val current = network
        val report = current?.let { store.report(it.key) }
        val scope = if (range.isNotEmpty()) range else current?.takeIf { it.ip.isNotEmpty() }?.let {
            try { val r = IpRange(it.ip,it.prefix); IpRange.address(if (it.prefix < 31) r.first-1 else r.first)+"/"+it.prefix } catch (_: IllegalArgumentException) { "" }
        }.orEmpty().ifEmpty { if (current != null) "IPv4 Wi-Fi required" else "" }
        return UiState(store.dark(),store.ready(),demo,scanning,showingSettings,selected,groupsForDisplay(),query,range,
            current,wifiName?.let { if (current == null) "" else it.get(current.network) }.orEmpty(),scope,statusText(),done,total,report,
            if (modal == UiModal.SCAN) discovery?.sources() ?: report?.sources ?: emptyMap() else emptyMap(),
            modal,appVersion(),wifiName?.allowed() == true,
            Build.VERSION.SDK_INT < 37 || checkSelfPermission(LAN_PERMISSION) == PackageManager.PERMISSION_GRANTED,
            updater?.view ?: UpdateFlow.View(),updater?.release,updater?.canContinue() == true,reportPreparing,sharedReport)
    }
    internal fun handle(action: UiAction) {
        when (action) {
            UiAction.Scan -> { val op = discovery; if (scanning && op != null) op.cancel() else beginScan() }
            UiAction.ExitSample -> resetSessionData()
            is UiAction.Select -> { selected = action.device.copy(); uiMemory.resetDetails() }
            is UiAction.Search -> { query = action.value }
            is UiAction.Filter -> { uiMemory.filter = action.value; uiMemory.resetList() }
            UiAction.Back -> { if (modal != null) closeModal() else if (showingSettings || selected != null) backToPreviousPage() else finish() }
            UiAction.Theme -> store.dark(!store.dark())
            UiAction.Settings -> openSettings()
            UiAction.ScanDetails -> scanDetails()
            UiAction.NetworkInfo -> networkInfo()
            UiAction.Privacy -> privacy()
            UiAction.Range -> scanRange()
            UiAction.Clear -> confirmClearResults()
            UiAction.ConfirmClear -> { modal = null; resetSessionData(false) }
            UiAction.AndroidSettings -> openAndroidSettings()
            UiAction.WifiSettings -> startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            UiAction.LocationSettings -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            UiAction.WifiName -> wifiNameAccess()
            UiAction.RequestWifiName -> { modal = null; requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION),WIFI_NAME) }
            UiAction.Updates -> showUpdates(true)
            UiAction.CheckUpdate -> updater?.check()
            UiAction.DownloadUpdate -> updater?.download()
            UiAction.InstallUpdate -> updater?.install()
            UiAction.CloseModal -> closeModal()
            UiAction.ReportOptions -> troubleshootingOptions()
            is UiAction.PrepareReport -> { modal = null; prepareTroubleshooting(action.includeNames) }
            UiAction.CopyRecognition -> copyRecognitionReport()
            UiAction.ShareReport -> shareReport()
            is UiAction.CopyReportPage -> {
                refreshConnection()
                val document = sharedReport
                if (document != null && store.isCurrent(sharedToken) && network?.key == sharedNetwork && !scanning && !demo && action.page in document.pages.indices) {
                    copyText("Troubleshooting report", "Network Lens report · page ${action.page+1}/${document.pages.size}\n"+document.pages[action.page],true); toast("Report page copied.")
                }
            }
            is UiAction.Copy -> { copyText(action.label,action.value,action.sensitive); toast("${action.label} copied.") }
            is UiAction.CopyDevice -> { copyText("Device details",deviceText(action.device),true); toast("Device details copied.") }
            is UiAction.SaveRange -> saveRange(action.value)
            UiAction.FinishCleanup -> { if (store.migrate()) recreate() }
        }
        render()
    }
    private fun closeModal() {
        if (modal == UiModal.UPDATES) { updatesOpen = false; updater?.dismiss() }
        if (modal == UiModal.REPORT) sharedReport = null
        modal = null
    }
    internal fun rangeError(value: String): String? {
        try {
            if (value.isNotEmpty()) {
                val current = network ?: throw IllegalArgumentException("Connect to an IPv4 Wi-Fi network before choosing a custom range.")
                if (current.ip.isEmpty()) throw IllegalArgumentException("Connect to an IPv4 Wi-Fi network before choosing a custom range.")
                val parts = value.javaSplit("/",-1)
                if (parts.size != 2 || !parts[1].matches(Regex("[0-9]{1,2}"))) throw IllegalArgumentException("Enter an IPv4 address and prefix, such as 192.168.1.0/24.")
                val chosen = IpRange(parts[0],parts[1].toInt()); val local = IpRange(current.ip,current.prefix)
                if (chosen.first < local.first || chosen.last > local.last) throw IllegalArgumentException("Choose a range inside your current Wi-Fi network.")
                if (chosen.size() > 1024) throw IllegalArgumentException("Choose a smaller range, with no more than 1,024 addresses.")
            }
        } catch (e: IllegalArgumentException) { return e.message ?: "Check the IPv4 range." }
        return null
    }
    private fun saveRange(value: String) {
        val clean = value.javaTrim()
        val error = rangeError(clean)
        if (error != null) { uiMemory.rangeError = error; return }
        range = clean; message = if (clean.isEmpty()) "Next scan: the current Wi-Fi address range." else "Next scan: $clean"
        uiMemory.rangeError = ""; modal = null
    }
    private fun shareReport() {
        refreshConnection(); val document = sharedReport ?: return
        if (!store.isCurrent(sharedToken) || network?.key != sharedNetwork || scanning || demo) return
        val uri = ReportShareProvider.publish(packageName,document)
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_STREAM,uri); clipData = ClipData.newRawUri("Troubleshooting report",uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        try { startActivity(Intent.createChooser(intent,"Share troubleshooting report")) }
        catch (_: ActivityNotFoundException) { ReportShareProvider.clear(); toast("No sharing app is available. You can copy individual pages.") }
    }
    private fun appVersion(): String = try { packageManager.getPackageInfo(packageName,0).versionName ?: "" } catch (_: PackageManager.NameNotFoundException) { "" }
    private fun reported(value: String?): String = if (value.isNullOrEmpty()) "Not reported" else value
    private fun time(value: Long): String = if (value <= 0) "Not recorded" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(value))
    private fun toast(value: String) { uiMemory.notice=value }
    private fun prepareTroubleshooting(includeNames: Boolean) {
        refreshConnection()
        if (reportPreparing || demo || scanning) return
        val current = network ?: return
        val report = store.report(current.key) ?: return
        val token = store.token(); val generation = ++reportGeneration
        val raw = store.devices(current.key)
        val info = try { packageManager.getPackageInfo(packageName, 0) } catch (_: PackageManager.NameNotFoundException) { null }
        val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() } ?: 0
        reportPreparing = true
        toast("Preparing the report for every device…")
        presentation.execute {
            try {
                val groups = Device.grouped(raw)
                val state = when { report.statusLabel == "Scan stopped" -> RecognitionReport.ScanState.STOPPED; report.complete -> RecognitionReport.ScanState.FINISHED; else -> RecognitionReport.ScanState.NOTES }
                val others = groups.filterNot { it.endpoints().any { endpoint -> endpoint.self } }
                val summary = RecognitionReport.text(others.map { it.recognitionReportGroup() }, groups.size - others.size,
                    state, report.sources, report.omittedSources, info?.versionName.orEmpty(), code, Build.VERSION.SDK_INT)
                val context = listOf(
                    TroubleshootingReport.Fact("Scan status", state.label, publicValue = true),
                    TroubleshootingReport.Fact("IPv4 addresses checked", "${report.checked} / ${report.total}", publicValue = true),
                    TroubleshootingReport.Fact("Scan scope", report.scope),
                    TroubleshootingReport.Fact("Scan explanation", report.reason, publicValue = true),
                    TroubleshootingReport.Fact("Method records omitted", report.omittedSources.toString(), publicValue = true)
                ) + report.sources.map { (key, value) -> TroubleshootingReport.Fact("Discovery method", value, key, publicValue = true) }
                val document = TroubleshootingReport.create(summary, groups.map { it.troubleshootingGroup(report.finished) }, context, includeNames)
                main.post {
                    if (destroyed || generation != reportGeneration || !store.isCurrent(token) || scanning || demo || network?.key != current.key) return@post
                    reportPreparing = false
                    sharedReport = document; sharedToken = token; sharedNetwork = current.key
                    modal = UiModal.REPORT; render()
                }
            } catch (_: Exception) {
                main.post { if (generation == reportGeneration) { reportPreparing = false; toast("The report could not be prepared. No partial report was shared.") } }
            }
        }
    }
    private fun copyRecognitionReport() {
        // Reconcile attachment changes before reading the current session; never export a retired scan.
        refreshConnection()
        if (demo || scanning) { toast("Finish or stop a real scan before copying its report."); return }
        val current = network ?: run { toast("Run a scan to create a recognition report."); return }
        val report = store.report(current.key) ?: run { toast("Run a scan to create a recognition report."); return }
        val groups = Device.grouped(store.devices(current.key))
        val self = groups.count { group -> group.endpoints().any { it.self } }
        val facts = groups.filterNot { group -> group.endpoints().any { it.self } }.map { it.recognitionReportGroup() }
        val info = try { packageManager.getPackageInfo(packageName, 0) } catch (_: PackageManager.NameNotFoundException) { null }
        val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() } ?: 0
        val state = when {
            report.statusLabel == "Scan stopped" -> RecognitionReport.ScanState.STOPPED
            report.complete -> RecognitionReport.ScanState.FINISHED
            else -> RecognitionReport.ScanState.NOTES
        }
        val text = RecognitionReport.text(facts, self, state, report.sources, report.omittedSources,
            info?.versionName.orEmpty(), code, Build.VERSION.SDK_INT)
        copyText("Recognition report", text, sensitive = false)
        toast("Recognition report copied. Paste it into your support conversation.")
    }
    private fun groupsForDisplay(): List<Device> {
        if (groupedSource !== devices) { displayGroups = Device.grouped(devices); groupedSource = devices }
        return displayGroups
    }
    private fun refreshCataloguePresentation() {
        // The catalogue is not Compose state. Give lazy rows and cached filters fresh
        // owned snapshots when loading finishes, while retaining their keys/scroll.
        groupedSource=null
        selected=selected?.let {Device.currentGroup(devices,it)}
        render()
    }
    private fun copyText(label: String, value: String, sensitive: Boolean) {
        val clip = ClipData.newPlainText(label, value)
        if (sensitive) clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
    }
    private fun deviceText(group: Device): String {
        val text = StringBuilder(group.presentation().title)
        val classification = group.classification()
        if (!group.self && !group.gateway && group.groupProduct() == null) text.append("\nIdentification: ").append(group.recognitionSummary())
        text.append("\nPrimary device type: ").append(if (classification.conflicted) "Conflicting reports" else classification.category?.label ?: "Not identified")
        if (group.capabilities().isNotEmpty()) text.append("\nServices available: ").append(group.capabilities().joinToString(", "))
        for (d in group.endpoints()) {
            text.append("\n\nIP: ").append(d.ip).append("\nMAC: ").append(d.mac.ifEmpty { "Not available" }).append("\nMAC source: ").append(reported(d.macSource))
                .append("\nNetwork name: ").append(reported(d.name)).append("\nHostname: ").append(reported(d.hostname)).append("\nManufacturer: ").append(reported(d.manufacturer)).append("\nModel: ").append(reported(d.model))
            val echo = d.details["ICMPv6 response time"] ?: d.details["ICMP response time"]
            if (!echo.isNullOrEmpty()) text.append("\nResponse time (").append(if (d.details.containsKey("ICMPv6 response time")) "ICMPv6" else "ICMP").append("): ").append(echo)
            else if (d.latency >= 0) text.append("\nResponse time (TCP): ").append(d.latency).append(" ms")
            for ((label, value) in d.networkNameRows()) text.append("\n").append(label).append(": ").append(value)
            val vendor = MacVendors.lookup(d.mac)
            if (d.kind.isNotEmpty()) text.append("\nDevice type: ").append(d.kind)
            d.identifiedProduct()?.let { match ->
                text.append("\nLocally recognized").append(if (match.product.familyOnly) " family: " else ": ").append(match.product.title)
                if (match.product.category != DeviceCatalog.Category.UNSPECIFIED) text.append("\nRecognized device type: ").append(match.product.category.label)
                text.append("\nRecognition source: ").append(match.source).append("\nRecognition input: ").append(match.input)
            }
            if (d.audioStatus.isNotEmpty()) text.append("\nAudio lookup: ").append(d.audioStatus)
            for ((label, value) in d.audioRows) text.append("\n").append(label).append(": ").append(value)
            for ((source, result) in d.metadataLookupRows()) text.append("\n").append(source).append(": ").append(result)
            for ((source, result) in d.publicIdentityRows()) text.append("\n").append(source).append(": ").append(result)
            for (hint in d.observations()) text.append("\nReported ").append(hint.field).append(" · ").append(hint.source).append(": ").append(hint.value)
            for (fact in d.classification().facts) text.append("\nDevice type evidence · ").append(fact.source).append(": ").append(fact.category.label)
            d.details["UPnP description status"]?.let { text.append("\nUPnP description status: ").append(it) }
            d.details[AppleDeviceInfo.SOURCE]?.let { text.append("\n").append(AppleDeviceInfo.SOURCE).append(": ").append(it) }
            d.details["HomeKit setup (advertised)"]?.let { text.append("\nHomeKit setup (advertised): ").append(it) }
            if (vendor.isNotEmpty()) text.append("\nMAC vendor (IEEE registry): ").append(vendor)
            d.details["Network identifier (DNS)"]?.let { text.append("\nNetwork identifier (DNS): ").append(it) }
            d.details["ONVIF hardware (advertised)"]?.let { text.append("\nReported hardware (ONVIF): ").append(it) }
            text.append("\nServices: ").append(join(d.services)).append("\nObserved / advertised ports: ").append(d.ports)
                .append("\nFound by: ").append(join(d.evidence)).append("\nLast response: ").append(time(d.lastSeen))
        }
        return text.toString()
    }
    private fun beginScan() {
        if (!store.ready()) { render(); return }
        if (demo) { toast("Exit the sample to scan your Wi-Fi."); return }
        if (scanning) return
        if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission(LAN_PERMISSION) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(LAN_PERMISSION), LOCAL_NETWORK); return }
        try { network = NetworkState.current(this) } catch (e: Exception) { message = e.message ?: ""; render(); return }
        if (network?.ip?.isEmpty() == true) { message = ScanPlan.IPV4_REQUIRED; render(); return }
        startDiscovery()
    }
    private fun startDiscovery() {
        val chosen = network ?: return
        invalidateTroubleshooting()
        resetListPosition(); store.clear()
        val token = store.token(); val operation = Discovery(this, chosen); discovery = operation
        scanning = true; selected = null; done = 0; total = 0; devices = ArrayList(); message = ""; render()
        operation.scan(range, object : Discovery.Listener {
            override fun progress(n: Int, size: Int, found: List<Device>) { offerScan(operation, token, n, size, found, false, "") }
            override fun progressSnapshot(n: Int, size: Int, devices: () -> List<Device>) { offerScanSnapshot(operation, token, n, size, devices, false, "") }
            override fun finished(found: List<Device>, complete: Boolean, summary: String) { offerScan(operation, token, done, total, found, true, summary) }
        })
    }
    /** One latest input, one grouped result and one main-thread delivery, independent of callback rate. */
    private fun offerScan(operation: Discovery, token: Long, n: Int, size: Int, found: List<Device>, finished: Boolean, summary: String) {
        offerScanSnapshot(operation, token, n, size, { found }, finished, summary)
    }
    private fun offerScanSnapshot(operation: Discovery, token: Long, n: Int, size: Int, snapshot: () -> List<Device>, finished: Boolean, summary: String) {
        synchronized(updateLock) {
            if (destroyed || presentation.isShutdown || discovery !== operation || !store.isCurrent(token)) return
            if (queuedOperation !== operation) { queuedOperation = operation; terminalQueued = false }
            if (terminalQueued) return
            if (finished) terminalQueued = true
            pendingInput = ScanUpdate(operation, token, ++nextSequence, n, size, snapshot, finished, summary)
            if (!preparing) { preparing = true; presentation.schedule({ prepareScan() }, maxOf(0, 250 - (SystemClock.uptimeMillis() - lastPrepared)), TimeUnit.MILLISECONDS) }
        }
    }
    private fun prepareScan() {
        val next = synchronized(updateLock) { val value = pendingInput; pendingInput = null; lastPrepared = SystemClock.uptimeMillis(); value }
        if (next != null && discovery === next.operation && store.isCurrent(next.token)) {
            next.prepared = ScanPresentation.capture(next.snapshot())
            synchronized(updateLock) {
                if (discovery === next.operation && store.isCurrent(next.token)) pendingDisplay = next
                if (!displayPosted) { displayPosted = true; main.post { displayScan() } }
            }
        }
        synchronized(updateLock) {
            if (pendingInput != null && !presentation.isShutdown) presentation.schedule({ prepareScan() }, 250, TimeUnit.MILLISECONDS) else preparing = false
        }
    }
    private fun displayScan() {
        val next = synchronized(updateLock) { val value = pendingDisplay; pendingDisplay = null; displayPosted = false; value } ?: return
        if (destroyed || discovery !== next.operation || !store.isCurrent(next.token) || next.sequence <= displaySequence) return
        val current = network ?: return
        val prepared = next.prepared ?: return
        val raw = prepared.devices()
        val admitted = raw.filter(current::contains)
        displaySequence = next.sequence; devices = admitted
        displayGroups = if (admitted.size == raw.size) prepared.groups() else Device.grouped(admitted)
        groupedSource = devices
        selected = selected?.let { Device.currentGroup(admitted, it) }
        if (next.finished) {
            store.report(current.key)?.let { report -> store.retainIfCurrent(next.token, current.key, admitted, report) }
            scanning = false; discovery = null; message = next.message
            render(); return
        }
        done = next.done; total = next.total
        render()
    }
    override fun onRequestPermissionsResult(request: Int, permissions: Array<String>, grants: IntArray) {
        super.onRequestPermissionsResult(request, permissions, grants)
        if (request == LOCAL_NETWORK) {
            if (grants.isNotEmpty() && grants[0] == PackageManager.PERMISSION_GRANTED) beginScan()
            else { message = "Local-network access is needed to find devices. You can allow it in Android app settings."; render() }
        } else if (request == WIFI_NAME) {
            wifiName!!.stop(); wifiName!!.start()
            message = if (wifiName!!.allowed()) "" else "Wi-Fi name hidden. You can still scan your network."; render()
        }
    }
    private fun resetSessionData() { resetSessionData(true) }
    private fun resetSessionData(forgetRange: Boolean) {
        invalidateTroubleshooting()
        resetListPosition(); store.clear(); discovery?.cancel(); discovery = null; scanning = false; devices = ArrayList(); selected = null; demo = false
        query = ""; message = ""; if (forgetRange) range = ""
        loadNetwork(); render()
    }
    private fun samples(): MutableList<Device> {
        val out = ArrayList<Device>()
        val names = arrayOf("Home router", "This Android phone", "Living-room TV", "Office printer", "Work computer")
        val ips = arrayOf("192.168.1.1", "192.168.1.12", "192.168.1.24", "192.168.1.35", "192.168.1.51")
        for (n in names.indices) {
            val d = Device(); d.name = names[n]; d.ip = ips[n]; d.self = n == 1; d.gateway = n == 0; d.seen = true
            d.lastSeen = System.currentTimeMillis() - 60000; d.firstSeen = d.lastSeen; d.evidence("Sample data")
            if (n == 2) { d.model = "Sample television"; d.kind = "Cast device"; d.service("_googlecast._tcp."); d.port(8009) }
            if (n == 3) { d.model = "Sample laser printer"; d.kind = "Printer service"; d.service("_ipp._tcp."); d.port(631) }
            if (n == 4) { d.hostname = "WORK-PC"; d.kind = "File-sharing device"; d.hardware("00:11:22:33:44:55", "Sample NetBIOS reply"); d.service("NetBIOS node status") }
            out.add(d)
        }
        return out
    }
    companion object {
        private const val LOCAL_NETWORK = 37
        private const val WIFI_NAME = 38
        private const val LAN_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        @JvmStatic private fun join(values: Collection<*>): String {
            val text = StringBuilder()
            for (value in values) { if (text.isNotEmpty()) text.append('\n'); text.append(value) }
            return text.toString()
        }
    }
}
