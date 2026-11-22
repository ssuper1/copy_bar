package com.example.copy_bar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MenuRule(
    val hiddenActions: Set<String> = emptySet(),
    val blockAllProcessors: Boolean = false,
    val hiddenPackages: Set<String> = emptySet(),
    val hiddenComponents: Set<String> = emptySet(),
    val hiddenTitles: Set<String> = emptySet()
) {
    fun blocksProcessor(pkg: String?, component: String?) =
        blockAllProcessors || pkg in hiddenPackages || component in hiddenComponents
    fun toJson() = JSONObject().apply {
        put("actions", JSONArray(hiddenActions.toList()))
        put("allProcessors", blockAllProcessors)
        put("packages", JSONArray(hiddenPackages.toList()))
        put("components", JSONArray(hiddenComponents.toList()))
        put("titles", JSONArray(hiddenTitles.toList()))
    }
    companion object {
        private fun values(json: JSONObject, key: String): Set<String> {
            val array = json.optJSONArray(key) ?: return emptySet()
            return (0 until array.length()).map { array.getString(it) }.toSet()
        }
        fun fromJson(json: JSONObject) = MenuRule(values(json, "actions"),
            json.optBoolean("allProcessors"), values(json, "packages"),
            values(json, "components"), values(json, "titles"))
    }
}

/** An explicit app rule replaces the global rule; absent app rules inherit it. */
class RuleStore(context: Context) {
    // LSPosed API 93+ redirects MODE_WORLD_READABLE module preferences to its shared path.
    @Suppress("DEPRECATION")
    private val prefs = try {
        context.getSharedPreferences("menu_rules", Context.MODE_WORLD_READABLE)
    } catch (_: SecurityException) {
        context.getSharedPreferences("menu_rules", Context.MODE_PRIVATE)
    }
    init { prefs.edit().putBoolean("config_ready", true).apply() }
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    fun hasOverride(pkg: String) = prefs.contains("rule:$pkg")
    fun read(pkg: String = ""): MenuRule {
        val raw = prefs.getString("rule:$pkg", null)
            ?: if (pkg.isNotEmpty()) prefs.getString("rule:", null) else null
        return try { MenuRule.fromJson(JSONObject(raw ?: "{}")) } catch (_: Exception) { MenuRule() }
    }
    fun save(pkg: String, rule: MenuRule) { prefs.edit().putString("rule:$pkg", rule.toJson().toString()).apply() }
    fun reset(pkg: String) { prefs.edit().remove("rule:$pkg").apply() }
}
