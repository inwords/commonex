package ru.commonex.screens

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

internal class JoinInvitationScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun openManualEntry(): JoinEventScreen {
        rule.onNodeWithTag("events_join_manual").performScrollTo().performClick()
        return JoinEventScreen()
    }
}
