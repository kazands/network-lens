@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.kazands.networklens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

@Composable internal fun SettingsScreen(state: UiState,memory: UiMemory,action: (UiAction)->Unit) {
    val generation=memory.settingsVersion
    val scroll=remember(generation) { ScrollState(memory.settingsOffset) }
    LaunchedEffect(scroll,generation) { snapshotFlow {scroll.value}.distinctUntilChanged().collect { if(memory.settingsVersion==generation) memory.settingsOffset=it } }
    DisposableEffect(scroll,generation) {onDispose {if(memory.settingsVersion==generation) memory.settingsOffset=scroll.value}}
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp).testTag("settings"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        SectionTitle("Scanning")
        DetailSurface { Text(if(state.range.isEmpty()) "Automatic · current Wi-Fi" else state.range,style=MaterialTheme.typography.titleMedium);Text("Scan range changes apply only to this session.",style=MaterialTheme.typography.bodyMedium);SettingsAction("Scan range",UiAction.Range,action) }
        SectionTitle("Permissions")
        DetailSurface {
            InfoField("Network discovery",if(state.networkAllowed) "Allowed" else "Not allowed")
            Text("Required to find devices on Wi-Fi. Older Android versions do not request separate local-network permission.",style=MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            InfoField("Wi-Fi name",if(state.wifiNameAllowed) "Allowed" else "Not allowed · optional")
            Text("Android requires precise location access to share the Wi-Fi name. Network Lens does not request GPS coordinates.",style=MaterialTheme.typography.bodyMedium)
            if(!state.wifiNameAllowed) SettingsAction("Allow Wi-Fi name",UiAction.WifiName,action)
            SettingsAction("Open Android settings",UiAction.AndroidSettings,action)
            Text("Scanning works without Wi-Fi name access.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionTitle("App updates")
        DetailSurface { Text("Installed version · ${state.version}",style=MaterialTheme.typography.titleMedium);Text("Find new releases, including previews. Android asks before installing.",style=MaterialTheme.typography.bodyMedium);SettingsAction("Check for updates",UiAction.Updates,action) }
        SectionTitle("Privacy & about")
        DetailSurface {
            Text("Fresh scans. Nothing saved.",style=MaterialTheme.typography.titleMedium)
            Text("Every scan starts empty. Results and app choices stay in memory only; a full app restart clears them.",style=MaterialTheme.typography.bodyMedium)
            SettingsAction("Privacy & device details",UiAction.Privacy,action)
            SettingsAction("Clear current results",UiAction.Clear,action)
            Text("Empty this session’s list and stop a scan. Keep the current theme and scan range.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Network Lens · Version ${state.version}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun SettingsAction(label: String,event: UiAction,action: (UiAction)->Unit) {
    TextButton(onClick={action(event)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),contentPadding=PaddingValues(vertical=8.dp)) {
        Text(label,modifier=Modifier.weight(1f));LensIcon(R.drawable.ic_ui_chevron_right,null,tint=MaterialTheme.colorScheme.primary)
    }
}
@Composable internal fun LensModal(state: UiState,memory: UiMemory,action: (UiAction)->Unit) {
    val modal=state.modal ?: return
    key(modal) {
        when(modal) {
            UiModal.SCAN -> ScanSheet(state,memory,action)
            UiModal.UPDATES -> UpdateModal(state,action)
            UiModal.RANGE -> RangeModal(state,memory,action)
            UiModal.NETWORK -> ModalFrame("Wi-Fi details",action,actions={ ModalButton("Done",UiAction.CloseModal,action) }) {
                val network=state.network
                if(network==null) { Text("Connect to Wi-Fi to see this phone's network addresses and connection details.");OutlinedButton(onClick={action(UiAction.WifiSettings)}) {Text("Open Wi-Fi settings")} }
                else {
                    InfoField("Wi-Fi name",state.ssid.ifEmpty {"Hidden by Android or not available"})
                    for(ip in network.addresses.sortedWith(Address::compare)) AddressField(if(ip.contains(':')) "IPv6" else "IPv4",ip) {action(UiAction.Copy("Network address",ip))}
                    if(network.gateway.isNotEmpty()) AddressField("Router address",network.gateway) {action(UiAction.Copy("Router address",network.gateway))} else InfoField("Router address","Not reported")
                    if(network.dns.isNotEmpty()) AddressField("Name servers (DNS)",network.dns) {action(UiAction.Copy("Name servers",network.dns))} else InfoField("Name servers (DNS)","Not reported")
                    InfoField("Wi-Fi interface",network.iface)
                    Text("Session results belong to this Wi-Fi connection. Reconnecting starts a fresh device list.")
                    TextButton(onClick={action(UiAction.WifiName)}) {Text(if(state.wifiNameAllowed) "Wi-Fi name help" else "Allow Wi-Fi name")}
                }
            }
            UiModal.PRIVACY -> ModalFrame("Privacy & device details",action,actions={ModalButton("Done",UiAction.CloseModal,action)}) {
                Text("Network Lens scans the Wi-Fi you choose. Scan results and your appearance choice stay only in memory.")
                for((label,value) in listOf(
                    "When scans run" to "You start each scan. Discovery stops when you leave the app. There is no background monitoring, account, advertising or analytics SDK.",
                    "Session-only results" to "Network Lens writes no scan files, database, history, preferences or logs. Results disappear when the app process ends. Every scan starts empty. On restart, appearance follows your phone. An explicit Copy action uses the Android clipboard; Android manages permission grants and system caches.",
                    "Why some MAC addresses are unavailable" to "Modern Android restricts access to other devices' MAC addresses. Some devices report one through their discovery services. Network Lens shows the source and never makes an address up. A reported MAC may belong to another interface on the device.",
                    "MAC vendor names" to "An offline IEEE public registry maps globally assigned MAC prefixes to their registered organizations. This may identify an interface supplier rather than the product brand. Locally assigned or randomized MACs are not matched. No address is sent to a lookup service.",
                    "Names and grouped addresses" to "Names and model details come from device responses or advertisements and may be incomplete. Router DNS names are diagnostic evidence only. Related IP addresses are grouped using discovery evidence; a matching name alone does not merge devices.",
                    "App updates" to "Checking for updates contacts GitHub without sending scan results. APKs use private temporary storage, cleared after installation or when the update session ends. Android controls installation approval.")) InfoField(label,value)
            }
            UiModal.WIFI_NAME -> ModalFrame(if(state.wifiNameAllowed) "Wi-Fi name" else "Show Wi-Fi name",action,actions={
                if(state.wifiNameAllowed) { ModalButton("Done",UiAction.CloseModal,action);TextButton(onClick={action(if(state.ssid.isEmpty()) UiAction.LocationSettings else UiAction.AndroidSettings)}) {Text(if(state.ssid.isEmpty()) "Location settings" else "Open Android settings")} }
                else { ModalButton("Continue",UiAction.RequestWifiName,action);TextButton(onClick={action(UiAction.CloseModal)}) {Text("Not now")} }
            }) {
                if(state.wifiNameAllowed) { InfoField(if(state.ssid.isEmpty()) "Name unavailable" else "Connected network",state.ssid.ifEmpty {"Permission is allowed, but Android has not supplied a Wi-Fi name."});Text(if(state.ssid.isEmpty()) "Connect to Wi-Fi and check that Location is on in Android settings. Scanning still works without the name." else "The network name appears automatically on the scan card. You can hide it by revoking location permission in Android app settings.") }
                else { Text("Show your connected network on the scan card.");InfoField("What Android needs","Allow precise location while using the app so Android can share the connected Wi-Fi name.");Text("This is optional. You can still scan for devices without it.") }
                InfoField("What Network Lens uses","Only the Wi-Fi name. Network Lens does not request GPS coordinates or save the name.")
            }
            UiModal.CLEAR -> ModalFrame("Clear current results?",action,actions={ ModalButton("Clear results",UiAction.ConfirmClear,action);TextButton(onClick={action(UiAction.CloseModal)}) {Text("Cancel")} }) {
                Text("Empty the current device list and stop any active scan. Your appearance and scan range will stay the same for this session. There is no saved history to delete.")
            }
            UiModal.REPORT_OPTIONS -> {
                var include by remember {mutableStateOf(false)}
                ModalFrame("Troubleshooting report",action,actions={ Button(onClick={action(UiAction.PrepareReport(include))}) {Text("Preview report")};TextButton(onClick={action(UiAction.CloseModal)}) {Text("Cancel")} }) {
                    Text("Includes every device, returned observations and recorded lookup history. By default, personal names and addresses are replaced with labels. Public page titles, authentication realms and certificate names may contain personal information; they are masked by default. Unreviewed values and credentials are withheld. Review the preview before sharing.")
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=include,role=Role.Checkbox,onValueChange={include=it}),verticalAlignment=Alignment.CenterVertically) { Checkbox(checked=include,onCheckedChange=null);Text("Include device names, local addresses and public service labels",modifier=Modifier.weight(1f)) }
                    Text("The shared file is held in memory for up to 10 minutes. The receiving app may save it. Starting a new scan or detecting a network change removes the temporary copy. If another app is open, Network Lens checks for network changes when you return.",style=MaterialTheme.typography.bodySmall)
                }
            }
            UiModal.REPORT -> state.document?.let { document ->
                var page by remember(document) {mutableIntStateOf(0)}
                ModalFrame("Troubleshooting report",action,bodyKey=page,actions={ ModalButton("Share complete report",UiAction.ShareReport,action);TextButton(onClick={action(UiAction.CloseModal)}) {Text("Close")} },controls={
                    Text("Page ${page+1} of ${document.pages.size}. Sharing includes every page.",style=MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={page--},enabled=page>0) {Text("Previous")}
                        OutlinedButton(onClick={page++},enabled=page<document.pages.lastIndex) {Text("Next")}
                        OutlinedButton(onClick={action(UiAction.CopyReportPage(page))}) {Text("Copy this page")}
                    }
                }) {
                    SelectionContainer { Text(document.pages[page],style=MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
@Composable private fun ScanSheet(state: UiState,memory: UiMemory,action: (UiAction)->Unit) {
    ModalBottomSheet(onDismissRequest={action(UiAction.CloseModal)},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),scrimColor=Color.Black.copy(alpha=.65f),containerColor=MaterialTheme.colorScheme.surface) {
        PrivateUiRoot()
        val scroll=rememberScrollState()
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*.85f).dp).navigationBarsPadding().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text("Scan details",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f).semantics {heading()});TextButton(onClick={action(UiAction.CloseModal)}) {Text("Done")} }
            Column(Modifier.weight(1f,fill=false).verticalScroll(scroll).padding(bottom=24.dp).testTag("scan-details-scroll"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(if(state.demo) "Sample results" else if(state.scanning) "Scan in progress" else state.report?.statusLabel ?: "Ready to scan",style=MaterialTheme.typography.headlineMedium)
                Text(if(state.demo) "Sample devices show how Network Lens looks. They do not describe your Wi-Fi." else if(state.scanning) "Network Lens is checking addresses and listening for device details." else "Results reflect responses during this scan. Scan again to refresh them.")
                if(!state.demo) {
                    Text(state.status)
                    val total=if(state.scanning) state.total else state.report?.total ?: 0
                    val checked=if(state.scanning) state.done else state.report?.checked ?: 0
                    if(total>0) InfoField("Network coverage","$checked / $total addresses checked")
                    val scope=state.report?.scope?.takeIf {!state.scanning} ?: state.scope
                    if(scope.isNotEmpty()) InfoField("Scan scope",scope)
                    if(state.report?.complete==false && !state.scanning) {
                        state.report.reason.takeIf {it.isNotEmpty()}?.let {InfoField("Scan explanation",it)}
                        Text(RecognitionCopy.NEXT_SCAN,style=MaterialTheme.typography.bodySmall)
                    }
                    val available=!state.scanning && state.report!=null
                    OutlinedButton(onClick={action(UiAction.ReportOptions)},enabled=available && !state.reportPreparing,modifier=Modifier.fillMaxWidth()) {Text(if(state.reportPreparing) "Preparing report…" else "Troubleshooting report")}
                    Text("Review evidence for every device, including unnamed devices and lookup history.",style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick={action(UiAction.CopyRecognition)},enabled=available,modifier=Modifier.fillMaxWidth()) {Text("Copy recognition report")}
                    Text("Recognition summaries exclude device names, addresses and identifiers. Nothing is uploaded by copying.",style=MaterialTheme.typography.bodySmall)
                    val review=state.sources.count {it.value.startsWith("Failed") || it.value.startsWith("Truncated") || it.value.startsWith("Running")}
                    if(review>0) Text("$review checks to review",style=MaterialTheme.typography.titleMedium)
                    if((state.report?.omittedSources ?: 0)>0) Text("Details for ${state.report!!.omittedSources} additional methods could not be retained.")
                    Disclosure("Show method outcomes","scan-methods",memory,"Hide method outcomes") {
                        if(state.sources.isEmpty()) Text("No method outcomes recorded yet.")
                        for((source,result) in state.sources) InfoField(source,result)
                    }
                    Text("Silent or isolated devices may be missing. A device that does not reply is not necessarily offline.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
@Composable private fun RangeModal(state: UiState,memory: UiMemory,action: (UiAction)->Unit) {
    var value by remember {mutableStateOf(state.range)}
    ModalFrame("Scan range",action,actions={ Button(onClick={action(UiAction.SaveRange(value))}) {Text("Save range")};TextButton(onClick={action(UiAction.CloseModal)}) {Text("Cancel")} }) {
        Text("Automatic checks your current Wi-Fi's IPv4 address range, up to 1,024 addresses. Leave the field empty to use it.")
        OutlinedTextField(value=value,onValueChange={value=it;memory.rangeError=""},label={Text("Custom IPv4 range (optional)")},placeholder={Text("192.168.1.0/24")},modifier=Modifier.fillMaxWidth().testTag("range-input"),singleLine=true,isError=memory.rangeError.isNotEmpty(),keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,keyboardType=KeyboardType.Uri))
        if(memory.rangeError.isNotEmpty()) Text(memory.rangeError,color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
        Text("A custom range must stay inside the Wi-Fi network you are connected to. IPv6 devices are found through service advertisements, rather than an address sweep.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun UpdateModal(state: UiState,action: (UiAction)->Unit) {
    val step=state.update.step
    ModalFrame("App updates",action,actions={
        when(step) {
            UpdateFlow.Step.IDLE,UpdateFlow.Step.ERROR -> {ModalButton("Check again",UiAction.CheckUpdate,action);TextButton(onClick={action(UiAction.CloseModal)}) {Text("Close")}}
            UpdateFlow.Step.CHECKING,UpdateFlow.Step.DOWNLOADING,UpdateFlow.Step.STAGING -> ModalButton("Cancel",UiAction.CloseModal,action)
            UpdateFlow.Step.AVAILABLE -> {ModalButton("Download update",UiAction.DownloadUpdate,action);TextButton(onClick={action(UiAction.CloseModal)}) {Text("Later")}}
            UpdateFlow.Step.READY -> {ModalButton("Install update",UiAction.InstallUpdate,action);TextButton(onClick={action(UiAction.CloseModal)}) {Text("Later")}}
            UpdateFlow.Step.INSTALLING -> {if(state.canInstall) ModalButton("Continue installation",UiAction.InstallUpdate,action);TextButton(onClick={action(UiAction.CloseModal)}) {Text("Cancel")}}
            UpdateFlow.Step.CURRENT -> ModalButton("Done",UiAction.CloseModal,action)
            UpdateFlow.Step.BLOCKED -> ModalButton("Close",UiAction.CloseModal,action)
        }
    }) {
        Text("Installed · ${state.version}",style=MaterialTheme.typography.bodyMedium)
        Text(when(step) {
            UpdateFlow.Step.IDLE -> "Ready to check"
            UpdateFlow.Step.CHECKING -> "Checking for updates…"
            UpdateFlow.Step.CURRENT -> "You’re up to date"
            UpdateFlow.Step.AVAILABLE -> "Update available · ${state.release?.version ?: ""}"
            UpdateFlow.Step.DOWNLOADING -> "Downloading · ${state.update.percent}%"
            UpdateFlow.Step.READY -> "Update verified · ${state.release?.version ?: ""}"
            UpdateFlow.Step.STAGING -> "Preparing the installer…"
            UpdateFlow.Step.INSTALLING -> "Confirm the update"
            UpdateFlow.Step.BLOCKED -> "Update blocked by Android"
            UpdateFlow.Step.ERROR -> "Update couldn’t finish"
        },style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
        if(step==UpdateFlow.Step.DOWNLOADING) LinearProgressIndicator(progress={state.update.percent/100f},modifier=Modifier.fillMaxWidth())
        if(step==UpdateFlow.Step.CHECKING || step==UpdateFlow.Step.STAGING) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        state.release?.takeIf {step in setOf(UpdateFlow.Step.AVAILABLE,UpdateFlow.Step.DOWNLOADING,UpdateFlow.Step.READY)}?.let {
            Text((if(it.preview) "Development preview" else "Public release")+" · "+String.format(Locale.ROOT,"%.1f MB",it.bytes/1048576.0))
        }
        if(state.update.message.isNotEmpty()) Text(state.update.message)
        Text("Checks GitHub’s public releases only when you ask. No scan results are sent. Keep Network Lens open while checking or downloading.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun ModalButton(label: String,event: UiAction,action: (UiAction)->Unit) { Button(onClick={action(event)},modifier=Modifier.heightIn(min=48.dp)) {Text(label)} }
@Composable private fun ModalFrame(title: String,action: (UiAction)->Unit,actions: @Composable FlowRowScope.()->Unit,bodyKey: Any?=null,controls: @Composable ColumnScope.()->Unit={},content: @Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest={action(UiAction.CloseModal)},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        PrivateUiRoot()
        val view=LocalView.current
        SideEffect {(view.parent as? DialogWindowProvider)?.window?.setDimAmount(.65f)}
        Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.padding(16.dp).widthIn(max=560.dp).fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp-48).coerceAtLeast(200).dp).imePadding().testTag("modal")) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics {heading()})
                controls()
                val bodyScroll=remember(bodyKey) {ScrollState(0)}
                Column(Modifier.weight(1f,fill=false).verticalScroll(bodyScroll),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
                HorizontalDivider()
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End),verticalArrangement=Arrangement.spacedBy(4.dp),content=actions)
            }
        }
    }
}
