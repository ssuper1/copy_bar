package com.example.copy_bar

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenuFilterDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun frameworkFloatingActionModeEntryExists() {
        val mode = Class.forName("com.android.internal.view.FloatingActionMode")
        assertTrue(mode.declaredMethods.any { it.name == "invalidate" })
    }

    @Test fun filtersSystemItemsWithoutChangingOtherActions() {
        val menu = MenuBuilder(context)
        val copy = menu.add(0, android.R.id.copy, 0, "复制")
        val paste = menu.add(0, android.R.id.paste, 1, "粘贴")
        MenuFilter.apply(menu, MenuRule(hiddenActions = setOf("copy")))
        assertFalse(copy.isVisible)
        assertTrue(paste.isVisible)
        assertEquals(2, menu.size())
    }

    @Test fun filtersProcessorsByComponentIncludingSubmenusAndDuplicateIds() {
        val menu = MenuBuilder(context)
        val component = ComponentName("example.app", "example.app.Translate")
        val translate = menu.add(0, 0, 0, "翻译").setIntent(
            Intent(Intent.ACTION_PROCESS_TEXT).setComponent(component))
        val search = menu.add(0, 0, 1, "搜索").setIntent(
            Intent(Intent.ACTION_PROCESS_TEXT).setComponent(ComponentName("example.app", "example.app.Search")))
        val child = menu.addSubMenu("更多").add("自定义翻译")
        MenuFilter.apply(menu, MenuRule(hiddenComponents = setOf(component.flattenToString()), hiddenTitles = setOf("自定义翻译")))
        assertFalse(translate.isVisible)
        assertTrue(search.isVisible)
        assertFalse(child.isVisible)
    }

    @Test fun appOverrideAndReadOnlyProvider() {
        val pkg = "com.example.copy_bar.test.fixture"
        val store = RuleStore(context)
        val hadOverride = store.hasOverride(pkg)
        val original = store.read(pkg)
        try {
            store.save(pkg, MenuRule(hiddenActions = setOf("copy")))
            val data = context.contentResolver.call(Uri.parse("content://com.example.copy_bar.rules"), "getRule", pkg, null)
            assertEquals(setOf("copy"), MenuRule.fromJson(org.json.JSONObject(data!!.getString("rule")!!)).hiddenActions)
            assertNull(context.contentResolver.call(Uri.parse("content://com.example.copy_bar.rules"), "setRule", pkg, null))
            store.reset(pkg)
            assertEquals(store.read(), store.read(pkg))
        } finally {
            if (hadOverride) store.save(pkg, original) else store.reset(pkg)
        }
    }
}
