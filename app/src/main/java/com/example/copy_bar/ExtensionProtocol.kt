package com.example.copy_bar

/** Kept separate so the settings activity never loads Xposed-only classes. */
object ExtensionProtocol {
    const val MODULE_PACKAGE = "com.example.copy_bar"
    const val INVENTORY = "com.example.copy_bar.INVENTORY"
    const val PROBE_COMPONENT = "com.example.copy_bar.FrameworkProbe"
}
