package io.github.kazands.networklens

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val LocalLensMotionAllowed=staticCompositionLocalOf { false }
// Custom test semantics have no spoken label; the textual scan status owns announcements.
internal val RadarRunning=SemanticsPropertyKey<Boolean>("LensRadarRunning")
internal val RadarFinishing=SemanticsPropertyKey<Boolean>("LensRadarFinishing")

/** Observe changes while open; recomposition alone does not invalidate a remembered setting. */
@Composable internal fun rememberLensMotionAllowed(): Boolean {
    val resolver=LocalContext.current.contentResolver
    fun read(): Boolean = try {
        Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f
    } catch(_: SecurityException) { false }
    var allowed by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { allowed=read() }
        }
        var registered=false
        try {
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
            registered=true
            allowed=read()
        } catch(_: SecurityException) { allowed=false }
        onDispose { if(registered) resolver.unregisterContentObserver(observer) }
    }
    return allowed
}

internal fun radarMayFinish(report: ScanReport?,count: Int): Boolean =
    report?.complete==true && report.statusLabel!="Scan stopped" && count>0

/** The scan is the only looping motion. Hidden, paused and reduced-motion states are static. */
@Composable internal fun ScanRadar(scanning: Boolean,progress: Float?,report: ScanReport?,count: Int,
    session: Any?,modifier: Modifier=Modifier,diameter: Dp=64.dp) {
    val motion=LocalLensMotionAllowed.current
    val owner=LocalLifecycleOwner.current
    var foreground by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,_ -> foreground=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var visible by remember { mutableStateOf(false) }
    val running=scanning && motion && foreground && visible
    // Removing this branch also removes the infinite transition; no idle frame clock work.
    val phase=if(running) rememberInfiniteTransition(label="scan-radar").animateFloat(
        initialValue=0f,targetValue=1f,animationSpec=infiniteRepeatable(tween(2400,easing=LinearEasing)),label="radar-sweep") else null
    val closing=remember(session) { Animatable(1f) }
    var wasScanning by remember(session) { mutableStateOf(false) }
    LaunchedEffect(scanning,report?.finished,motion,foreground,visible,session) {
        val finished=wasScanning && !scanning && radarMayFinish(report,count)
        wasScanning=scanning
        closing.snapTo(1f)
        if(finished && motion && foreground && visible) {
            closing.snapTo(0f)
            closing.animateTo(1f,tween(360,easing=FastOutSlowInEasing))
        }
    }
    val ink=MaterialTheme.colorScheme.primary
    val rim=MaterialTheme.colorScheme.outlineVariant
    Box(modifier.size(diameter).onGloballyPositioned { visible=!it.boundsInWindow().isEmpty }
        .testTag("scan-radar").clearAndSetSemantics {
            this[RadarRunning]=running
            this[RadarFinishing]=closing.isRunning
        },contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke=2.dp.toPx()
            val r=size.minDimension/2f-stroke
            drawCircle(rim,r,style=Stroke(stroke))
            drawCircle(rim.copy(alpha=.6f),r*.62f,style=Stroke(stroke))
            phase?.let {
                val t=it.value
                val ping=(t+.5f)%1f
                drawCircle(ink.copy(alpha=(1f-ping)*.4f),r*(.3f+.7f*ping),style=Stroke(stroke))
                rotate(t*360f,center) {
                    drawLine(ink.copy(alpha=.65f),Offset(center.x,center.y-r*.44f),Offset(center.x,center.y-r),stroke,StrokeCap.Round)
                }
            }
            if(closing.value<1f) drawCircle(ink.copy(alpha=(1f-closing.value)*.5f),r*(.3f+.7f*closing.value),style=Stroke(stroke))
            if(scanning && progress!=null) drawArc(ink,-90f,360f*progress.coerceIn(0f,1f),false,
                Offset(center.x-r,center.y-r),Size(r*2,r*2),style=Stroke(stroke*1.5f,cap=StrokeCap.Round))
        }
        LensIcon(R.drawable.ic_ui_wifi,null,Modifier.size(diameter*.34f),ink)
    }
}
