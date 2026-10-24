package com.inwords.expenses.feature.share.api

import android.content.Context
import android.content.Intent
import expenses.shared.feature.share.generated.resources.Res
import expenses.shared.feature.share.generated.resources.share_chooser_title
import org.jetbrains.compose.resources.getString

actual class ShareManager internal constructor(
    private val context: Context
) {

    actual suspend fun shareText(subject: String, fullText: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (subject.isNotBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
            putExtra(Intent.EXTRA_TEXT, fullText)
        }

        val chooserTitle = getString(Res.string.share_chooser_title)
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
