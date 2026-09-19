# Keep the Xposed entry point and its referenced classes intact.
-keep class com.autumn.douyin.liquidglass.hook.LiquidGlassHook { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }

# The status activity is patched by name at runtime.
-keep class com.autumn.douyin.liquidglass.status.ModuleStatusActivity {
    boolean isModuleActive();
}

# Xposed API is provided by the framework.
-dontwarn de.robv.android.xposed.**
-keep class de.robv.android.xposed.** { *; }
