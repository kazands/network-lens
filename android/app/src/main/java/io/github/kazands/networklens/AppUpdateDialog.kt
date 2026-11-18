package io.github.kazands.networklens

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.TextView
import java.util.Locale

/** Progress updates keep the same views, scroll position and focused action. */
internal class AppUpdateDialog(context: Context, colors: Colors, private val flow: UpdateFlow) :
    AppDialog(context, colors, "App updates") {
    private val configuration = Configuration(context.resources.configuration)
    private val installed = row("Installed · " + flow.version())
    private val status = row("", true)
    private val releaseInfo = row("")
    private val message = row("")
    private val explanation = row("Checks GitHub’s public releases only when you ask. No scan results are sent. Keep Network Lens open while checking or downloading.")
    private var actionsFor: Pair<UpdateFlow.Step, Boolean>? = null

    fun matchesAppearance(context: Context, palette: Colors): Boolean =
        configuration == context.resources.configuration && colors == palette

    private fun row(value: String, bold: Boolean = false): TextView =
        paragraph(value, if (bold) 18 else 15, if (bold) colors.text else colors.muted, bold).also {
            it.setPadding(0, 0, 0, px(12))
            body.addView(it)
        }

    private fun value(view: TextView, value: String) {
        if (view.text.toString() != value) view.text = value
        val visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
        if (view.visibility != visibility) view.visibility = visibility
    }

    fun refresh() {
        val state = flow.view
        val release = flow.release
        val version = release?.version?.let { " · $it" }.orEmpty()
        value(status, when (state.step) {
            UpdateFlow.Step.IDLE -> "Ready to check"
            UpdateFlow.Step.CHECKING -> "Checking for updates…"
            UpdateFlow.Step.CURRENT -> "You’re up to date"
            UpdateFlow.Step.AVAILABLE -> "Update available$version"
            UpdateFlow.Step.DOWNLOADING -> "Downloading · ${state.percent}%"
            UpdateFlow.Step.READY -> "Update verified$version"
            UpdateFlow.Step.STAGING -> "Preparing the installer…"
            UpdateFlow.Step.INSTALLING -> "Confirm the update"
            UpdateFlow.Step.ERROR -> "Update couldn’t finish"
        })
        value(releaseInfo, if (release != null && state.step in setOf(UpdateFlow.Step.AVAILABLE, UpdateFlow.Step.DOWNLOADING, UpdateFlow.Step.READY))
            (if (release.preview) "Development preview" else "Public release") + " · " + String.format(Locale.ROOT, "%.1f MB", release.bytes / 1048576.0) else "")
        value(message, state.message)
        val actionState = state.step to (state.step == UpdateFlow.Step.INSTALLING && flow.canContinue())
        if (actionState == actionsFor) return
        actionsFor = actionState
        clearActions()
        when (state.step) {
            UpdateFlow.Step.IDLE, UpdateFlow.Step.ERROR -> primary("Check again") { flow.check(); false }.secondary("Close") { true }
            UpdateFlow.Step.CHECKING, UpdateFlow.Step.DOWNLOADING, UpdateFlow.Step.STAGING -> primary("Cancel") { true }
            UpdateFlow.Step.AVAILABLE -> primary("Download update") { flow.download(); false }.secondary("Later") { true }
            UpdateFlow.Step.READY -> primary("Install update") { flow.install(); false }.secondary("Later") { true }
            UpdateFlow.Step.INSTALLING -> if (actionState.second)
                primary("Continue installation") { flow.install(); false }.secondary("Cancel") { true }
                else primary("Cancel") { true }
            UpdateFlow.Step.CURRENT -> primary("Done") { true }
        }
    }
}
