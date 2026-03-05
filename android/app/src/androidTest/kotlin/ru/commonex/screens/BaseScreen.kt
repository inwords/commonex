package ru.commonex.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText

@OptIn(ExperimentalTestApi::class)
internal abstract class BaseScreen {

    context(rule: ComposeTestRule)
    fun waitForElementWithText(
        text: String,
        count: Int = 1,
        timeout: Long = 10000,
    ) {
        if (count == 1) {
            rule.waitUntilAtLeastOneExists(hasText(text), timeout)
        } else {
            rule.waitUntilNodeCount(hasText(text), count, timeout)
        }
    }

    context(rule: ComposeTestRule)
    fun waitForElementWithTextDoesNotExist(
        text: String,
        timeout: Long = 10000
    ) {
        rule.waitUntilDoesNotExist(hasText(text), timeout)
    }

    context(rule: ComposeTestRule)
    fun waitForElementWithTag(
        tag: String,
        timeout: Long = 10000,
    ) {
        rule.waitUntilAtLeastOneExists(hasTestTag(tag), timeout)
    }

    context(rule: ComposeTestRule)
    fun waitForElementWithTagDoesNotExist(
        tag: String,
        timeout: Long = 10000,
    ) {
        rule.waitUntilDoesNotExist(hasTestTag(tag), timeout)
    }

    context(rule: ComposeTestRule)
    fun assertElementWithTextExists(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    context(rule: ComposeTestRule)
    fun assertElementsWithTextCount(text: String, count: Int) {
        rule
            .onAllNodesWithText(text, substring = true)
            .assertCountEquals(count)
    }
}
