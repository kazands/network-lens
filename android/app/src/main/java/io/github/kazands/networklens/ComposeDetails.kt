@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.kazands.networklens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable internal fun AddressText(value: String,modifier: Modifier=Modifier) {
    // Whole hex groups wrap as units. Copy actions always use the original string,
    // never a display string with inserted whitespace or invisible characters.
    val parts=remember(value) { if(!value.contains(':')) listOf(value) else Regex("[^:.%]+[:.]?|[:.]|%.*").findAll(value).map { it.value }.toList() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        FlowRow(modifier.clearAndSetSemantics { text=AnnotatedString(value) },horizontalArrangement=Arrangement.Start) {
            for(part in parts) Text(part,style=MaterialTheme.typography.bodyMedium,fontFamily=FontFamily.Monospace)
        }
    }
}
@Composable internal fun DeviceScreen(device: Device,state: UiState,memory: UiMemory,action: (UiAction)->Unit) {
    val tone=lensTone(device)
    val generation=memory.detailVersion
    val scroll=remember(device.ip,generation) { ScrollState(memory.detailOffset) }
    LaunchedEffect(scroll,generation) { snapshotFlow { scroll.value }.distinctUntilChanged().collect { if(memory.detailVersion==generation) memory.detailOffset=it } }
    DisposableEffect(scroll,generation) {onDispose {if(memory.detailVersion==generation) memory.detailOffset=scroll.value}}
    val endpoints=device.endpoints().sortedWith {a,b->Address.compare(a.ip,b.ip)}
    val primary=endpoints.firstOrNull { !it.ip.contains(':') } ?: endpoints.first()
    val addressKind=if(primary.ip.contains(':')) "IPv6" else "IPv4"
    val product=device.groupProduct()?.product
    val classification=device.classification()
    val category=when { device.self->"This phone";device.gateway->"Wi-Fi gateway";classification.conflicted->"Conflicting reports";else->product?.displayType?.takeIf {it.isNotEmpty()} ?: classification.category?.label ?: "Not identified" }
    val name=device.presentation().title
    val room=device.name.takeIf { IdentityHints.specificName(it) && it!=name && product?.redundantName(it)!=true }.orEmpty()
    val matches=endpoints.map { it.identifiedProduct() }
    val shared=matches.firstOrNull()?.takeIf { first -> product==first.product && matches.all { it!=null && it.product==first.product && RecognitionCopy.explanation(it)==RecognitionCopy.explanation(first) } }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp).testTag("device-details"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Surface(shape=LensShape.hero,color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(shape=LensShape.tile,color=tone.container,modifier=Modifier.size(64.dp)) {
                    Box(contentAlignment=Alignment.Center) { LensIcon(DeviceIcon.forGroup(device).drawable,null,Modifier.size(40.dp),tone.icon) }
                }
                Text(name,style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center,modifier=Modifier.semantics { heading() })
                Text(category,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.semantics { contentDescription="Primary device type: $category" })
                if(room.isNotEmpty() && !device.self && !device.gateway) Text(room,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
                if(product?.familyOnly==true) BadgeText("Device family")
                Column(Modifier.fillMaxWidth().padding(top=4.dp)) {
                    IdentityAddressRow(addressKind,primary.ip,"copy-address") { action(UiAction.Copy("$addressKind address",primary.ip)) }
                    // A sibling endpoint may report another interface's MAC.
                    // Pair the header's identifiers only from this endpoint.
                    if(primary.mac.isNotEmpty() && !primary.hardwareAddressMayBeOtherInterface()) IdentityAddressRow("MAC",primary.mac,"copy-mac") { action(UiAction.Copy("MAC address",primary.mac)) }
                }
            }
        }
        if(device.capabilities().isNotEmpty() || endpoints.any { it.ports.isNotEmpty() }) DetailSurface {
            SectionTitle("Services & ports")
            val capabilities=device.capabilities()
            if(capabilities.isNotEmpty()) { Text("Services available",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { for(capability in capabilities) BadgeText(capability) } }
            val ports=endpoints.flatMap { it.ports }.distinct().sorted()
            if(ports.isNotEmpty()) { Text("Observed / advertised ports",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { for(port in ports) BadgeText(port.toString()) } }
            Text("A service or open port describes a capability; it does not prove the device’s physical type.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(!device.self && !device.gateway && product==null) {
            SectionTitle("About this device");Text(device.recognitionSummary(),style=MaterialTheme.typography.bodyMedium);Text(RecognitionCopy.NEXT_SCAN,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Disclosure("Show technical details","technical",memory,"Hide technical details") {
            if(shared!=null) {
                SectionTitle("Identification evidence")
                Text(RecognitionCopy.explanation(shared),style=MaterialTheme.typography.bodyMedium)
            }
            if(endpoints.size>1) Text("${endpoints.size} IP addresses grouped together",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            for((i,endpoint) in endpoints.withIndex()) {
                if(i>0) HorizontalDivider()
                val kind=if(endpoint.ip.contains(':')) "IPv6" else "IPv4"
                if(endpoints.size>1) SectionTitle("$kind · ${endpoint.ip}")
                AddressField(kind,endpoint.ip,"copy-ip:${endpoint.ip}") { action(UiAction.Copy("$kind address",endpoint.ip)) }
                if(endpoint.mac.isNotEmpty()) AddressField("MAC address",endpoint.mac,"copy-mac:${endpoint.ip}") { action(UiAction.Copy("MAC address",endpoint.mac)) }
                InfoField("Response",if(endpoint.seen) "Responded this scan" else "Not confirmed this scan")
                InfoField("Discovery evidence","${endpoint.evidence.size} observation"+(if(endpoint.evidence.size==1) "" else "s"))
                if(shared==null) endpoint.identifiedProduct()?.let { match ->
                    SectionTitle("Identification evidence")
                    Text(RecognitionCopy.explanation(match),style=MaterialTheme.typography.bodyMedium)
                }
                for((label,value) in UiDetails.fields(endpoint)) {
                    if(label!="MAC address" || endpoint.mac.isEmpty()) InfoField(label,value)
                }
                if(endpoint.audioStatus.isNotEmpty() || endpoint.audioRows.isNotEmpty()) Disclosure("Audio system"+(if(endpoints.size>1) " · ${endpoint.ip}" else ""),"audio:${endpoint.ip}",memory) {
                    if(endpoint.audioStatus.isNotEmpty()) InfoField("Lookup",endpoint.audioStatus)
                    for((label,value) in endpoint.audioRows) InfoField(label,value)
                }
            }
            Disclosure("About MAC addresses","mac-help",memory) {
                Text("Android limits MAC access. Some devices report an address through their discovery services. A reported MAC may identify another interface on the device.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Reported details come from device responses. Local recognition is an interpretation of those details. Missing values stay unknown; neither establishes ownership or security.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick={action(UiAction.CopyDevice(device))},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Copy device details") }
    }
}
@Composable private fun IdentityAddressRow(label: String,value: String,tag: String,onCopy: ()->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked=LocalDensity.current.fontScale>=1.4f || maxWidth-104.dp < 144.dp*LocalDensity.current.fontScale
        @Composable fun CopyAction() {
            IconButton(onClick=onCopy,modifier=Modifier.size(48.dp).testTag(tag)) {
                LensIcon(R.drawable.ic_ui_copy,"Copy $label address",Modifier.size(20.dp),MaterialTheme.colorScheme.primary)
            }
        }
        if(stacked) Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                CopyAction()
            }
            AddressText(value)
        } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.width(40.dp))
            AddressText(value,Modifier.weight(1f))
            CopyAction()
        }
    }
}
@Composable internal fun DetailSurface(content: @Composable ColumnScope.()->Unit) {
    Surface(shape=LensShape.card,color=MaterialTheme.colorScheme.surface) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) }
}
@Composable internal fun SectionTitle(title: String) { Text(title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics { heading() }) }
@Composable internal fun InfoField(label: String,value: String,identifier: Boolean=false) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(identifier) AddressText(value) else SelectionContainer { Text(value,style=MaterialTheme.typography.bodyMedium) }
    }
}
@Composable internal fun Disclosure(title: String,key: String,memory: UiMemory,expandedTitle: String=title,content: @Composable ColumnScope.()->Unit) {
    val open=memory.expanded[key]==true
    Column(Modifier.fillMaxWidth()) {
        // A pill clip cuts off the corners of multiline labels at enlarged text.
        TextButton(onClick={memory.expanded[key]=!open},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("disclosure:$key").semantics { stateDescription=if(open) "Expanded" else "Collapsed" },shape=androidx.compose.ui.graphics.RectangleShape,contentPadding=PaddingValues(vertical=8.dp)) {
            Text(if(open) expandedTitle else title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
            LensIcon(if(open) R.drawable.ic_ui_chevron_down else R.drawable.ic_ui_chevron_right,null,tint=MaterialTheme.colorScheme.primary)
        }
        // No height animation: expanding never drives scroll position or repeatedly detaches content.
        if(open) Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
internal object UiDetails {
    fun time(value: Long): String = if(value<=0) "Time not recorded" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(value))
    fun fields(d: Device): List<Pair<String,String>> = buildList {
        add("MAC address" to d.mac.ifEmpty {"Not available"});if(d.macSource.isNotEmpty()) add("MAC source" to d.macSource)
        val v6=d.details["ICMPv6 response time"];val echo=v6 ?: d.details["ICMP response time"]
        if(!echo.isNullOrEmpty()) add((if(v6!=null) "Response time (ICMPv6)" else "Response time (ICMP)") to echo)
        else if(d.latency>=0) add("Response time (TCP)" to "${d.latency} ms")
        if(d.name.isNotEmpty()) add("Network name" to d.name)
        val identity=listOf("Hostname" to d.hostname,"Manufacturer" to d.manufacturer,"Model" to d.model,"Service-reported type" to d.kind)
        addAll(identity.filter {it.second.isNotEmpty()})
        val missing=identity.filter {it.second.isEmpty()}.map {it.first.lowercase()}
        if(missing.isNotEmpty()) add("Not reported" to missing.joinToString(", "))
        val vendor=MacVendors.lookup(d.mac);if(vendor.isNotEmpty()) { add("MAC vendor (IEEE registry)" to vendor);add("About MAC vendors" to "The registered organization may identify an interface supplier rather than the product brand.") }
        if(d.lastSeen>0) add("Last response" to time(d.lastSeen))
        addAll(d.networkNameRows());addAll(d.metadataLookupRows());addAll(d.publicIdentityRows())
        add("How this endpoint was found" to d.evidence.joinToString("\n").ifEmpty {"Session observation"})
        add("Advertised services" to d.services.joinToString("\n").ifEmpty {"None observed"})
        add("Observed / advertised ports" to d.ports.toString())
        addAll(d.details.filterKeys {it !in setOf("ICMP response time","ICMPv6 response time")}.map {it.key to it.value})
        for(hint in d.observations()) add("Reported ${hint.field.lowercase()} · ${hint.source}" to hint.value)
        for(fact in d.classification().facts) add("Device type evidence · ${fact.source}" to fact.category.label)
        d.identifiedProduct()?.let { add("Local recognition source" to it.source);add("Recognition input" to it.input) }
    }
}

@Composable internal fun AddressField(label: String,value: String,tag: String="copy:$label",copy: ()->Unit) {
    Column {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            IconButton(onClick=copy,modifier=Modifier.testTag(tag)) {LensIcon(R.drawable.ic_ui_copy,"Copy $label"+if(label=="IPv4" || label=="IPv6") " address" else "",tint=MaterialTheme.colorScheme.primary)}
        }
        AddressText(value)
    }
}
