package com.myra.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityHelperService : AccessibilityService() {

    companion object {
        var instance: AccessibilityHelperService? = null

        fun isEnabled(context: Context): Boolean {
            val pkgName = context.packageName
            val service = "$pkgName/.service.AccessibilityHelperService"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            return enabled.contains(service)
        }
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun closeCurrentApp() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun clickOnText(text: String) {
        val root = rootInActiveWindow ?: return
        val nodes = root.findAccessibilityNodeInfosByText(text)
        nodes.firstOrNull()?.let { node ->
            if (node.isClickable) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            else node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    fun typeText(text: String) {
        val root = rootInActiveWindow ?: return
        val editTexts = mutableListOf<AccessibilityNodeInfo>()
        findEditTexts(root, editTexts)
        editTexts.firstOrNull()?.let { node ->
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }

    private fun findEditTexts(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        if (node.className?.contains("EditText") == true) result.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findEditTexts(it, result) }
        }
    }

    fun scrollDown() {
        rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollUp() {
        rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }
}
