package io.github.kazands.networklens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** All navigation, expansion and scroll choices are process-local; never rememberSaveable. */
internal class UiMemory {
    var notice by mutableStateOf<String?>(null)
    var filter by mutableStateOf(DeviceFilter.ALL)
    var menuOpen by mutableStateOf(false)
    var rangeError by mutableStateOf("")
    val expanded = mutableStateMapOf<String,Boolean>()
    var detailVersion = 0
    var settingsVersion = 0
    var listVersion = 0
    var listIndex = 0; var listOffset = 0
    var listKey: String? = null
    var detailOffset = 0; var settingsOffset = 0
    fun resetList() { listVersion++; listIndex=0; listOffset=0; listKey=null }
    fun resetDetails() { detailVersion++; detailOffset=0; expanded.clear() }
}
internal enum class DeviceFilter(val label: String) { ALL("All"), UNNAMED("Unnamed"), NETWORK("Network"), HOME("Smart home"), COMPUTERS("Computers"), MEDIA("Media") }
internal enum class UiModal { SCAN, RANGE, NETWORK, PRIVACY, WIFI_NAME, UPDATES, CLEAR, REPORT_OPTIONS, REPORT }
internal data class UiState(
    val dark: Boolean, val ready: Boolean, val demo: Boolean, val scanning: Boolean,
    val settings: Boolean, val selected: Device?, val groups: List<Device>, val query: String, val range: String,
    val network: NetworkState?, val ssid: String, val scope: String, val status: String,
    val done: Int, val total: Int, val report: ScanReport?, val sources: Map<String,String>,
    val modal: UiModal?, val version: String, val wifiNameAllowed: Boolean, val networkAllowed: Boolean,
    val update: UpdateFlow.View, val release: AppUpdates.Release?, val canInstall: Boolean,
    val reportPreparing: Boolean, val document: TroubleshootingReport.Document?
)
internal sealed interface UiAction {
    data object Scan: UiAction
    data object ExitSample: UiAction
    data class Select(val device: Device): UiAction
    data class Search(val value: String): UiAction
    data class Filter(val value: DeviceFilter): UiAction
    data object Back: UiAction
    data object Theme: UiAction
    data object Settings: UiAction
    data object ScanDetails: UiAction
    data object NetworkInfo: UiAction
    data object Privacy: UiAction
    data object Range: UiAction
    data object Clear: UiAction
    data object ConfirmClear: UiAction
    data object AndroidSettings: UiAction
    data object WifiSettings: UiAction
    data object LocationSettings: UiAction
    data object WifiName: UiAction
    data object RequestWifiName: UiAction
    data object Updates: UiAction
    data object CheckUpdate: UiAction
    data object DownloadUpdate: UiAction
    data object InstallUpdate: UiAction
    data object CloseModal: UiAction
    data object ReportOptions: UiAction
    data class PrepareReport(val includeNames: Boolean): UiAction
    data object CopyRecognition: UiAction
    data object ShareReport: UiAction
    data class CopyReportPage(val page: Int): UiAction
    data class Copy(val label: String,val value: String,val sensitive: Boolean = true): UiAction
    data class CopyDevice(val device: Device): UiAction
    data class SaveRange(val value: String): UiAction
    data object FinishCleanup: UiAction
}
