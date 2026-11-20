package io.github.kazands.networklens.test

import android.app.Instrumentation

/** Migrated from View inspection to real Compose semantics and layout checks. */
internal object RecognitionReportChecks {
    fun run(test: Instrumentation,check: (Boolean,String)->Unit) = ComposeUiChecks.run(test,check,"compose-reports")
}
