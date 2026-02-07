package com.flowpay.ussd

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service that automates multi-step USSD sessions.
 *
 * When *99# is dialed, Android shows a system USSD dialog.
 * This service:
 *   1. Reads the USSD response text from the dialog
 *   2. Gets the next reply from USSDSessionManager
 *   3. Types the reply into the dialog's input field
 *   4. Clicks "Reply" / "Send"
 *   5. Repeats until the sequence is complete
 *
 * The user sees our beautiful FlowPay UI.
 * The USSD dialogs flash briefly but are auto-handled.
 */
class USSDAccessibilityService : AccessibilityService() {

    companion object {
        // Known USSD dialog package names across Android OEMs
        val USSD_PACKAGES = setOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.samsung.android.phone",
            "com.huawei.phone",
            "com.oppo.phone",
            "com.vivo.phone",
            "com.xiaomi.phone",
            "com.oneplus.phone",
            "com.google.android.dialer",
            "com.android.dialer",
        )

        // Keywords that identify USSD dialog elements
        val REPLY_BUTTON_TEXTS = setOf("reply", "send", "ok", "submit", "जवाब")
        val CANCEL_BUTTON_TEXTS = setOf("cancel", "dismiss", "close", "रद्द")
        val INPUT_CLASS = "android.widget.EditText"

        @Volatile
        var instance: USSDAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Only process window state changes (new dialogs appearing)
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        // Only process USSD-related packages
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in USSD_PACKAGES) return

        // Only process if we have an active USSD session
        if (!USSDSessionManager.isActive) return

        val rootNode = rootInActiveWindow ?: return

        try {
            handleUSSDDialog(rootNode)
        } catch (e: Exception) {
            // Don't crash the accessibility service
            e.printStackTrace()
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * Process a USSD dialog:
     * 1. Read the response text
     * 2. Find the input field
     * 3. Enter the next reply
     * 4. Click Send/Reply
     */
    private fun handleUSSDDialog(rootNode: AccessibilityNodeInfo) {
        // Extract the USSD response text from the dialog
        val responseText = extractResponseText(rootNode)
        if (responseText.isNotEmpty()) {
            USSDSessionManager.addResponse(responseText)
        }

        // Get the next reply to send
        val nextReply = USSDSessionManager.getNextReply()

        if (nextReply != null) {
            // Find the input field and type the reply
            val inputField = findInputField(rootNode)
            if (inputField != null) {
                // Clear existing text
                val clearArgs = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                }
                inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)

                // Enter new text
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, nextReply)
                }
                inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                inputField.recycle()

                // Click Reply/Send button
                val replyButton = findReplyButton(rootNode)
                replyButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                replyButton?.recycle()
            } else {
                // No input field — might be a confirmation or final dialog
                // Click OK/Reply to proceed
                val okButton = findReplyButton(rootNode) ?: findCancelButton(rootNode)
                okButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                okButton?.recycle()
            }
        } else {
            // No more replies — session complete
            // Click OK/Cancel to dismiss the final dialog
            val dismissButton = findCancelButton(rootNode) ?: findReplyButton(rootNode)
            dismissButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            dismissButton?.recycle()

            // Mark session as complete
            USSDSessionManager.complete()
        }
    }

    /**
     * Extract the main text content from the USSD dialog.
     */
    private fun extractResponseText(node: AccessibilityNodeInfo): String {
        val texts = mutableListOf<String>()
        collectTexts(node, texts)

        // Filter out button texts and empty strings
        val allButtonTexts = REPLY_BUTTON_TEXTS + CANCEL_BUTTON_TEXTS
        return texts
            .filter { it.isNotBlank() && it.lowercase() !in allButtonTexts }
            .joinToString("\n")
    }

    /**
     * Recursively collect all text from the node tree.
     */
    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) texts.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) texts.add(it) }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, texts)
            child.recycle()
        }
    }

    /**
     * Find the text input field in the USSD dialog.
     */
    private fun findInputField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.toString() == INPUT_CLASS && node.isEnabled) {
            return AccessibilityNodeInfo.obtain(node)
        }

        // Also check for editable nodes
        if (node.isEditable && node.isEnabled) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findInputField(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    /**
     * Find the Reply/Send button.
     */
    private fun findReplyButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findButtonByText(node, REPLY_BUTTON_TEXTS)
    }

    /**
     * Find the Cancel/Dismiss button.
     */
    private fun findCancelButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findButtonByText(node, CANCEL_BUTTON_TEXTS)
    }

    /**
     * Find a button by its text content.
     */
    private fun findButtonByText(node: AccessibilityNodeInfo, keywords: Set<String>): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (node.isClickable && (keywords.any { text.contains(it) } || keywords.any { desc.contains(it) })) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findButtonByText(child, keywords)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    override fun onInterrupt() {
        // Service interrupted — cancel any active session
        if (USSDSessionManager.isActive) {
            USSDSessionManager.reset()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
