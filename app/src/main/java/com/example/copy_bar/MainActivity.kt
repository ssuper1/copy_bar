package com.example.copy_bar

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    private lateinit var store: RuleStore
    private lateinit var body: LinearLayout
    private lateinit var previewHost: LinearLayout
    private lateinit var preview: EditText
    private lateinit var summary: TextView
    private lateinit var extensions: LinearLayout
    private var rule = MenuRule()
    private var scanning = false
    private var sampleIndex = 0
    private var previewValue = samples[0].second
    private val ink get() = getColor(R.color.page_ink)
    private val muted get() = getColor(R.color.page_muted)

    companion object {
        private val samples = listOf(
            "中文" to "选择一段文字，让常用的工具出现在身边。今天去公园散步，看看秋天的风景。",
            "English" to "Select a word to explore the tools available for this text. A small change can make everyday life easier.",
            "中英混合" to "今天学习 Android development，试试选中「翻译」或 select this sentence，比较菜单中的扩展。",
            "网址" to "https://developer.android.com/guide",
            "数字" to "1234567890  2026-11-22  3.1415926",
            "自定义" to ""
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RuleStore(this)
        rule = store.read()
        sampleIndex = savedInstanceState?.getInt("sample", 0) ?: 0
        previewValue = savedInstanceState?.getString("preview") ?: samples[sampleIndex].second
        buildPage()
        scan()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("sample", sampleIndex)
        outState.putString("preview", preview.text.toString())
        super.onSaveInstanceState(outState)
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(parent: LinearLayout, value: String, size: Float = 14f, strong: Boolean = false): TextView {
        val view = TextView(this).apply {
            text = value; textSize = size; setTextColor(if (strong) ink else muted)
            if (strong) setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        return view
    }
    private fun card(parent: LinearLayout = body): LinearLayout = column().apply {
        setPadding(dp(20), dp(20), dp(20), dp(16))
        background = GradientDrawable().apply {
            setColor(getColor(R.color.page_surface)); cornerRadius = dp(24).toFloat()
            setStroke(dp(1), getColor(R.color.page_border))
        }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
    }
    private fun button(parent: LinearLayout, title: String, onClick: () -> Unit): MaterialButton {
        val view = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = title; isAllCaps = false; cornerRadius = dp(16)
            minHeight = dp(48); setOnClickListener { onClick() }
        }
        parent.addView(view, LinearLayout.LayoutParams(-1, -2))
        return view
    }
    private fun toggle(parent: LinearLayout, title: String, detail: String, checked: Boolean,
                       checkedLabel: String = "已开启", uncheckedLabel: String = "已关闭", change: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        val words = column()
        label(words, title, 16f, true)
        if (detail.isNotEmpty()) label(words, detail, 12f)
        row.addView(words, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12) })
        val control = column().apply { gravity = Gravity.CENTER_HORIZONTAL; minimumWidth = dp(64) }
        val status = TextView(this).apply {
            text = if (checked) checkedLabel else uncheckedLabel
            textSize = 12f; setTextColor(if (checked) getColor(R.color.page_accent) else muted)
            gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0)
        }
        control.addView(SwitchMaterial(this).apply {
            contentDescription = title; isChecked = checked
            ViewCompat.setStateDescription(this, status.text)
            setOnCheckedChangeListener { view, value ->
                status.text = if (value) checkedLabel else uncheckedLabel
                status.setTextColor(if (value) getColor(R.color.page_accent) else muted)
                ViewCompat.setStateDescription(view, status.text)
                change(value)
            }
        })
        control.addView(status)
        row.addView(control)
        parent.addView(row)
    }
    private fun save(updated: MenuRule) { rule = updated; store.save("", rule) }

    private fun buildPage() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.page_background)); isFillViewport = true
        }
        body = column().apply { setPadding(dp(20), dp(24), dp(20), dp(32)) }
        scroll.addView(body)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        label(body, "TEXT TOOLS  /  文字工具", 11f, true)
        label(body, "把文字菜单，整理好。", 28f, true)
        label(body, "保留常用扩展，让每一次选择更顺手。", 14f)

        val state = card()
        label(state, "全局控制", 19f, true)
        toggle(state, "启用扩展过滤", "对使用标准文字扩展查询的应用生效", store.enabled) { store.enabled = it }
        toggle(state, "隐藏全部扩展", "优先于下面的单项选择", rule.blockAllProcessors) { save(rule.copy(blockAllProcessors = it)) }
        summary = label(state, "正在检查框架连接……", 12f)

        val demo = card()
        label(demo, "试一试真实菜单", 20f, true)
        label(demo, "切换内容，长按选词，比较不同文字的菜单。")
        val choices = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        samples.forEachIndexed { index, sample ->
            choices.addView(Chip(this).apply {
                id = View.generateViewId(); text = sample.first; isCheckable = true
                isChecked = index == sampleIndex
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        sampleIndex = index
                        previewValue = sample.second
                        rebuildPreview(previewValue)
                    }
                }
            })
        }
        demo.addView(choices)
        previewHost = column()
        demo.addView(previewHost, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(8) })
        rebuildPreview(previewValue)
        val actions = LinearLayout(this)
        demo.addView(actions)
        fun previewButton(title: String, action: () -> Unit) {
            actions.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = title; isAllCaps = false; minHeight = dp(48)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        previewButton("选择全部") {
            preview.requestFocus(); preview.selectAll()
            Toast.makeText(this, "已全选，轻点选区或长按文字打开菜单", Toast.LENGTH_SHORT).show()
        }
        previewButton("重建预览") {
            previewValue = preview.text.toString()
            rebuildPreview(previewValue)
            Toast.makeText(this, "预览已重建，请长按文字打开新菜单", Toast.LENGTH_SHORT).show()
        }
        label(demo, "可直接编辑或粘贴内容。系统会根据所选内容、语言和应用调整菜单；某些智能操作不属于文字处理扩展。", 12f)

        val list = card()
        label(list, "管理扩展", 20f, true)
        label(list, "下方开关用于屏蔽：开启＝隐藏，关闭＝不单独屏蔽。")
        label(list, "若开启全局隐藏或设置了包名屏蔽，关闭单项开关也不会恢复显示。", 12f)
        button(list, "刷新列表与框架状态") { scan() }
        button(list, "按所属应用包名屏蔽") { editPackages() }
        extensions = column()
        list.addView(extensions)

        val help = card()
        label(help, "使用提示", 16f, true)
        label(help, "LSPosed 作用域只需勾选系统框架。首次启用或更新模块后，请重启手机。", 12f)
        label(help, "修改规则后等待约一秒，再重建预览并长按文字。其他应用可能缓存列表，必要时重启该应用。复制、剪切等系统项不受影响。", 12f)
    }

    private fun rebuildPreview(value: String) {
        if (::preview.isInitialized) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(preview.windowToken, 0)
            preview.clearFocus()
        }
        previewHost.removeAllViews()
        preview = EditText(this).apply {
            setText(value); hint = "在这里输入或粘贴想测试的内容……"
            textSize = 18f; setTextColor(ink); setHintTextColor(muted)
            minLines = 3; maxLines = 8; gravity = Gravity.TOP or Gravity.START
            setPadding(dp(16), dp(16), dp(16), dp(16))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = GradientDrawable().apply {
                setColor(getColor(R.color.page_preview)); cornerRadius = dp(16).toFloat()
                setStroke(dp(1), getColor(R.color.page_border))
            }
            contentDescription = "真实文字菜单预览，可编辑并长按选择"
        }
        previewHost.addView(preview, LinearLayout.LayoutParams(-1, -2))
    }

    private fun scan() {
        if (scanning) return
        scanning = true
        summary.text = "正在检查框架连接……"
        Thread {
            val result = runCatching {
                packageManager.queryIntentActivities(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                    .putExtra(ExtensionProtocol.INVENTORY, true), PackageManager.MATCH_ALL)
            }
            runOnUiThread {
                scanning = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                val all = result.getOrElse {
                    summary.text = "扫描失败：" + it.message; return@runOnUiThread
                }
                val probe = all.firstOrNull { it.activityInfo?.name == ExtensionProtocol.PROBE_COMPONENT }
                summary.text = probe?.nonLocalizedLabel ?: "未连接系统框架 · 请检查作用域并重启手机"
                extensions.removeAllViews()
                val processors = all.filter { it.activityInfo != null && it.activityInfo.name != ExtensionProtocol.PROBE_COMPONENT }
                    .distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }
                    .sortedBy { it.loadLabel(packageManager).toString() }
                val system = processors.filter { isSystem(it.activityInfo.applicationInfo) }
                val third = processors.filterNot { isSystem(it.activityInfo.applicationInfo) }
                label(extensions, "共 " + processors.size + " 项  ·  系统 " + system.size + "  ·  第三方 " + third.size, 12f)
                listOf("系统应用" to system, "第三方应用" to third).forEach { (title, entries) ->
                    val group = column().apply {
                        setPadding(dp(14), dp(16), dp(14), dp(12))
                        background = GradientDrawable().apply {
                            setColor(getColor(R.color.page_preview)); cornerRadius = dp(18).toFloat()
                            setStroke(dp(1), getColor(R.color.page_border))
                        }
                    }
                    extensions.addView(group, LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = dp(16); bottomMargin = dp(12)
                    })
                    label(group, title + " · " + entries.size + " 项", 17f, true)
                    if (entries.isEmpty()) label(group, "暂无可查询的文字扩展", 13f)
                    entries.forEach { info ->
                        val activity = info.activityInfo
                        val component = ComponentName(activity.packageName, activity.name).flattenToString()
                        toggle(group, info.loadLabel(packageManager).toString(),
                            activity.applicationInfo.loadLabel(packageManager).toString() + "\n" + component,
                            component in rule.hiddenComponents, "已屏蔽", "未屏蔽") { hidden ->
                            save(rule.copy(hiddenComponents = if (hidden) rule.hiddenComponents + component else rule.hiddenComponents - component))
                        }
                        group.addView(View(this).apply { setBackgroundColor(getColor(R.color.page_border)) },
                            LinearLayout.LayoutParams(-1, dp(1)))
                    }
                }
            }
        }.start()
    }
    private fun isSystem(info: ApplicationInfo) =
        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private fun editPackages() {
        val input = EditText(this).apply {
            setText(rule.hiddenPackages.sorted().joinToString("\n")); minLines = 4
            gravity = Gravity.TOP; setPadding(dp(22), dp(12), dp(22), dp(12))
        }
        AlertDialog.Builder(this).setTitle("按所属应用屏蔽")
            .setMessage("每行一个完整包名，隐藏该应用提供的全部文字处理扩展。").setView(input)
            .setPositiveButton("保存") { _, _ ->
                save(rule.copy(hiddenPackages = input.text.toString().lineSequence().map { it.trim() }
                    .filter { it.isNotEmpty() }.toSet()))
            }.setNegativeButton("取消", null).show()
    }
}
