@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package io.github.kazands.networklens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable internal fun NetworkLensApp(state: UiState, memory: UiMemory, action: (UiAction)->Unit) {
    PrivateUiRoot()
    PrivateTextInput {
    LensTheme(state.dark) {
        val snackbars = remember { SnackbarHostState() }
        LaunchedEffect(memory.notice) { memory.notice?.let { snackbars.showSnackbar(it); memory.notice=null } }
        BackHandler(state.modal != null || state.settings || state.selected != null) { action(UiAction.Back) }
        Scaffold(containerColor=MaterialTheme.colorScheme.background,
            topBar={ LensHeader(state,memory,action) },snackbarHost={ SnackbarHost(snackbars) },contentWindowInsets=WindowInsets.safeDrawing) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().semantics { testTagsAsResourceId=true }) {
                when {
                    !state.ready -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("cleanup"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("Finish removing older app data",style=MaterialTheme.typography.headlineMedium)
                        Text("Android could not remove data left by an earlier version. Scanning is paused until cleanup succeeds.")
                        Text("Try cleanup again. If it still fails, open Android settings, choose Storage & cache, then Clear storage. This also resets Android-managed app permissions.")
                        Button(onClick={action(UiAction.FinishCleanup)}) { Text("Retry cleanup") }
                        OutlinedButton(onClick={action(UiAction.AndroidSettings)}) {Text("Open Android settings")}
                    }
                    state.settings -> SettingsScreen(state,memory,action)
                    state.selected != null && maxWidth >= 840.dp -> Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(.48f)) { DevicesScreen(state,memory,action) }
                        VerticalDivider()
                        Box(Modifier.weight(.52f)) { DeviceScreen(state.selected,state,memory,action) }
                    }
                    state.selected != null -> DeviceScreen(state.selected,state,memory,action)
                    else -> DevicesScreen(state,memory,action)
                }
            }
        }
        LensModal(state,memory,action)
    }
    }
}
@Composable private fun LensHeader(state: UiState,memory: UiMemory,action: (UiAction)->Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(horizontal=12.dp).heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically) {
        if(state.settings || state.selected != null) IconButton(onClick={action(UiAction.Back)},modifier=Modifier.testTag("back")) {
            LensIcon(R.drawable.ic_ui_back,if(state.settings && state.selected != null) "Back to device details" else "Back to devices",tint=MaterialTheme.colorScheme.primary)
        } else Image(painterResource(R.drawable.network_lens_logo),null,Modifier.padding(4.dp).size(40.dp))
        Text(if(state.settings) "Settings" else if(state.selected != null) "Device details" else "Network Lens",
            style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f).padding(horizontal=8.dp).semantics { heading() })
        IconButton(onClick={action(UiAction.Theme)},modifier=Modifier.testTag("theme").semantics { contentDescription=if(state.dark) "Switch to light mode" else "Switch to dark mode" }) {
            LensIcon(if(state.dark) R.drawable.ic_ui_sun else R.drawable.ic_ui_moon,null,tint=MaterialTheme.colorScheme.primary)
        }
        if(state.ready) Box {
            IconButton(onClick={memory.menuOpen=true},modifier=Modifier.testTag("options")) { LensIcon(R.drawable.ic_ui_more,"Options",tint=MaterialTheme.colorScheme.primary) }
            DropdownMenu(expanded=memory.menuOpen,onDismissRequest={memory.menuOpen=false},containerColor=MaterialTheme.colorScheme.surfaceContainerHigh) {
                PrivateUiRoot()
                for((label,event) in listOf("Settings" to UiAction.Settings,"Wi-Fi details" to UiAction.NetworkInfo,"Check for updates" to UiAction.Updates)) {
                    DropdownMenuItem(text={Text(label)},onClick={memory.menuOpen=false;action(event)})
                }
            }
        }
    }
}
@Composable internal fun LensIcon(resource: Int,label: String?,modifier: Modifier=Modifier,tint: Color=MaterialTheme.colorScheme.onSurface) {
    Icon(painterResource(resource),contentDescription=label,modifier=modifier.size(24.dp),tint=tint)
}
/** Keep device-name searches and range input out of keyboard learning. */
@Composable private fun PrivateTextInput(content: @Composable ()->Unit) {
    InterceptPlatformTextInput(interceptor={ request,next ->
        next.startInputMethod(object: PlatformTextInputMethodRequest {
            override fun createInputConnection(outAttributes: EditorInfo): android.view.inputmethod.InputConnection {
                val connection=request.createInputConnection(outAttributes)
                outAttributes.imeOptions=outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                return connection
            }
        })
    },content=content)
}
