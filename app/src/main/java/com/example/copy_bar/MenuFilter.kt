package com.example.copy_bar

import android.content.Intent
import android.view.Menu
import android.view.MenuItem

object MenuFilter {
    val actions = linkedMapOf(
        "cut" to "剪切", "copy" to "复制", "paste" to "粘贴", "selectAll" to "全选",
        "shareText" to "分享", "pasteAsPlainText" to "粘贴为纯文本", "textAssist" to "智能操作"
    )
    fun actionKey(id: Int): String? = when (id) {
        android.R.id.cut -> "cut"
        android.R.id.copy -> "copy"
        android.R.id.paste -> "paste"
        android.R.id.selectAll -> "selectAll"
        android.R.id.shareText -> "shareText"
        android.R.id.pasteAsPlainText -> "pasteAsPlainText"
        android.R.id.textAssist -> "textAssist"
        else -> null
    }
    fun shouldHide(item: MenuItem, rule: MenuRule): Boolean {
        if (actionKey(item.itemId) in rule.hiddenActions) return true
        if (item.title?.toString()?.trim() in rule.hiddenTitles) return true
        val intent = item.intent
        if (intent?.action == Intent.ACTION_PROCESS_TEXT) {
            val component = intent.component
            return rule.blocksProcessor(component?.packageName ?: intent.`package`, component?.flattenToString())
        }
        return false
    }
    fun apply(menu: Menu, rule: MenuRule) {
        // Preserve the host's ordering, callbacks and duplicate menu IDs.
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            item.subMenu?.let { apply(it, rule) }
            if (shouldHide(item, rule)) item.isVisible = false
        }
    }
}
