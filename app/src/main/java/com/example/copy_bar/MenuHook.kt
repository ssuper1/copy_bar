package com.example.copy_bar

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.example.copy_bar.ExtensionProtocol.MODULE_PACKAGE
import com.example.copy_bar.ExtensionProtocol.INVENTORY
import com.example.copy_bar.ExtensionProtocol.PROBE_COMPONENT

/** Runs only in system_server. No Binder calls or app launches on the package-query path. */
class MenuHook : IXposedHookLoadPackage {
    private val lock = Any()
    private var lastRead = -2000L
    private var enabled = false
    private var rule = MenuRule()
    private var preferences: XSharedPreferences? = null
    private var failureLogged = false
    private val depth = ThreadLocal<Int>()

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        if (param.packageName != "android" || param.processName != "android") return
        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(p: MethodHookParam) {
                depth.set((depth.get() ?: 0) + 1)
            }
            override fun afterHookedMethod(p: MethodHookParam) {
                val current = depth.get() ?: 1
                depth.set(current - 1)
                if (current != 1 || p.hasThrowable()) return
                val intent = p.args.filterIsInstance<Intent>().firstOrNull() ?: return
                if (intent.action != Intent.ACTION_PROCESS_TEXT) return
                try { filter(p, intent) } catch (error: Throwable) {
                    if (!failureLogged) {
                        XposedBridge.log("CopyBar: extension filtering failed: $error")
                        failureLogged = true
                    }
                }
            }
        }
        var count = 0
        listOf(
            "com.android.server.pm.ComputerEngine",
            "com.android.server.pm.PackageManagerService",
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.ResolveIntentHelper"
        ).forEach { name ->
            val clazz = XposedHelpers.findClassIfExists(name, param.classLoader) ?: return@forEach
            listOf("queryIntentActivities", "queryIntentActivitiesInternal").forEach { method ->
                try {
                    count += XposedBridge.hookAllMethods(clazz, method, callback).size
                } catch (error: Throwable) {
                    XposedBridge.log("CopyBar: unavailable query hook $name.$method: $error")
                }
            }
        }
        XposedBridge.log("CopyBar: system framework query hooks installed: $count")
    }

    private fun readRule(): Pair<Boolean, MenuRule> = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRead >= 1000) {
            lastRead = now
            enabled = false
            val prefs = preferences ?: XSharedPreferences(MODULE_PACKAGE, "menu_rules").also { preferences = it }
            prefs.reload()
            // A token written by the settings app distinguishes readable configuration from empty defaults.
            if (prefs.getBoolean("config_ready", false)) {
                rule = MenuRule.fromJson(org.json.JSONObject(prefs.getString("rule:", "{}") ?: "{}"))
                enabled = prefs.getBoolean("enabled", true)
            }
        }
        enabled to rule
    }

    private fun filter(p: XC_MethodHook.MethodHookParam, intent: Intent) {
        val original = p.result ?: return
        val list = if (original is List<*>) original else XposedHelpers.callMethod(original, "getList") as? List<*> ?: return
        val (active, settings) = readRule()
        val inventory = intent.getBooleanExtra(INVENTORY, false)
        val filtered = list.filter { entry ->
            val info = entry as? ResolveInfo
            val activity = info?.activityInfo
            inventory || !active || activity == null || !settings.blocksProcessor(
                activity.packageName, ComponentName(activity.packageName, activity.name).flattenToString())
        }.toMutableList()
        if (inventory) {
            val readable = preferences?.getBoolean("config_ready", false) == true
            filtered.add(ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = MODULE_PACKAGE
                    name = PROBE_COMPONENT
                    applicationInfo = ApplicationInfo().apply { packageName = MODULE_PACKAGE }
                    exported = false
                }
                nonLocalizedLabel = if (!readable) "系统框架已加载；配置不可读，请检查 LSPosed 的共享配置支持"
                    else if (active) "系统框架已加载；配置读取正常；过滤已开启"
                    else "系统框架已加载；配置读取正常；过滤已关闭"
            })
        }
        // Never modify the package manager's original or cached result list.
        p.result = if (original is List<*>) filtered else
            XposedHelpers.newInstance(original.javaClass, filtered)
    }

}
