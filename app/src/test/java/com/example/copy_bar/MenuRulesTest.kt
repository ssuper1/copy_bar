package com.example.copy_bar

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MenuRulesTest {
    @Test fun serializationPreservesAllFilters() {
        val rule = MenuRule(setOf("copy", "shareText"), true, setOf("example.app"),
            setOf("example.app/example.app.Translate"), setOf("翻译", "搜索"))
        assertEquals(rule, MenuRule.fromJson(JSONObject(rule.toJson().toString())))
    }
    @Test fun defaultRuleKeepsUnknownAndImplicitProcessors() {
        assertFalse(MenuRule().blocksProcessor(null, null))
        assertFalse(MenuRule().blocksProcessor("example.app", "example.app/Translate"))
    }
    @Test fun componentRuleDoesNotBlockSiblingComponent() {
        val rule = MenuRule(hiddenComponents = setOf("example.app/Translate"))
        assertTrue(rule.blocksProcessor("example.app", "example.app/Translate"))
        assertFalse(rule.blocksProcessor("example.app", "example.app/Search"))
    }
    @Test fun packageRuleBlocksEveryComponentFromThatPackage() {
        val rule = MenuRule(hiddenPackages = setOf("example.app"))
        assertTrue(rule.blocksProcessor("example.app", "example.app/Translate"))
        assertTrue(rule.blocksProcessor("example.app", "example.app/Search"))
        assertFalse(rule.blocksProcessor("other.app", "other.app/Search"))
    }
    @Test fun blockAllIncludesImplicitProcessors() {
        assertTrue(MenuRule(blockAllProcessors = true).blocksProcessor(null, null))
    }
    @Test fun emptyJsonUsesSafeDefaults() {
        assertEquals(MenuRule(), MenuRule.fromJson(JSONObject("{}")))
    }
}
