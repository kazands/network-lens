@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.kazands.networklens

import androidx.compose.foundation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

internal object UiDevicePolicy {
    fun matches(group: Device,query: String,filter: DeviceFilter): Boolean {
        val type=group.classification().category
        val category=when(filter) {
            DeviceFilter.ALL -> true
            DeviceFilter.UNNAMED -> group.presentation().unnamed
            DeviceFilter.NETWORK -> group.gateway || type in setOf(DeviceCatalog.Category.ROUTER,DeviceCatalog.Category.ACCESS_POINT,DeviceCatalog.Category.NETWORK_SWITCH,DeviceCatalog.Category.RANGE_EXTENDER)
            DeviceFilter.COMPUTERS -> group.self || type in setOf(DeviceCatalog.Category.LAPTOP,DeviceCatalog.Category.DESKTOP,DeviceCatalog.Category.TABLET,DeviceCatalog.Category.PHONE,DeviceCatalog.Category.NAS)
            DeviceFilter.MEDIA -> type in setOf(DeviceCatalog.Category.SPEAKER,DeviceCatalog.Category.TV_PLAYER,DeviceCatalog.Category.TV,DeviceCatalog.Category.SMART_DISPLAY,DeviceCatalog.Category.HANDHELD,DeviceCatalog.Category.RECORDER,DeviceCatalog.Category.CONSOLE,DeviceCatalog.Category.AMPLIFIER,DeviceCatalog.Category.AUDIO_RECEIVER,DeviceCatalog.Category.REMOTE,DeviceCatalog.Category.REMOTE_HUB)
            DeviceFilter.HOME -> type != null && type in setOf(DeviceCatalog.Category.THERMOSTAT,DeviceCatalog.Category.CAMERA,DeviceCatalog.Category.DOORBELL,DeviceCatalog.Category.LIGHT,DeviceCatalog.Category.LOCK,DeviceCatalog.Category.OUTLET,DeviceCatalog.Category.SWITCH,DeviceCatalog.Category.SENSOR,DeviceCatalog.Category.FAN,DeviceCatalog.Category.HEATER,DeviceCatalog.Category.AIR_CONDITIONER,DeviceCatalog.Category.AIR_PURIFIER,DeviceCatalog.Category.HUMIDIFIER,DeviceCatalog.Category.DEHUMIDIFIER,DeviceCatalog.Category.BRIDGE,DeviceCatalog.Category.HUB,DeviceCatalog.Category.GARAGE_DOOR,DeviceCatalog.Category.SECURITY_SYSTEM,DeviceCatalog.Category.ALARM_INTERFACE,DeviceCatalog.Category.DOOR,DeviceCatalog.Category.WINDOW,DeviceCatalog.Category.WINDOW_COVERING,DeviceCatalog.Category.SPRINKLER,DeviceCatalog.Category.FAUCET,DeviceCatalog.Category.SHOWER)
        }
        if(!category) return false
        val term=query.trim().lowercase(Locale.ROOT)
        if(term.isEmpty()) return true
        val labels=group.presentation()
        return group.endpoints().any { d ->
            val product=d.identifiedProduct()?.product
            (labels.title+" "+labels.subtitle+" "+d.ip+" "+d.mac+" "+d.name+" "+d.hostname+" "+d.model+" "+d.manufacturer+" "+MacVendors.lookup(d.mac)+" "+d.services+" "+product?.title.orEmpty()+" "+product?.category?.label.orEmpty()+" "+group.capabilities().joinToString(" ")+" "+d.observations().joinToString(" "){it.value}).lowercase(Locale.ROOT).contains(term)
        }
    }
}
@Composable internal fun DevicesScreen(state: UiState,memory: UiMemory,action: (UiAction)->Unit) {
    val filter=memory.filter
    val shown=remember(state.groups,state.query,filter) { state.groups.filter { UiDevicePolicy.matches(it,state.query,filter) } }
    val generation=memory.listVersion
    val showControls=state.groups.isNotEmpty() || state.query.isNotEmpty() || filter != DeviceFilter.ALL
    val itemKeys=listOfNotNull(if(state.demo) "sample" else null,"scan",
        if(showControls) "search" else null,if(showControls) "filters" else null,"heading")+
        if(shown.isEmpty()) listOf("empty") else shown.map {it.ip}
    val list=remember(state.network?.key,state.demo,state.query,filter,generation) {
        // The list is disposed while handset details/settings are open. Resolve
        // its retained key against the current results, which may have grown.
        val index=memory.listKey?.let(itemKeys::indexOf)?.takeIf {it>=0} ?: memory.listIndex
        LazyListState(index.coerceIn(0,itemKeys.lastIndex),memory.listOffset)
    }
    fun savePosition(index: Int,offset: Int,key: String?) {
        if(memory.listVersion==generation && key!=null) {memory.listIndex=index;memory.listOffset=offset;memory.listKey=key}
    }
    LaunchedEffect(list,generation) { snapshotFlow {
        Triple(list.firstVisibleItemIndex,list.firstVisibleItemScrollOffset,
            list.layoutInfo.visibleItemsInfo.firstOrNull {it.index==list.firstVisibleItemIndex}?.key as? String)
    }.distinctUntilChanged().collect { (index,offset,key) -> savePosition(index,offset,key)
    } }
    DisposableEffect(list,generation) {onDispose {
        savePosition(list.firstVisibleItemIndex,list.firstVisibleItemScrollOffset,
            list.layoutInfo.visibleItemsInfo.firstOrNull {it.index==list.firstVisibleItemIndex}?.key as? String)
    }}
    LazyColumn(state=list,modifier=Modifier.fillMaxSize().testTag("devices-list"),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(state.demo) item(key="sample") {
            Column {
                Text("Sample results · not your network",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={action(UiAction.ExitSample)},modifier=Modifier.testTag("exit-sample")) { Text("Use my Wi-Fi") }
            }
        }
        item(key="scan") { ScanHero(state,action) }
        if(showControls) {
            item(key="search") {
                OutlinedTextField(value=state.query,onValueChange={action(UiAction.Search(it))},label={Text("Search devices")},
                    leadingIcon={LensIcon(R.drawable.ic_ui_search,null)},trailingIcon={if(state.query.isNotEmpty()) IconButton(onClick={action(UiAction.Search(""))}) { LensIcon(R.drawable.ic_ui_clear,"Clear search") }},
                    modifier=Modifier.fillMaxWidth().padding(top=8.dp).testTag("search"),singleLine=true,shape=LensShape.tile,
                    keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,imeAction=ImeAction.Search))
            }
            item(key="filters") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("filters"),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    for(value in DeviceFilter.entries) FilterChip(selected=filter==value,onClick={action(UiAction.Filter(value))},label={Text(value.label)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primary,selectedLabelColor=MaterialTheme.colorScheme.onPrimary),modifier=Modifier.heightIn(min=48.dp).testTag("filter-${value.name}"))
                }
            }
        }
        item(key="heading") {
            Column(Modifier.padding(top=8.dp,bottom=4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("Your devices",style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics { heading() })
                if(state.groups.isNotEmpty() || state.query.isNotEmpty() || filter!=DeviceFilter.ALL) {
                    Text("${shown.size}"+(if(state.query.isNotEmpty() || filter!=DeviceFilter.ALL) " matching" else "")+" device"+(if(shown.size==1) "" else "s"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("result-count"))
                }
            }
        }
        if(shown.isEmpty()) item(key="empty") { EmptyDevices(state,filter,action) }
        items(shown,key={it.ip},contentType={"device"}) { device ->
            Box(if(state.scanning) Modifier.animateItem(fadeInSpec=tween(160),placementSpec=null,fadeOutSpec=null) else Modifier) { DeviceCard(device,state.selected?.ip==device.ip,action) }
        }
    }
}
@Composable private fun ScanHero(state: UiState,action: (UiAction)->Unit) {
    Surface(shape=LensShape.hero,color=MaterialTheme.colorScheme.secondaryContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
        val stacked=maxWidth<320.dp || LocalDensity.current.fontScale>=1.4f
        val network=state.ssid.ifEmpty { if(state.network==null && !state.demo) "Wi-Fi not connected" else if(state.demo) "Example network" else "Current Wi-Fi" }
        val title=if(state.groups.isEmpty()) if(state.scanning) "Looking for devices…" else "Discover your network"
            else "${state.groups.size} device"+(if(state.groups.size==1) "" else "s")+" found"
        @Composable fun NetworkLabel(modifier: Modifier=Modifier) {
            Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(network,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSecondaryContainer)
                if(state.scope.isNotEmpty()) Text(state.scope,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        @Composable fun Radar() {
            ScanRadar(state.scanning,if(state.total>0 && state.done<state.total) state.done.coerceAtLeast(0).toFloat()/state.total else null,
                state.report,state.groups.size,Pair(state.network?.key,state.demo),diameter=if(stacked) 48.dp else 64.dp)
        }
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(stacked) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { NetworkLabel(Modifier.weight(1f));Radar() }
                Text(title,style=MaterialTheme.typography.titleLarge)
            } else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) { NetworkLabel();Text(title,style=MaterialTheme.typography.headlineMedium) }
                Radar()
            }
            Button(onClick={action(UiAction.Scan)},enabled=state.scanning || state.demo || state.network?.ip?.isNotEmpty()==true,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("scan")) { Text(if(state.scanning) "Stop scan" else "Scan Wi-Fi") }
            val detailsAction=Modifier.fillMaxWidth().clickable(role=Role.Button) {action(UiAction.ScanDetails)}.heightIn(min=48.dp).testTag("scan-details-action")
            @Composable fun Status(modifier: Modifier=Modifier) {
                Text(state.status,style=MaterialTheme.typography.bodySmall,modifier=modifier.testTag("scan-status").semantics { liveRegion=LiveRegionMode.Polite })
            }
            @Composable fun DetailsLabel() {
                Text("Details",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                LensIcon(R.drawable.ic_ui_chevron_right,null,tint=MaterialTheme.colorScheme.primary)
            }
            if(stacked) Column(detailsAction,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Status(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End)) { DetailsLabel() }
            } else Row(detailsAction,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Status(Modifier.weight(1f));DetailsLabel()
            }
        }
        }
    }
}
@Composable private fun DeviceCard(device: Device,selected: Boolean,action: (UiAction)->Unit) {
    val tone=lensTone(device)
    val label=device.presentation();val endpoints=device.endpoints().sortedWith {a,b->Address.compare(a.ip,b.ip)}
    val primary=endpoints.firstOrNull { !it.ip.contains(':') } ?: endpoints.first()
    val product=device.groupProduct()?.product
    val room=device.name.takeIf { IdentityHints.specificName(it) && it!=label.title && product?.redundantName(it)!=true && !device.self && !device.gateway }.orEmpty()
    val role=when {device.self->"This phone";device.gateway->"Wi-Fi gateway";device.classification().conflicted->"";else->product?.displayType?.takeIf {it.isNotEmpty()} ?: device.classification().category?.label.orEmpty()}
    val compact=LocalDensity.current.fontScale<1.4f && LocalConfiguration.current.screenWidthDp>=360
    Surface(onClick={action(UiAction.Select(device))},shape=LensShape.card,color=MaterialTheme.colorScheme.surface,
        border=if(selected) BorderStroke(1.dp,MaterialTheme.colorScheme.primary) else null,modifier=Modifier.fillMaxWidth().testTag("device:${device.ip}")) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(shape=LensShape.tile,color=tone.container,modifier=Modifier.size(48.dp)) {
                Box(contentAlignment=Alignment.Center) { LensIcon(DeviceIcon.forGroup(device).drawable,null,Modifier.size(32.dp),tone.icon) }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(label.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                    if(compact && label.title.length<=22 && role.isNotEmpty() && role.length<=24 && !label.title.contains(role,true)) BadgeText(role)
                }
                if(room.isNotEmpty()) Text(room,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(primary.ip.contains(':')) AddressText(primary.ip)
                    else Text(primary.ip,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace,color=MaterialTheme.colorScheme.primary)
                    if(endpoints.size>1) BadgeText("+${endpoints.size-1} address"+(if(endpoints.size==2) "" else "es"))
                    if(compact) for(service in device.capabilities().take(2)) BadgeText(service)
                }
            }
            LensIcon(R.drawable.ic_ui_chevron_right,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun BadgeText(label: String) {
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=LensShape.chip) {
        Text(label,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(horizontal=8.dp,vertical=3.dp),color=MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
@Composable private fun EmptyDevices(state: UiState,filter: DeviceFilter,action: (UiAction)->Unit) {
    val filtered=state.query.isNotEmpty() || filter!=DeviceFilter.ALL
    Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        LensIcon(if(filtered) R.drawable.ic_ui_search else if(state.network==null && !state.demo) R.drawable.ic_ui_wifi_off else R.drawable.ic_ui_wifi,null,Modifier.size(40.dp),MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if(filtered) "No matching devices" else if(state.scanning) "Looking for devices…" else if(state.network==null && !state.demo) "Connect to Wi-Fi" else if(state.network?.ip?.isEmpty()==true) "IPv4 address required" else "Your devices will appear here",style=MaterialTheme.typography.titleLarge)
        Text(if(filtered) "Try another name, address or service, or clear the filters." else if(state.scanning) "Names and details appear as devices respond." else if(state.network==null && !state.demo) "Join your Wi-Fi network, then return and tap Scan Wi-Fi." else if(state.network?.ip?.isEmpty()==true) ScanPlan.IPV4_REQUIRED else "Start a fresh scan to find responding IPv4 devices. Results stay in memory for this app session.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(filtered) OutlinedButton(onClick={action(UiAction.Search(""));action(UiAction.Filter(DeviceFilter.ALL))}) { Text("Clear filters") }
        else if((state.network==null || state.network.ip.isEmpty()) && !state.demo) OutlinedButton(onClick={action(UiAction.WifiSettings)}) { Text("Open Wi-Fi settings") }
    }
}
