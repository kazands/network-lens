package io.github.kazands.networklens

import android.content.Context
import android.widget.LinearLayout

/** Static, paged preview: incoming discovery cannot redraw or move the user's reading position. */
internal class TroubleshootingDialog(context: Context, colors: Colors, private val document: TroubleshootingReport.Document,
                                     share: () -> Unit, private val copy: (String) -> Unit) : AppDialog(context, colors, "Troubleshooting report") {
    private var page = 0
    init {
        primary("Share full report") { share(); false }
        secondary("Close") { true }
        drawPage()
    }
    private fun drawPage() {
        body.removeAllViews()
        body.addView(paragraph("Page ${page + 1} of ${document.pages.size}. Sharing includes every page.", 14, colors.muted, false))
        val controls = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun navigation(label: String, enabled: Boolean, change: Int) {
            val button = actionButton(label, false, false)
            button.isEnabled = enabled; button.alpha = if(enabled) 1f else .5f
            button.setOnClickListener { page += change; drawPage(); scroll.scrollTo(0, 0) }
            controls.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
        }
        navigation("Previous", page > 0, -1)
        controls.addView(android.view.View(context), LinearLayout.LayoutParams(px(8), 1))
        navigation("Next", page < document.pages.lastIndex, 1)
        body.addView(controls)
        body.addView(android.view.View(context), LinearLayout.LayoutParams(1, px(8)))
        body.addView(actionButton("Copy this page", false, false).apply { setOnClickListener { copy("Network Lens report · page ${page+1}/${document.pages.size}\n" + document.pages[page]) } })
        body.addView(android.view.View(context), LinearLayout.LayoutParams(1, px(12)))
        body.addView(paragraph(document.pages[page], 13, colors.text, false).apply { setTextIsSelectable(true) })
    }
}
