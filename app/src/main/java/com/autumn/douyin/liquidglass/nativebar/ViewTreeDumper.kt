package com.autumn.douyin.liquidglass.nativebar

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.autumn.douyin.liquidglass.ModuleLog

/**
 * Dumps the bottom slice of an activity's view tree to logcat so you can find
 * the real resource-entry-name / class of Douyin's tab bar for a given version.
 *
 * Enable by watching:  adb logcat -s DouyinLiquidGlass
 * It only prints views whose bottom edge sits in the lowest 25% of the screen,
 * to keep the noise down.
 */
object ViewTreeDumper {

    fun dumpBottomRegion(activity: Activity) {
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val screenH = activity.resources.displayMetrics.heightPixels
        val threshold = screenH * 0.75f
        ModuleLog.d("===== view tree dump for ${activity.javaClass.name} (screenH=$screenH) =====")
        walk(decor, 0, screenH, threshold)
        ModuleLog.d("===== end dump =====")
    }

    private fun walk(view: View, depth: Int, screenH: Int, threshold: Float) {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val top = loc[1]
        val bottom = top + view.height

        // Only log views that reach into the bottom region.
        if (bottom >= threshold && view.width > 0 && view.height > 0) {
            val id = idName(view)
            val childCount = (view as? ViewGroup)?.childCount ?: 0
            val indent = "  ".repeat(depth)
            ModuleLog.d(
                "$indent${view.javaClass.simpleName} id=$id " +
                    "top=$top bottom=$bottom h=${view.height} w=${view.width} " +
                    "children=$childCount vis=${view.visibility}"
            )
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                walk(view.getChildAt(i), depth + 1, screenH, threshold)
            }
        }
    }

    private fun idName(view: View): String {
        val id = view.id
        if (id == View.NO_ID) return "NO_ID"
        return try {
            "${view.resources.getResourceEntryName(id)}"
        } catch (_: Throwable) {
            "0x${Integer.toHexString(id)}"
        }
    }
}
