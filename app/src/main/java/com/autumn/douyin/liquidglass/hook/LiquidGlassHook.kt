package com.autumn.douyin.liquidglass.hook

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import com.autumn.douyin.liquidglass.ModuleLog
import com.autumn.douyin.liquidglass.settings.ModuleSettings
import com.autumn.douyin.liquidglass.ui.OverlayController
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Collections
import java.util.WeakHashMap

/**
 * Xposed entry point. LSPosed calls [handleLoadPackage] for every process it
 * injects; we bail out unless we're inside Douyin's main process, then attach a
 * lifecycle callback that installs / removes a glass overlay per activity.
 */
class LiquidGlassHook : IXposedHookLoadPackage {

    private val controllers =
        Collections.synchronizedMap(WeakHashMap<Activity, OverlayController>())

    @Volatile
    private var settings: ModuleSettings = ModuleSettings.DEFAULT

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Self-detection: make the status screen report "active" when the
        // module's own app is loaded under Xposed.
        if (lpparam.packageName == SELF_PACKAGE) {
            patchSelfStatus(lpparam)
            return
        }

        if (lpparam.packageName != DOUYIN_PACKAGE) return
        if (lpparam.processName != DOUYIN_PACKAGE) return

        // AGSL / RuntimeShader requires API 33+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ModuleLog.w("Device is below Android 13; liquid glass shaders unavailable.")
            return
        }

        ModuleLog.i("Douyin loaded, installing liquid glass hook (process=${lpparam.processName})")

        // Register activity lifecycle callbacks on the app instance. We hook
        // Application.onCreate via the classloader's Instrumentation-free path:
        // simplest reliable approach is to wait for the first Activity and pull
        // the Application from it.
        registerViaActivityThread(lpparam)
    }

    private fun registerViaActivityThread(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            ActivityCallbackBridge.install(
                lpparam,
                onResumed = { activity -> onActivityResumed(activity) },
                onPaused = { activity -> onActivityPaused(activity) }
            )
        } catch (t: Throwable) {
            ModuleLog.e("failed to register lifecycle callbacks", t)
        }
    }

    @Suppress("NewApi")
    private fun onActivityResumed(activity: Activity) {
        if (!settings.enabled) return
        if (controllers.containsKey(activity)) {
            controllers[activity]?.reposition()
            return
        }
        try {
            val controller = OverlayController(activity, settings)
            controllers[activity] = controller
            controller.install()
        } catch (t: Throwable) {
            ModuleLog.e("install overlay failed for ${activity.javaClass.name}", t)
        }
    }

    private fun onActivityPaused(activity: Activity) {
        controllers.remove(activity)?.remove()
    }

    private fun patchSelfStatus(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            de.robv.android.xposed.XposedHelpers.findAndHookMethod(
                "com.autumn.douyin.liquidglass.status.ModuleStatusActivity",
                lpparam.classLoader,
                "isModuleActive",
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
        } catch (_: Throwable) {
            // Status screen just shows "inactive"; not fatal.
        }
    }

    companion object {
        const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
        const val SELF_PACKAGE = "com.autumn.douyin.liquidglass"
    }
}

/**
 * Bridges classic-Xposed to Android's [Application.ActivityLifecycleCallbacks].
 * We hook Application.onCreate to grab the app instance, then register a
 * standard lifecycle callback — no per-method view hooking needed.
 */
private object ActivityCallbackBridge {

    fun install(
        lpparam: XC_LoadPackage.LoadPackageParam,
        onResumed: (Activity) -> Unit,
        onPaused: (Activity) -> Unit
    ) {
        de.robv.android.xposed.XposedHelpers.findAndHookMethod(
            Application::class.java.name,
            lpparam.classLoader,
            "onCreate",
            object : de.robv.android.xposed.XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.thisObject as? Application ?: return
                    registerCallbacks(app, onResumed, onPaused)
                }
            }
        )
    }

    private fun registerCallbacks(
        app: Application,
        onResumed: (Activity) -> Unit,
        onPaused: (Activity) -> Unit
    ) {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, s: Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityResumed(a: Activity) = onResumed(a)
                override fun onActivityPaused(a: Activity) = onPaused(a)
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, s: Bundle) {}
                override fun onActivityDestroyed(a: Activity) = onPaused(a)
            }
        )
        ModuleLog.i("ActivityLifecycleCallbacks registered")
    }
}
