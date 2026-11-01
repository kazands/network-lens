package io.github.kazands.networklens

import android.app.*
import android.app.job.JobScheduler
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import android.provider.Settings
import android.text.*
import android.view.*
import android.widget.*
import java.text.DateFormat
import java.util.*
import java.util.concurrent.*

/** A device list and detail view. Discovery runs only while the user has the app open. */
class MainActivity : Activity() {
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
    private val rowCache = HashMap<String, DeviceRow>()
    private var rowPanel: LinearLayout? = null
    private class ScanUpdate(val operation: Discovery, val token: Long, val sequence: Long, val done: Int, val total: Int, val snapshot: () -> List<Device>, val finished: Boolean, val message: String) {
        var raw: List<Device> = emptyList()
        var groups: List<Device> = emptyList()
        constructor(operation: Discovery, token: Long, sequence: Long, done: Int, total: Int, raw: List<Device>, finished: Boolean, message: String) :
            this(operation, token, sequence, done, total, { raw }, finished, message) { this.raw = raw }
    }
    private var selected: Device? = null
    private val expandedDetails = HashSet<String>()
    private var detailScroll: ScrollView? = null
    private var restoredDetailY = 0
    private var showingSettings = false
    private var settingsScroll: ScrollView? = null
    private var settingsY = 0
    private var demo = false
    private var scanning = false
    @Volatile private var destroyed = false
    private var query = ""
    private var range = ""
    private var message = ""
    private var done = 0
    private var total = 0
    private var bg = 0
    private var card = 0
    private var ink = 0
    private var muted = 0
    private var line = 0
    private var accent = 0
    private var soft = 0
    private lateinit var root: LinearLayout
    private var list: LinearLayout? = null
    private var scanButton: TextView? = null
    private var status: TextView? = null
    private var count: TextView? = null
    private var progress: ProgressBar? = null
    private var scanDetailsDialog: ScanDetailsDialog? = null
    private var optionsDialog: AppDialog? = null
    private var optionsMenu: PopupWindow? = null
    private var deviceScroll: ScrollView? = null
    private val deviceRows = LinkedHashMap<String, View>()
    private var displayedListContext = ""
    private var positionContext = ""
    private var positionIp = ""
    private var positionY = 0
    private var positionOffset = 0
    private var detailBack: android.window.OnBackInvokedCallback? = null
    private val main = Handler(Looper.getMainLooper())

    public override fun onCreate(saved: Bundle?) {
        store = Store(this); setTheme(if (store.dark()) R.style.AppTheme_Dark else R.style.AppTheme)
        super.onCreate(null)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        // Remove jobs and alerts left by older releases.
        (getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler).cancel(7401)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).let { it.cancelAll(); it.deleteNotificationChannel("network-changes") }
        if (!store.ready()) { render(); return }
        demo = intent.getBooleanExtra("demo", false)
        val retained = lastNonConfigurationInstance
        if (retained is ViewState) {
            demo = retained.demo; query = retained.query; range = retained.range
            showingSettings = retained.settings; settingsY = retained.settingsY
            network = retained.networkState
        }
        wifiName = WifiName(this) { if (!destroyed) updateScanButton() }
        loadNetwork()
        if (retained is ViewState) {
            if (retained.context == listContext()) {
                positionContext = retained.context; positionIp = retained.ip; positionY = retained.y; positionOffset = retained.offset
            }
            if (retained.networkKey == network?.key) {
                selected = retained.selection?.let { Device.currentGroup(devices, it) }
                if (selected != null) { restoredDetailY = retained.detailY; expandedDetails.addAll(retained.expanded) }
            } else { range = ""; query = "" }
        }
        render()
        presentation.execute { MacVendors.load(applicationContext); ui { render() } }
    }
    private class ViewState(a: MainActivity) {
        val demo = a.demo
        val query = a.query
        val range = a.range
        val context = a.positionContext
        val ip = a.positionIp
        val y = a.positionY
        val offset = a.positionOffset
        val settings = a.showingSettings
        val settingsY = a.settingsScroll?.scrollY ?: a.settingsY
        val networkKey = a.network?.key
        val networkState = a.network
        val selection = if (a.showingSettings) a.selected?.copy() else null
        val detailY = a.detailScroll?.scrollY ?: a.restoredDetailY
        val expanded = a.expandedDetails.toSet()
    }
    override fun onRetainNonConfigurationInstance(): Any { rememberListPosition(); return ViewState(this) }
    override fun onSaveInstanceState(state: Bundle) { super.onSaveInstanceState(state); state.clear() }
    override fun onResume() {
        super.onResume()
        if (::store.isInitialized && store.ready()) { watchConnection(); wifiName?.start(); loadNetwork(); render() }
    }
    override fun onConfigurationChanged(config: Configuration) { super.onConfigurationChanged(config); render() }
    override fun onPause() { super.onPause(); leaveForeground() }
    override fun onStop() { super.onStop(); leaveForeground() }
    private fun leaveForeground() {
        stopWatchingConnection(); wifiName?.stop(); scanDetailsDialog?.dismiss(); optionsDialog?.dismiss(); optionsMenu?.dismiss(); discovery?.cancel()
    }
    override fun onDestroy() {
        destroyed = true; presentation.shutdownNow()
        synchronized(updateLock) { pendingInput = null; pendingDisplay = null }
        discovery?.cancel(); main.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= 33) detailBack?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        super.onDestroy()
    }
    override fun onBackPressed() { if (showingSettings || selected != null) backToPreviousPage() else super.onBackPressed() }
    private fun ui(task: Runnable) { main.post { if (!destroyed) task.run() } }
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
                    "Wi-Fi IPv6 routes or addresses changed. Unaffected responses remain; scan again to refresh.",
                    if (operation == null) report?.checked ?: 0 else done,
                    if (operation == null) report?.total ?: 0 else total,
                    System.currentTimeMillis(), (operation?.sources() ?: report?.sources ?: emptyMap()).mapValues {
                        if (it.value.startsWith("Running")) "Truncated · Wi-Fi IPv6 routes or addresses changed" else it.value
                    })
                operation?.cancel(); discovery = null; scanning = false; selected = null
                store.replaceScope(current!!.key, kept, replacement)
                message = ""; dismissConnectionDialogs()
            } else store.pruneRetiredSelf(current!!)
            network = current; store.observeConnection(current); devices = kept
            selected?.let { previous ->
                restoredDetailY = detailScroll?.scrollY ?: restoredDetailY
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
    private fun dismissConnectionDialogs() {
        scanDetailsDialog?.dismiss(); optionsDialog?.dismiss(); optionsMenu?.dismiss()
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
        loadNetwork(); render()
    }
    private fun palette() {
        val dark = store.dark()
        bg = Color.parseColor(if (dark) "#101A17" else "#F5F6F1")
        card = Color.parseColor(if (dark) "#1B2923" else "#FFFFFF")
        ink = Color.parseColor(if (dark) "#EEF4EC" else "#172D25")
        muted = Color.parseColor(if (dark) "#A0B4A8" else "#617267")
        line = Color.parseColor(if (dark) "#314139" else "#DFE6DA")
        accent = Color.parseColor(if (dark) "#A5E4BB" else "#0B6B51")
        soft = Color.parseColor(if (dark) "#243B2D" else "#E7EFE2")
    }
    private fun render() {
        settingsScroll?.takeIf { it.isLaidOut }?.let { settingsY = it.scrollY }; settingsScroll = null
        rememberListPosition(); deviceScroll = null; deviceRows.clear(); palette()
        rowCache.clear(); rowPanel = null; list = null; scanButton = null; status = null; count = null; progress = null
        if (Build.VERSION.SDK_INT >= 33) {
            if ((showingSettings || selected != null) && detailBack == null) {
                detailBack = android.window.OnBackInvokedCallback { backToPreviousPage() }
                onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, detailBack!!)
            } else if (!showingSettings && selected == null && detailBack != null) {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(detailBack!!); detailBack = null
            }
        }
        window.statusBarColor = bg; window.navigationBarColor = bg
        window.decorView.systemUiVisibility = if (store.dark()) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root = column()
        root.isSaveEnabled = false; root.isSaveFromParentEnabled = false
        root.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (Build.VERSION.SDK_INT >= 29) root.importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        root.isFocusableInTouchMode = true; root.setBackgroundColor(bg)
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom); insets
        }
        setContentView(root); root.requestApplyInsets()
        val header = row(); header.gravity = Gravity.CENTER_VERTICAL; header.setPadding(dp(22), dp(12), dp(14), dp(8))
        if (showingSettings || selected != null) {
            val back = text("‹", 32, accent, true); back.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 32f); back.gravity = Gravity.CENTER
            back.contentDescription = if (showingSettings && selected != null) "Back to device details" else "Back to devices"
            back.setOnClickListener { backToPreviousPage() }; header.addView(back, lp(48, 48))
        } else {
            val icon = ImageView(this); icon.setImageResource(R.drawable.network_lens_logo)
            icon.tag = "app-brand"; icon.scaleType = ImageView.ScaleType.FIT_CENTER
            icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO; header.addView(icon, lp(56, 56)); spaceHorizontal(header, 10)
        }
        val title = text(if (!store.ready()) getString(R.string.app_name) else if (showingSettings) "Settings" else if (selected == null) getString(R.string.app_name) else "Device details", 18, ink, true)
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val theme = ModeToggle(); theme.contentDescription = if (store.dark()) "Switch to light mode" else "Switch to dark mode"
        theme.setOnClickListener { store.dark(!store.dark()); render() }; header.addView(theme, lp(48, 48))
        val more = text("⋮", 28, accent, true); more.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 28f); more.gravity = Gravity.CENTER
        more.contentDescription = "Options"; more.setOnClickListener { menu(it) }
        if (!showingSettings && store.ready()) header.addView(more, lp(48, 48))
        root.addView(header)
        if (!store.ready()) { cleanupPage(); return }
        if (demo && !showingSettings) {
            val banner = text("Sample devices · Tap to use your Wi-Fi", 12, accent, true); banner.setPadding(dp(23), dp(12), dp(23), dp(12)); banner.setBackgroundColor(soft)
            banner.setOnClickListener { demo = false; selected = null; message = ""; loadNetwork(); render() }; root.addView(banner)
        }
        if (showingSettings) settingsPage() else if (selected != null) detailsPage() else devicesPage()
        scanDetailsDialog?.let { if (it.isShowing) it.resize() }
        optionsDialog?.let { if (it.isShowing) it.resize() }
    }
    private fun backToPreviousPage() {
        if (showingSettings) { showingSettings = false; settingsScroll = null; settingsY = 0; render() }
        else backToDevices()
    }
    private fun cleanupPage() {
        val page = ScrollView(this); val body = column(); body.setPadding(dp(23), dp(24), dp(23), dp(24))
        page.addView(body); root.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(text("Finish removing older app data", 25, ink, true)); space(body, 16)
        body.addView(text("Android could not remove data left by an earlier version. Scanning is paused until cleanup succeeds.", 15, muted, false)); space(body, 16)
        body.addView(text("Try cleanup again. If it still fails, open Android settings, choose Storage & cache, then Clear storage. This also resets Android-managed app permissions.", 14, muted, false)); space(body, 24)
        val retry = button("Retry cleanup", true); retry.setOnClickListener { if (store.migrate()) recreate() else render() }; body.addView(retry)
        space(body, 10)
        val settings = button("Open Android settings", false); settings.setOnClickListener { openAndroidSettings() }; body.addView(settings)
    }
    private fun backToDevices() { selected = null; detailScroll = null; restoredDetailY = 0; expandedDetails.clear(); render() }
    private fun listContext(): String = (if (demo) "sample" else network?.key ?: "disconnected") + "\n" + query
    /** Anchor the selected or first visible row so metadata updates do not move the return target. */
    private fun rememberListPosition() {
        val page = deviceScroll ?: return
        if (!page.isLaidOut) return
        positionContext = displayedListContext; positionY = page.scrollY; positionIp = ""; positionOffset = 0
        if (positionY == 0 && selected == null) return
        var anchor = selected?.let { deviceRows[it.ip] }
        val pageLocation = IntArray(2); val rowLocation = IntArray(2); page.getLocationInWindow(pageLocation)
        if (anchor != null) positionIp = selected!!.ip
        else for ((ip, view) in deviceRows) {
            view.getLocationInWindow(rowLocation)
            if (rowLocation[1] + view.height > pageLocation[1]) { anchor = view; positionIp = ip; break }
        }
        anchor?.let { it.getLocationInWindow(rowLocation); positionOffset = rowLocation[1] - pageLocation[1] }
    }
    private fun restoreListPosition() {
        val page = deviceScroll ?: return
        if (displayedListContext != positionContext) return
        val context = positionContext; val ip = positionIp; val y = positionY; val offset = positionOffset
        page.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (page.viewTreeObserver.isAlive) page.viewTreeObserver.removeOnPreDrawListener(this)
                if (deviceScroll !== page || context != listContext()) return true
                val anchor = deviceRows[ip]; var target = y
                if (anchor != null) {
                    val a = IntArray(2); val b = IntArray(2); anchor.getLocationInWindow(a); page.getLocationInWindow(b)
                    target = page.scrollY + a[1] - b[1] - offset
                }
                page.scrollTo(0, maxOf(0, target)); return true
            }
        })
    }
    private fun resetListPosition() { positionContext = ""; positionIp = ""; positionY = 0; positionOffset = 0; deviceScroll = null; deviceRows.clear() }
    private fun devicesPage() {
        val page = ScrollView(this); deviceScroll = page; displayedListContext = listContext()
        page.isFillViewport = true; page.isVerticalScrollBarEnabled = false
        val content = column(); page.addView(content); root.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = column(); controls.setPadding(dp(23), dp(12), dp(23), dp(8))
        val scan = button("", true, 16); scanButton = scan; scan.maxLines = 2; scan.ellipsize = TextUtils.TruncateAt.END; updateScanButton()
        scan.setOnClickListener { val operation = discovery; if (scanning && operation != null) operation.cancel() else beginScan() }
        controls.addView(scan, LinearLayout.LayoutParams(-1, -2))
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress = bar
        bar.progressTintList = ColorStateList.valueOf(accent); bar.isIndeterminate = scanning && total == 0; bar.max = maxOf(1, total); bar.progress = done
        bar.visibility = if (scanning) View.VISIBLE else View.GONE
        val progressParams = lp(-1, 5); progressParams.topMargin = dp(if (scanning) 8 else 0); controls.addView(bar, progressParams)
        val summary = row(); summary.gravity = Gravity.CENTER_VERTICAL; summary.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        summary.minimumHeight = dp(56); summary.setPadding(dp(2), dp(12), dp(2), dp(12))
        summary.background = RippleDrawable(ColorStateList.valueOf(0x22336644), round(bg, 10), null)
        val statusLabel = text(statusText(), 13, muted, false); status = statusLabel
        statusLabel.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        statusLabel.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO; summary.addView(statusLabel, LinearLayout.LayoutParams(0, -2, 1f))
        val more = text("›", 22, muted, false); more.setPadding(dp(12), 0, dp(5), 0); more.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO; summary.addView(more)
        summary.contentDescription = statusText() + ". View scan details"; summary.setOnClickListener { scanDetails() }; statusLabel.setOnClickListener { scanDetails() }
        controls.addView(summary); content.addView(controls)
        val section = column(); section.setPadding(dp(23), dp(8), dp(23), dp(14))
        val heading = text("Your devices", 26, ink, true); heading.letterSpacing = -.01f
        if (Build.VERSION.SDK_INT >= 28) heading.isAccessibilityHeading = true
        section.addView(heading); space(section, 6); count = text("", 13, muted, false); section.addView(count); space(section, 14)
        val search = EditText(this); search.isSaveEnabled = false
        search.imeOptions = search.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        search.setSingleLine(true); search.textSize = 14f; search.setTextColor(ink); search.setHintTextColor(muted)
        search.hint = "Search devices"; search.contentDescription = "Search devices"; search.setText(query)
        search.setPadding(dp(15), dp(13), dp(15), dp(13)); search.minHeight = dp(48); search.background = border(card, line, 14)
        section.addView(search, LinearLayout.LayoutParams(-1, -2))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, c: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                query = s.toString(); positionContext = ""; positionIp = ""; positionY = 0; positionOffset = 0
                displayedListContext = listContext(); fillDevices(); page.scrollTo(0, 0)
            }
            override fun afterTextChanged(e: Editable) {}
        })
        content.addView(section)
        val rows = column(); list = rows; rows.setPadding(dp(23), 0, dp(23), dp(24)); content.addView(rows)
        fillDevices(); root.requestFocus(); restoreListPosition()
    }
    private fun updateScanButton() {
        val button = scanButton ?: return
        val action = if (scanning) "Stop scan" else "Scan Wi-Fi"
        val ssid = if (demo || network == null) "" else wifiName?.get(network!!.network) ?: ""
        if (ssid.isEmpty()) { button.typeface = AppTypography.face(16, true); button.text = action; button.contentDescription = action; return }
        button.typeface = AppTypography.face(16, false)
        val label = SpannableString(action + "\n" + ssid); val start = action.length + 1
        label.setSpan(android.text.style.TypefaceSpan("sans-serif-medium"), 0, action.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        label.setSpan(android.text.style.RelativeSizeSpan(14f / 16f), start, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        button.text = label; button.contentDescription = action + ", connected network " + ssid
    }
    private fun statusText(): String {
        if (scanning) return (if (total > 0) {
            if (done >= total) discovery?.phase() ?: "Finishing device details…" else "Scanning · " + done + " / " + total + " addresses"
        } else "Listening for devices…") + "\n" + groupsForDisplay().size + " device groups found · View scan details"
        if (demo) return "Sample results · not your network"
        val report = network?.let { store.report(it.key) }
        if (message.isNotEmpty() && (report == null || message != report.reason)) return message
        if (report == null) return if (network == null) "Connect to Wi-Fi to scan for devices." else "Ready when you are · Scan your Wi-Fi"
        return (if (report.complete) "Complete scan" else "Partial scan") + " · " + scanTime(report.finished) + "\nView scan details"
    }
    private fun scanTime(value: Long): String {
        if (value <= 0) return "time not recorded"
        val now = Calendar.getInstance(); val then = Calendar.getInstance(); then.timeInMillis = value
        return if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) "Today, " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(value)) else time(value)
    }
    private fun scanDetails() {
        if (scanDetailsDialog?.isShowing == true) return
        scanDetailsDialog = ScanDetailsDialog(dialogContext(), dialogColors()) {
            val report = network?.let { store.report(it.key) }; val operation = discovery
            val sources = operation?.sources() ?: report?.sources ?: emptyMap()
            ScanDetailsDialog.Snapshot(demo, scanning, report, operation?.scope() ?: "", done, total, message, sources)
        }.also { it.show() }
    }
    private fun groupsForDisplay(): List<Device> {
        if (groupedSource !== devices) { displayGroups = Device.grouped(devices); groupedSource = devices }
        return displayGroups
    }
    private fun fillDevices() {
        val rows = list ?: return
        deviceRows.clear()
        val shown = ArrayList<Device>(); val term = query.lowercase(Locale.ROOT).javaTrim()
        for (group in groupsForDisplay()) for (d in group.endpoints()) {
            val all = (group.title() + " " + d.title() + " " + d.ip + " " + d.mac + " " + d.hostname + " " + d.model + " " + d.manufacturer + " " + MacVendors.lookup(d.mac) + " " + d.services).lowercase(Locale.ROOT)
            if (all.contains(term)) { shown.add(group); break }
        }
        var addresses = 0
        for (d in shown) addresses += d.endpoints().size
        count!!.text = "" + shown.size + " device group" + (if (shown.size == 1) "" else "s") + (if (query.isEmpty()) "" else " matching") + " · " + addresses + " IP" + if (addresses == 1) "" else "s"
        if (shown.isEmpty()) {
            rowCache.clear(); rowPanel = null; rows.removeAllViews()
            val empty = panel(); empty.setPadding(dp(23), dp(29), dp(23), dp(29))
            empty.addView(text(if (scanning) "Looking for devices…" else if (query.isNotEmpty()) "No matching devices" else if (network == null && !demo) "Your Wi-Fi comes first" else "Your devices will appear here", 20, ink, true))
            space(empty, 10)
            empty.addView(text(if (scanning) "Names and details appear as devices respond." else if (query.isNotEmpty()) "Try a name, address, or service." else if (network == null && !demo) "Join your Wi-Fi network, then return and tap Scan Wi-Fi." else "Tap Scan Wi-Fi to discover responding devices. Results stay in memory for this app session.", 14, muted, false))
            if (network == null && !demo) {
                space(empty, 16); val wifi = button("Open Wi-Fi settings", false); wifi.setOnClickListener { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }; empty.addView(wifi)
            }
            rows.addView(empty); return
        }
        if (rowPanel == null) { rows.removeAllViews(); rowPanel = panel(); rows.addView(rowPanel) }
        val keep = HashSet<String>(); for (group in shown) keep.add(group.ip)
        val iterator = rowCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!keep.contains(entry.key)) { rowPanel!!.removeView(entry.value); iterator.remove() }
        }
        for (n in shown.indices) {
            val group = shown[n]
            val row = rowCache[group.ip] ?: DeviceRow().also { rowCache[group.ip] = it }
            row.bind(group, n < shown.size - 1)
            if (rowPanel!!.indexOfChild(row) != n) { rowPanel!!.removeView(row); rowPanel!!.addView(row, n) }
            for (endpoint in group.endpoints()) deviceRows[endpoint.ip] = row
        }
    }
    private inner class DeviceRow : LinearLayout(this@MainActivity) {
        private val icon: ImageView
        private val name: TextView
        private val ipv4: TextView
        private val ipv6: TextView
        private val hardware: TextView
        private val model: TextView
        private val role: TextView
        private var current: Device? = null
        private var drawable = 0
        private var separator = false
        private val separatorPaint = Paint()
        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(17), dp(13), dp(17)); setWillNotDraw(false); isFocusable = true
            icon = ImageView(this@MainActivity); icon.imageTintList = ColorStateList.valueOf(ink); icon.scaleType = ImageView.ScaleType.FIT_CENTER
            icon.setPadding(dp(6), dp(6), dp(6), dp(6)); icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO; addView(icon, lp(40, 40))
            val labels = column(); labels.setPadding(dp(12), 0, dp(7), 0)
            name = text("", 16, ink, true); name.maxLines = 2; labels.addView(name)
            role = text("", AppTypography.METADATA, muted, false); role.setPadding(0, dp(5), 0, 0); labels.addView(role)
            ipv4 = addressLabel(accent); ipv6 = addressLabel(muted); labels.addView(ipv4); labels.addView(ipv6)
            hardware = text("", AppTypography.METADATA, muted, false); hardware.setPadding(0, dp(5), 0, 0); labels.addView(hardware)
            model = text("", AppTypography.METADATA, muted, false); model.setPadding(0, dp(5), 0, 0); labels.addView(model)
            addView(labels, LayoutParams(0, -2, 1f)); addView(text("›", 24, muted, false))
            setOnClickListener { selected = current!!.copy(); detailScroll = null; restoredDetailY = 0; expandedDetails.clear(); render() }
        }
        private fun addressLabel(color: Int): TextView = text("", AppTypography.LIST_ADDRESS, color, false).also {
            AppTypography.identifier(it, AppTypography.LIST_ADDRESS); it.setPadding(0, dp(5), 0, 0)
        }
        private fun value(view: TextView, value: String) {
            if (view.text.toString() != value) view.text = if (view === ipv6) AppTypography.address(value) else value
            view.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
        }
        fun bind(group: Device, lastLine: Boolean) {
            current = group; separator = lastLine
            val visual = DeviceIcon.forGroup(group)
            if (drawable != visual.drawable) { drawable = visual.drawable; icon.setImageResource(drawable) }
            value(name, group.title()); value(role, if (group.self) "This phone" else if (group.gateway) "Router" else "")
            var v4 = ""; var v6 = ""
            for (endpoint in group.endpoints()) {
                val address = Address.bytes(endpoint.ip) ?: continue
                if (address.size == 4) { if (v4.isEmpty()) v4 = endpoint.ip }
                else if (v6.isEmpty() || v6.lowercase(Locale.ROOT).startsWith("fe80:") && !endpoint.ip.lowercase(Locale.ROOT).startsWith("fe80:")) v6 = endpoint.ip
            }
            value(ipv4, if (v4.isEmpty()) "" else "IPv4  " + v4); value(ipv6, if (v6.isEmpty()) "" else "IPv6  " + v6)
            value(hardware, if (group.mac.isEmpty()) "MAC not available" else "MAC   " + group.mac)
            hardware.typeface = if (group.mac.isEmpty()) AppTypography.face(AppTypography.METADATA, false) else Typeface.MONOSPACE
            val vendor = MacVendors.lookup(group.mac)
            value(model, group.model.ifEmpty { if (vendor.isEmpty()) "" else "MAC vendor · " + vendor })
            contentDescription = group.title() + ", " + visual.label + ", " + group.endpoints().size + " IP addresses, " +
                (if (v4.isEmpty()) "" else "IPv4 " + v4 + ", ") + (if (v6.isEmpty()) "" else "IPv6 " + v6 + ", ") +
                (if (group.mac.isEmpty()) "MAC not available" else "MAC " + group.mac) + (if (vendor.isEmpty()) "" else ", MAC vendor " + vendor) + ", open details"
            invalidate()
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (separator) { separatorPaint.color = line; separatorPaint.strokeWidth = dp(1).toFloat(); canvas.drawLine(dp(15).toFloat(), height - 1f, width - dp(15).toFloat(), height - 1f, separatorPaint) }
        }
    }
    private fun detailsPage() {
        val d = selected!!
        val previousY = detailScroll?.scrollY ?: restoredDetailY
        val scroll = ScrollView(this); detailScroll = scroll
        val body = column(); body.setPadding(dp(23), dp(14), dp(23), dp(30)); scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val title = text(d.title(), 26, ink, true); title.letterSpacing = -.01f; body.addView(title); space(body, 8)
        body.addView(text(if (d.self) "This phone" else if (d.gateway) "Wi-Fi gateway" else "Details from your scan", 13, muted, false)); space(body, 21)
        if (d.endpoints().size > 1) { body.addView(text("" + d.endpoints().size + " IP addresses grouped together. Each address keeps its own reported details.", 13, accent, true)); space(body, 16) }
        for (endpoint in d.endpoints()) {
            val addresses = panel(); addresses.setPadding(dp(18), dp(18), dp(18), dp(12))
            field(addresses, "IP address", endpoint.ip, true); field(addresses, "MAC address", endpoint.mac.ifEmpty { "Not available" }, endpoint.mac.isNotEmpty())
            addresses.addView(text(if (endpoint.mac.isEmpty()) "Android limits MAC access. Some devices report an address through their discovery services." else "Source: " + endpoint.macSource + ". This may identify another interface on the device.", 14, muted, false))
            val v6echo = endpoint.details["ICMPv6 response time"]
            val echo = v6echo ?: endpoint.details["ICMP response time"]
            if (!echo.isNullOrEmpty()) { space(addresses, 12); detailRow(addresses, if (v6echo != null) "Response time (ICMPv6)" else "Response time (ICMP)", echo) }
            else if (endpoint.latency >= 0) { space(addresses, 12); detailRow(addresses, "Response time (TCP)", "" + endpoint.latency + " ms") }
            body.addView(addresses); space(body, 16)
            val facts = panel(); facts.setPadding(dp(18), dp(10), dp(18), dp(10)); val missing = ArrayList<String>()
            val identity = arrayOf(arrayOf("Hostname", endpoint.hostname, "hostname"), arrayOf("Manufacturer", endpoint.manufacturer, "manufacturer"), arrayOf("Model", endpoint.model, "model"), arrayOf("Device type", endpoint.kind, "device type"))
            for (item in identity) { if (item[1].isEmpty()) missing.add(item[2]) else detailRow(facts, item[0], item[1]) }
            val vendor = MacVendors.lookup(endpoint.mac)
            if (vendor.isNotEmpty()) { detailRow(facts, "MAC vendor", vendor); facts.addView(text("IEEE-registered organization; may identify the network interface supplier rather than the device brand.", 14, muted, false)) }
            endpoint.details["ONVIF hardware (advertised)"]?.let { detailRow(facts, "Reported hardware", it) }
            for (key in arrayOf("OS (advertised)", "Firmware (advertised)")) endpoint.details[key]?.let { detailRow(facts, key, it) }
            if (endpoint.lastSeen > 0) detailRow(facts, "Last response", time(endpoint.lastSeen))
            if (missing.isNotEmpty()) {
                val unknown = text("Not reported: " + missing.joinToString(", ") + ".", 12, muted, false)
                unknown.setPadding(0, dp(10), 0, dp(5)); facts.addView(unknown)
            }
            body.addView(facts); space(body, 8)
            val lookups = endpoint.metadataLookupRows()
            if (lookups.isNotEmpty()) {
                val lookupPanel = panel(); lookupPanel.setPadding(dp(18), dp(10), dp(18), dp(14))
                lookupPanel.addView(text("Device information lookups", 16, ink, true))
                for ((source, result) in lookups) detailRow(lookupPanel, source, result)
                lookupPanel.addView(text("A failed lookup does not mean this device is offline. Missing details stay unknown.", 14, muted, false))
                body.addView(lookupPanel); space(body, 8)
            }
            val evidence = panel(); evidence.setPadding(dp(18), dp(18), dp(18), 0); val open = expandedDetails.contains(endpoint.ip)
            if (open) populateEvidence(evidence, endpoint)
            evidence.visibility = if (open) View.VISIBLE else View.GONE
            val more = button(if (open) "Hide discovery evidence" else "Show discovery evidence", false)
            more.textSize = 14f; more.contentDescription = more.text.toString() + " for " + endpoint.ip
            more.setOnClickListener {
                val show = !expandedDetails.contains(endpoint.ip); evidence.removeAllViews()
                if (show) { expandedDetails.add(endpoint.ip); populateEvidence(evidence, endpoint) } else expandedDetails.remove(endpoint.ip)
                evidence.visibility = if (show) View.VISIBLE else View.GONE
                more.text = if (show) "Hide discovery evidence" else "Show discovery evidence"
                more.contentDescription = more.text.toString() + " for " + endpoint.ip
            }
            body.addView(more); space(body, 8); body.addView(evidence); space(body, 18)
        }
        body.addView(text("Device names and descriptions are self-reported. Missing details stay unknown; an advertised service does not establish ownership or security.", 14, muted, false)); space(body, 19)
        val copy = button("Copy device details", true)
        copy.setOnClickListener {
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Device details", deviceText(d)))
            toast("Device details copied.")
        }
        body.addView(copy); scroll.post { if (detailScroll === scroll) scroll.scrollTo(0, previousY) }
    }
    private fun detailRow(panel: LinearLayout, label: String, value: String) {
        val stacked = resources.configuration.fontScale >= 1.4f || resources.configuration.screenWidthDp < 340
        val row = LinearLayout(this); row.orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        row.isBaselineAligned = false; row.gravity = Gravity.TOP; row.setPadding(0, dp(9), 0, dp(9))
        val key = text(label, AppTypography.LABEL, muted, true)
        val content = text(value, 14, ink, false); content.setTextIsSelectable(true)
        if (stacked) {
            val keyLayout = LinearLayout.LayoutParams(-1, -2); keyLayout.bottomMargin = dp(6); row.addView(key, keyLayout); row.addView(content, LinearLayout.LayoutParams(-1, -2))
        } else {
            val keyLayout = LinearLayout.LayoutParams(0, -2, 1f); keyLayout.rightMargin = dp(14); row.addView(key, keyLayout); row.addView(content, LinearLayout.LayoutParams(0, -2, 2f))
        }
        panel.addView(row)
    }
    private fun populateEvidence(panel: LinearLayout, endpoint: Device) {
        field(panel, "How this endpoint was found", if (endpoint.evidence.isEmpty()) "Session observation" else join(endpoint.evidence), false)
        field(panel, "Advertised services", if (endpoint.services.isEmpty()) "None observed" else join(endpoint.services), false)
        field(panel, "Observed / advertised ports", if (endpoint.ports.isEmpty()) "None reported" else endpoint.ports.toString(), false)
        for ((key, value) in endpoint.details) if (key !in listOf("ONVIF hardware (advertised)", "OS (advertised)", "Firmware (advertised)", "ICMP response time", "ICMPv6 response time")) field(panel, key, value, false)
        for (hint in endpoint.observations()) field(panel, "Reported " + hint.field.lowercase(Locale.ROOT) + " · " + hint.source, hint.value, false)
    }
    private fun deviceText(group: Device): String {
        val text = StringBuilder(group.title())
        for (d in group.endpoints()) {
            text.append("\n\nIP: ").append(d.ip).append("\nMAC: ").append(d.mac.ifEmpty { "Not available" }).append("\nMAC source: ").append(reported(d.macSource))
                .append("\nHostname: ").append(reported(d.hostname)).append("\nManufacturer: ").append(reported(d.manufacturer)).append("\nModel: ").append(reported(d.model))
            val vendor = MacVendors.lookup(d.mac)
            if (d.kind.isNotEmpty()) text.append("\nDevice type: ").append(d.kind)
            for ((source, result) in d.metadataLookupRows()) text.append("\n").append(source).append(": ").append(result)
            d.details["HomeKit setup (advertised)"]?.let { text.append("\nHomeKit setup (advertised): ").append(it) }
            if (vendor.isNotEmpty()) text.append("\nMAC vendor (IEEE registry): ").append(vendor)
            d.details["Network identifier (DNS)"]?.let { text.append("\nNetwork identifier (DNS): ").append(it) }
            d.details["ONVIF hardware (advertised)"]?.let { text.append("\nReported hardware (ONVIF): ").append(it) }
            text.append("\nServices: ").append(join(d.services)).append("\nObserved / advertised ports: ").append(d.ports)
                .append("\nFound by: ").append(join(d.evidence)).append("\nLast response: ").append(time(d.lastSeen))
        }
        return text.toString()
    }
    private fun field(p: LinearLayout, label: String, value: String, mono: Boolean) {
        p.addView(text(label, AppTypography.LABEL, muted, true)); space(p, 6)
        val text = text(value, if (mono) AppTypography.DETAIL_ADDRESS else 14, ink, false)
        if (mono) { AppTypography.identifier(text, AppTypography.DETAIL_ADDRESS); text.text = AppTypography.address(value) }
        text.setTextIsSelectable(true); p.addView(text); space(p, 16)
    }
    private fun beginScan() {
        if (!store.ready()) { render(); return }
        if (demo) { toast("Exit the sample to scan your Wi-Fi."); return }
        if (scanning) return
        if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission(LAN_PERMISSION) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(LAN_PERMISSION), LOCAL_NETWORK); return }
        try { network = NetworkState.current(this) } catch (e: Exception) { message = e.message ?: ""; render(); return }
        startDiscovery()
    }
    private fun startDiscovery() {
        resetListPosition(); store.clear()
        val chosen = network!!; val token = store.token(); val operation = Discovery(this, chosen); discovery = operation
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
            next.raw = next.snapshot()
            next.groups = Device.grouped(next.raw)
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
        val admitted = next.raw.filter(current::contains)
        displaySequence = next.sequence; devices = admitted
        displayGroups = if (admitted.size == next.raw.size) next.groups else Device.grouped(admitted)
        groupedSource = devices
        selected = selected?.let { Device.currentGroup(admitted, it) }
        if (next.finished) {
            store.report(current.key)?.let { report -> store.retainIfCurrent(next.token, current.key, admitted, report) }
            scanning = false; discovery = null; message = next.message
            render(); return
        }
        done = next.done; total = next.total
        status?.let { val label = statusText(); it.text = label; (it.parent as View).contentDescription = label + ". View scan details" }
        progress?.let { it.isIndeterminate = false; it.max = maxOf(1, total); it.progress = done }
        fillDevices()
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
    private fun menu(anchor: View) {
        optionsMenu?.dismiss()
        val position = IntArray(2); anchor.getLocationOnScreen(position)
        val visible = Rect(); anchor.getWindowVisibleDisplayFrame(visible)
        val available = maxOf(dp(100), visible.bottom - position[1] - anchor.height - dp(12))
        val viewport = object : ScrollView(dialogContext()) {
            override fun onMeasure(width: Int, height: Int) { super.onMeasure(width, View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST)) }
        }
        viewport.background = border(card, line, 20); viewport.clipToOutline = true
        val items = column(); items.setPadding(dp(6), dp(6), dp(6), dp(6)); viewport.addView(items)
        val popup = PopupWindow(viewport, minOf(dp(300), resources.displayMetrics.widthPixels - dp(32)), -2, true); optionsMenu = popup
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); popup.isOutsideTouchable = true; popup.elevation = dp(12).toFloat(); popup.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        option(items, popup, "Wi-Fi details", "Addresses and connection", false) { networkInfo() }
        option(items, popup, "Settings", "Scanning, permissions and privacy", false) { openSettings() }
        popup.setOnDismissListener { if (optionsMenu === popup) optionsMenu = null }; popup.showAsDropDown(anchor, 0, dp(6), Gravity.END)
    }
    private fun option(parent: LinearLayout, popup: PopupWindow, title: String, subtitle: String, destructive: Boolean, action: Runnable) {
        val item = column(); item.setPadding(dp(14), dp(12), dp(14), dp(12)); item.minimumHeight = dp(52)
        item.background = RippleDrawable(ColorStateList.valueOf(0x22336644), round(card, 13), null)
        item.addView(text(title, 15, if (destructive) dialogColors().danger else ink, true)); space(item, 4); item.addView(text(subtitle, 12, muted, false))
        item.isFocusable = true; item.contentDescription = title + ". " + subtitle; item.setOnClickListener { popup.dismiss(); action.run() }; parent.addView(item)
    }
    private fun dialogColors(): AppDialog.Colors = AppDialog.Colors(bg, card, ink, muted, line, accent, soft)
    private fun openSettings() { showingSettings = true; settingsY = 0; render() }
    private fun openAndroidSettings() {
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + packageName))) }
        catch (_: ActivityNotFoundException) { toast("Open Android Settings, then Apps and Network Lens.") }
    }
    private fun settingsPage() {
        val page = ScrollView(this); settingsScroll = page
        page.isFillViewport = true; page.isVerticalScrollBarEnabled = false
        val body = column(); body.setPadding(dp(23), dp(12), dp(23), dp(30)); page.addView(body)
        root.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        fun section(title: String): LinearLayout {
            val heading = text(title, 14, ink, true); heading.setPadding(0, dp(12), 0, dp(10))
            if (Build.VERSION.SDK_INT >= 28) heading.isAccessibilityHeading = true
            body.addView(heading)
            val block = panel(); block.setPadding(dp(16), dp(16), dp(16), dp(16)); body.addView(block); space(body, 14)
            return block
        }
        fun note(parent: LinearLayout, value: String) { parent.addView(text(value, 14, muted, false)); space(parent, 12) }
        fun action(parent: LinearLayout, title: String, run: () -> Unit) {
            val control = button(title, false); control.isFocusable = true; control.setOnClickListener { run() }
            parent.addView(control, LinearLayout.LayoutParams(-1, -2)); space(parent, 8)
        }
        fun permission(parent: LinearLayout, title: String, state: String, explanation: String) {
            parent.addView(text(title, 16, ink, true)); space(parent, 5)
            parent.addView(text(state, 14, accent, true)); space(parent, 6); note(parent, explanation)
        }
        val scanningOptions = section("Scanning")
        scanningOptions.addView(text(if (range.isEmpty()) "Automatic · current Wi-Fi" else range, 16, ink, true)); space(scanningOptions, 6)
        note(scanningOptions, "Scan range changes apply only to this session.")
        action(scanningOptions, "Scan range") { scanRange() }
        val permissions = section("Permissions")
        val networkAccess = if (Build.VERSION.SDK_INT < 37) "No permission prompt"
            else if (checkSelfPermission(LAN_PERMISSION) == PackageManager.PERMISSION_GRANTED) "Allowed" else "Not allowed"
        permission(permissions, "Network discovery", networkAccess, if (Build.VERSION.SDK_INT < 37)
            "This Android version does not request separate permission for local-network scanning."
            else "Required to find devices on Wi-Fi. You can change access in Android settings.")
        divider(permissions, 0); space(permissions, 16)
        val nameAllowed = wifiName!!.allowed()
        permission(permissions, "Wi-Fi name", if (nameAllowed) "Allowed" else "Not allowed · optional",
            "Android requires precise location access to share the Wi-Fi name. Network Lens does not request GPS coordinates.")
        if (!nameAllowed) action(permissions, "Allow Wi-Fi name") { wifiNameAccess() }
        action(permissions, "Open Android settings") { openAndroidSettings() }
        note(permissions, "Manage permission grants in Android. Scanning works without Wi-Fi name access.")
        val privacyOptions = section("Privacy & about")
        privacyOptions.addView(text("Fresh scans. Nothing saved.", 16, ink, true)); space(privacyOptions, 6)
        note(privacyOptions, "Every scan starts empty. Results and app choices stay in memory only; a full app restart clears them.")
        action(privacyOptions, "Privacy & device details") { privacy() }
        action(privacyOptions, "Clear current results") { confirmClearResults() }
        note(privacyOptions, "Empty this session’s list and stop a scan. Keep the current theme and scan range.")
        body.addView(text("Network Lens · Version " + appVersion(), 12, muted, false))
        val y = settingsY
        page.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (page.viewTreeObserver.isAlive) page.viewTreeObserver.removeOnPreDrawListener(this)
                if (settingsScroll === page) page.scrollTo(0, y)
                return true
            }
        })
    }
    private fun optionsDialog(title: String): AppDialog {
        optionsDialog?.dismiss()
        val dialog = AppDialog(dialogContext(), dialogColors(), title); optionsDialog = dialog
        dialog.setOnDismissListener { if (optionsDialog === dialog) optionsDialog = null }; return dialog
    }
    private fun infoCard(dialog: AppDialog, title: String, value: String, mono: Boolean) {
        val block = dialog.surface(); block.setPadding(dp(16), dp(15), dp(16), dp(15))
        block.addView(dialog.paragraph(title, 13, muted, true)); dialog.gap(block, 7)
        val content = dialog.paragraph(value, if (mono) 15 else 14, if (mono) ink else muted, false)
        if (mono) { AppTypography.identifier(content, 15); content.setTextIsSelectable(true) }
        block.addView(content); dialog.content().addView(block); dialog.gap(dialog.content(), 12)
    }
    private fun resetSessionData() { resetSessionData(true) }
    private fun resetSessionData(forgetRange: Boolean) {
        resetListPosition(); store.clear(); discovery?.cancel(); discovery = null; scanning = false; devices = ArrayList(); selected = null; demo = false
        query = ""; message = ""; if (forgetRange) range = ""
        loadNetwork(); render()
    }
    private fun confirmClearResults() {
        val dialog = optionsDialog("Clear current results?")
        dialog.content().addView(dialog.paragraph("Empty the current device list and stop any active scan. Your appearance and scan range will stay the same for this session. There is no saved history to delete.", 14, ink, false))
        dialog.secondary("Cancel") { true }.primary("Clear results") { resetSessionData(false); true }.show()
    }
    private fun appVersion(): String = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: PackageManager.NameNotFoundException) { "" }
    private fun scanRange() {
        val dialog = optionsDialog("Scan range"); val body = dialog.content()
        body.addView(dialog.paragraph("Automatic checks your current Wi-Fi's IPv4 address range, up to 1,024 addresses. Leave the field empty to use it.", 14, muted, false)); dialog.gap(body, 16)
        body.addView(dialog.paragraph("Custom IPv4 range (optional)", 13, ink, true)); dialog.gap(body, 8)
        val edit = EditText(dialog.context); edit.isSaveEnabled = false; edit.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        edit.imeOptions = edit.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        edit.setSingleLine(true); edit.textSize = 16f; edit.setTextColor(ink); edit.setHintTextColor(muted); edit.setText(range)
        edit.hint = "For example, 192.168.1.0/24"; edit.contentDescription = "Custom IPv4 range"; edit.setPadding(dp(14), dp(14), dp(14), dp(14))
        edit.minHeight = dp(52); edit.background = border(card, line, 14); body.addView(edit, LinearLayout.LayoutParams(-1, -2))
        val error = dialog.paragraph("", 13, dialogColors().danger, false); error.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; error.visibility = View.GONE
        dialog.gap(body, 8); body.addView(error); dialog.gap(body, 10)
        body.addView(dialog.paragraph("A custom range must stay inside the Wi-Fi network you are connected to. IPv6 devices are found through service advertisements, rather than an address sweep.", 13, muted, false))
        dialog.secondary("Cancel") { true }.primary("Save range") {
            val value = edit.text.toString().javaTrim()
            try {
                if (value.isNotEmpty()) {
                    val current = network
                    if (current == null || current.ip.isEmpty()) throw IllegalArgumentException("Connect to an IPv4 Wi-Fi network before choosing a custom range.")
                    val parts = value.javaSplit("/", -1)
                    if (parts.size != 2 || !parts[1].matches(Regex("[0-9]{1,2}"))) throw IllegalArgumentException("Enter an IPv4 address and prefix, such as 192.168.1.0/24.")
                    val chosen = IpRange(parts[0], Integer.parseInt(parts[1])); val local = IpRange(current.ip, current.prefix)
                    if (chosen.first < local.first || chosen.last > local.last) throw IllegalArgumentException("Choose a range inside your current Wi-Fi network.")
                    if (chosen.size() > 1024) throw IllegalArgumentException("Choose a smaller range, with no more than 1,024 addresses.")
                }
            } catch (invalid: IllegalArgumentException) {
                error.text = invalid.message; error.visibility = View.VISIBLE; edit.requestFocus(); return@primary false
            }
            range = value; message = if (value.isEmpty()) "Next scan: the current Wi-Fi address range." else "Next scan: " + value; render(); true
        }.show()
    }
    private fun networkInfo() {
        val dialog = optionsDialog("Wi-Fi details"); val current = network
        val name = if (current == null) "" else wifiName!!.get(current.network)
        if (current == null) {
            dialog.content().addView(dialog.paragraph("Connect to Wi-Fi to see this phone's network addresses and connection details.", 14, muted, false))
            dialog.secondary("Wi-Fi settings") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)); true }.primary("Done") { true }.show(); return
        }
        infoCard(dialog, "Wi-Fi name", name.ifEmpty { "Hidden by Android or not available" }, false)
        infoCard(dialog, "This phone's addresses", join(current.addresses), true); infoCard(dialog, "Router address", reported(current.gateway), true)
        infoCard(dialog, "Name servers (DNS)", reported(current.dns), true); infoCard(dialog, "Wi-Fi interface", current.iface, true)
        dialog.content().addView(dialog.paragraph("Session results belong to this Wi-Fi connection. Reconnecting starts a fresh device list.", 13, muted, false))
        if (!wifiName!!.allowed()) dialog.secondary("Allow Wi-Fi name") { wifiNameAccess(); true }
        else if (name.isEmpty()) dialog.secondary("Wi-Fi name help") { wifiNameAccess(); true }
        dialog.primary("Done") { true }.show()
    }
    private fun wifiNameAccess() {
        if (wifiName!!.allowed()) {
            val name = network?.let { wifiName!!.get(it.network) } ?: ""; val dialog = optionsDialog("Wi-Fi name")
            infoCard(dialog, if (name.isEmpty()) "Name unavailable" else "Connected network", name.ifEmpty { "Permission is allowed, but Android has not supplied a Wi-Fi name." }, false)
            dialog.content().addView(dialog.paragraph(if (name.isEmpty()) "Connect to Wi-Fi and check that Location is on in Android settings. Scanning still works without the name." else "The network name appears automatically under Scan Wi-Fi. You can hide it by revoking location permission in Android app settings.", 14, muted, false))
            dialog.gap(dialog.content(), 12)
            dialog.content().addView(dialog.paragraph("Network Lens reads the Wi-Fi name. It does not request GPS coordinates or save the name.", 13, muted, false))
            dialog.secondary(if (name.isEmpty()) "Location settings" else "Open Android settings") {
                if (name.isEmpty()) startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) else openAndroidSettings(); true
            }.primary("Done") { true }.show(); return
        }
        val dialog = optionsDialog("Show Wi-Fi name"); val body = dialog.content()
        body.addView(dialog.paragraph("Show your connected network under Scan Wi-Fi.", 16, ink, true)); dialog.gap(body, 14)
        infoCard(dialog, "What Android needs", "Allow precise location while using the app so Android can share the connected Wi-Fi name.", false)
        infoCard(dialog, "What Network Lens uses", "Only the Wi-Fi name. Network Lens does not request GPS coordinates or save the name.", false)
        body.addView(dialog.paragraph("This is optional. You can still scan for devices without it.", 14, muted, false))
        dialog.secondary("Not now") { true }.primary("Continue") {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), WIFI_NAME); true
        }.show()
    }
    private fun privacy() {
        val dialog = optionsDialog("Privacy & device details")
        dialog.content().addView(dialog.paragraph("Network Lens scans the Wi-Fi you choose. Scan results and your appearance choice stay only in memory.", 14, ink, false)); dialog.gap(dialog.content(), 16)
        infoCard(dialog, "When scans run", "You start each scan. Discovery stops when you leave the app. There is no background monitoring, account, advertising or analytics SDK.", false)
        infoCard(dialog, "Session-only results", "Network Lens writes no scan files, database, history, preferences or logs. Results disappear when the app process ends. Every scan starts empty. On restart, appearance follows your phone. An explicit Copy action uses the Android clipboard; Android manages permission grants and system caches.", false)
        infoCard(dialog, "Why some MAC addresses are unavailable", "Modern Android restricts access to other devices' MAC addresses. Some devices report one through their discovery services. Network Lens shows the source and never makes an address up. A reported MAC may belong to another interface on the device.", false)
        infoCard(dialog, "MAC vendor names", "An offline IEEE public registry maps globally assigned MAC prefixes to their registered organizations. This may identify an interface supplier rather than the product brand. Locally assigned or randomized MACs are not matched. No address is sent to a lookup service.", false)
        infoCard(dialog, "Names and grouped addresses", "Names and model details come from device responses or advertisements and may be incomplete. Router DNS names are not used. Related IP addresses are grouped using discovery evidence; a matching name alone does not merge devices.", false)
        dialog.primary("Done") { true }.show()
    }
    private fun reported(value: String?): String = if (value.isNullOrEmpty()) "Not reported" else value
    private fun time(value: Long): String = if (value <= 0) "Not recorded" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
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
    private fun dialogContext(): Context = ContextThemeWrapper(this, if (store.dark()) R.style.AppTheme_Dark else R.style.AppTheme)
    private fun toast(value: String) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show() }
    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView =
        AppTypography.text(this, value, size, color, bold)
    private fun button(value: String, filled: Boolean, size: Int = 14): TextView = text(value, size, if (filled) card else accent, true).also {
        it.gravity = Gravity.CENTER; it.setPadding(dp(15), dp(15), dp(15), dp(15)); it.minHeight = dp(52)
        it.background = RippleDrawable(ColorStateList.valueOf(0x22336644), if (filled) round(accent, 14) else border(card, line, 14), null)
    }
    private fun column(): LinearLayout = LinearLayout(this).also { it.orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(this).also { it.orientation = LinearLayout.HORIZONTAL }
    private fun panel(): LinearLayout = column().also { it.background = border(card, line, 20) }
    private fun round(color: Int, radius: Int): GradientDrawable = GradientDrawable().also { it.setColor(color); it.cornerRadius = dp(radius).toFloat() }
    private fun border(color: Int, stroke: Int, radius: Int): GradientDrawable = round(color, radius).also { it.setStroke(dp(1), stroke) }
    private fun divider(parent: LinearLayout, inset: Int) {
        val v = View(this); v.setBackgroundColor(line); val p = lp(-1, 1); p.setMargins(dp(inset), 0, dp(inset), 0); parent.addView(v, p)
    }
    private fun space(parent: LinearLayout, size: Int) { parent.addView(View(this), lp(1, size)) }
    private fun spaceHorizontal(parent: LinearLayout, size: Int) { parent.addView(View(this), lp(size, 1)) }
    private fun lp(width: Int, height: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(if (width < 0) width else dp(width), if (height < 0) height else dp(height))
    private fun dp(value: Float): Int = Math.round(value * resources.displayMetrics.density)
    private fun dp(value: Int): Int = dp(value.toFloat())
    private inner class ModeToggle : View(this@MainActivity) {
        private val p = Paint(3)
        init { background = RippleDrawable(ColorStateList.valueOf(0x22336644), round(soft, 15), null) }
        override fun onDraw(c: Canvas) {
            super.onDraw(c); c.save(); c.scale(width / 48f, height / 48f)
            p.color = accent; p.style = Paint.Style.STROKE; p.strokeWidth = 1.8f
            if (store.dark()) {
                c.drawCircle(24f, 24f, 5.5f, p)
                for (n in 0 until 8) {
                    val a = n * Math.PI / 4
                    c.drawLine(24 + Math.cos(a).toFloat() * 9, 24 + Math.sin(a).toFloat() * 9, 24 + Math.cos(a).toFloat() * 12, 24 + Math.sin(a).toFloat() * 12, p)
                }
            } else { p.style = Paint.Style.FILL; c.drawCircle(23f, 25f, 10f, p); p.color = soft; c.drawCircle(28f, 20f, 9f, p) }
            c.restore()
        }
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
