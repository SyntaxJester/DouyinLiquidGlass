package com.autumn.douyin.liquidglass.nativebar

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.autumn.douyin.liquidglass.ModuleLog

/**
 * Locates Douyin's native bottom tab bar inside an [Activity]'s view tree.
 *
 * ⚠️ THIS IS THE PART YOU MUST TUNE PER DOUYIN VERSION.
 * Douyin ships an obfuscated, frequently-changing view hierarchy, so there is
 * no stable resource id we can rely on across versions. Instead we use a set of
 * heuristics that tend to survive minor updates:
 *
 *   1. Walk the decor view depth-first.
 *   2. Keep only [ViewGroup]s that sit flush against the bottom of the screen.
 *   3. Prefer the widest such group whose height is in the "tab bar" range
 *      (roughly 40–72dp) and that has 3–6 direct children (the tabs).
 *
 * To adapt to a specific build, run once with verbose logging on
 * (`adb logcat -s DouyinLiquidGlass`), read the dumped candidate list, then
 * hard-code the matching resource-entry-name or class name in
 * [isLikelyTabBar] for a rock-solid match.
 */
object NativeBottomBarLocator {

    /** Known/guessed resource entry names for the bottom tab container. */
    private val KNOWN_ID_HINTS = listOf(
        "main_bottom_tab",
        "bottom_tab",
        "tab_container",
        "main_tab_container",
        "x_tab_container"
    )

    /** Known/guessed class-name fragments for the tab bar view. */
    private val KNOWN_CLASS_HINTS = listOf(
        "MainTabView",
        "TabBar",
        "BottomTab",
        "ScrollableTabView"
    )

    data class Match(
        val view: View,
        val boundsOnScreen: Rect,
        val score: Int,
        val reason: String
    )

    fun locate(activity: Activity, minBarDp: Float, maxBarDp: Float): Match? {
        val root = activity.window?.decorView as? ViewGroup ?: return null
        val density = activity.resources.displayMetrics.density
        val screenH = activity.resources.displayMetrics.heightPixels
        val screenW = activity.resources.displayMetrics.widthPixels
        val minBarPx = (minBarDp * density).toInt()
        val maxBarPx = (maxBarDp * density).toInt()

        val candidates = ArrayList<Match>()
        traverse(root, screenW, screenH, minBarPx, maxBarPx, density, candidates)

        if (candidates.isEmpty()) {
            ModuleLog.w("NativeBottomBarLocator: no candidate bottom bar found")
            return null
        }

        candidates.sortByDescending { it.score }
        if (ModuleLog.verbose) {
            candidates.take(5).forEach {
                ModuleLog.d(
                    "candidate score=${it.score} ${it.reason} " +
                        "bounds=${it.boundsOnScreen} cls=${it.view.javaClass.name}"
                )
            }
        }
        return candidates.first()
    }

    private fun traverse(
        view: View,
        screenW: Int,
        screenH: Int,
        minBarPx: Int,
        maxBarPx: Int,
        density: Float,
        out: MutableList<Match>
    ) {
        if (view is ViewGroup) {
            evaluate(view, screenW, screenH, minBarPx, maxBarPx, out)
            for (i in 0 until view.childCount) {
                traverse(view.getChildAt(i), screenW, screenH, minBarPx, maxBarPx, density, out)
            }
        }
    }

    private fun evaluate(
        group: ViewGroup,
        screenW: Int,
        screenH: Int,
        minBarPx: Int,
        maxBarPx: Int,
        out: MutableList<Match>
    ) {
        if (group.visibility != View.VISIBLE) return
        if (group.width <= 0 || group.height <= 0) return

        val loc = IntArray(2)
        group.getLocationOnScreen(loc)
        val left = loc[0]
        val top = loc[1]
        val right = left + group.width
        val bottom = top + group.height
        val bounds = Rect(left, top, right, bottom)

        // Must sit near the bottom edge of the screen.
        val bottomGap = screenH - bottom
        val nearBottom = bottomGap in 0..(screenH / 8)
        if (!nearBottom) return

        // Height must be in the tab-bar range.
        if (group.height !in minBarPx..maxBarPx) return

        // Must span most of the screen width.
        val widthRatio = group.width.toFloat() / screenW
        if (widthRatio < 0.75f) return

        var score = 0
        val reasons = StringBuilder()

        score += (widthRatio * 40).toInt()
        reasons.append("width=${(widthRatio * 100).toInt()}% ")

        // Closer to the bottom is better.
        score += (20 - (bottomGap * 20 / (screenH / 8).coerceAtLeast(1))).coerceAtLeast(0)

        // A tab bar usually has a handful of direct children.
        val childCount = group.childCount
        if (childCount in 3..6) {
            score += 25
            reasons.append("children=$childCount ")
        } else if (childCount in 2..8) {
            score += 8
            reasons.append("children=$childCount ")
        }

        // Resource id hints.
        val idName = resourceEntryName(group)
        if (idName != null) {
            reasons.append("id=$idName ")
            if (KNOWN_ID_HINTS.any { idName.contains(it, ignoreCase = true) }) {
                score += 60
                reasons.append("[ID_HINT] ")
            }
        }

        // Class-name hints.
        val cls = group.javaClass.name
        if (KNOWN_CLASS_HINTS.any { cls.contains(it, ignoreCase = true) }) {
            score += 40
            reasons.append("[CLASS_HINT] ")
        }

        out.add(Match(group, bounds, score, reasons.toString().trim()))
    }

    private fun resourceEntryName(view: View): String? {
        val id = view.id
        if (id == View.NO_ID) return null
        return try {
            view.resources.getResourceEntryName(id)
        } catch (_: Throwable) {
            null
        }
    }
}
