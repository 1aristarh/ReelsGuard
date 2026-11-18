package com.petar.reelsguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelsGuardService : AccessibilityService() {

    private val timer by lazy { Store.get(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false
    private var lastExitAt = 0L
    private var exitStep = 0

    private val tick = object : Runnable {
        override fun run() {
            if (check()) handler.postDelayed(this, TICK_MS) else ticking = false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!ticking && check()) {
            ticking = true
            handler.postDelayed(tick, TICK_MS)
        }
    }

    override fun onInterrupt() {}

    /** Returns true while a Reels/Shorts surface is on screen and not blocked. */
    private fun check(): Boolean {
        if (!Store.lock(this).approved) return false // the guard stays off until the setup is approved
        val root = rootInActiveWindow
        val surface = root?.let { onReels(it) }
        if (surface == null) {
            timer.onLeftReels()
            exitStep = 0
            return false
        }
        val now = System.currentTimeMillis()
        Store.lastHit = surface; Store.lastHitAt = now
        val blocked = timer.onReelsVisible(now)
        Store.save(this, timer)
        if (blocked) {
            exitReels(root.packageName?.toString().orEmpty(), now)
            return false
        }
        return true
    }

    /**
     * Gets the user out of Reels with the gentlest move that works for the app, escalating
     * (swipe -> back -> home) if the previous attempt left them on Reels. Only runs while a
     * Reels/Shorts surface is actually on screen, so the rest of the app is left alone.
     */
    private fun exitReels(pkg: String, now: Long) {
        if (now - lastExitAt < EXIT_GAP_MS) return // the last attempt is still animating
        lastExitAt = now
        val plan = EXIT_PLANS[pkg] ?: listOf(Exit.HOME)
        val step = plan[minOf(exitStep, plan.size - 1)]
        if (exitStep == 0) {
            val msg = if (timer.blockAll) "Reels are blocked" else "Reels time is up"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        exitStep++
        when (step) {
            Exit.SWIPE_LEFT -> swipe(left = true)
            Exit.SWIPE_RIGHT -> swipe(left = false)
            Exit.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            Exit.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun swipe(left: Boolean) {
        val m = resources.displayMetrics
        val y = m.heightPixels * 0.5f
        val from = m.widthPixels * (if (left) 0.85f else 0.15f)
        val to = m.widthPixels * (if (left) 0.15f else 0.85f)
        val path = Path().apply { moveTo(from, y); lineTo(to, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 250)).build()
        dispatchGesture(g, null, null)
    }

    private enum class Exit { SWIPE_LEFT, SWIPE_RIGHT, BACK, HOME }

    /** Returns the matched surface's name (for display), or null if this isn't Reels/Shorts. */
    private fun onReels(root: AccessibilityNodeInfo): String? {
        val pkg = root.packageName?.toString() ?: return null
        val app = APP_NAMES[pkg] ?: pkg
        if (pkg in TIKTOK) return app
        // 1) known view IDs
        for (id in IDS[pkg].orEmpty()) {
            if (root.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) return app
        }
        // 2) fallback: the selected bottom tab is labelled Shorts / Reels (survives ID renames)
        if (selectedTab(root, TAB_LABELS)) return app
        if (BuildConfig.DEBUG) dumpIds(root, pkg)
        return null
    }

    private fun selectedTab(n: AccessibilityNodeInfo, labels: Set<String>): Boolean {
        if (n.isSelected && n.viewIdResourceName == "com.instagram.android:id/clips_tab") return true
        if (n.isSelected) {
            val t = n.text?.toString() ?: n.contentDescription?.toString()
            if (t != null && labels.any { t.equals(it, ignoreCase = true) }) return true
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            if (selectedTab(c, labels)) return true
        }
        return false
    }

    /** Debug aid: logs view IDs when nothing matched, so new IDs can be found quickly. */
    private fun dumpIds(n: AccessibilityNodeInfo, pkg: String, depth: Int = 0) {
        if (depth > 12) return
        n.viewIdResourceName?.let { Log.d("ReelsGuard", "$pkg $it") }
        for (i in 0 until n.childCount) n.getChild(i)?.let { dumpIds(it, pkg, depth + 1) }
    }

    companion object {
        private const val TICK_MS = 5_000L
        private const val EXIT_GAP_MS = 1_500L
        // How to leave Reels per app, tried in order (the last step repeats). Update if an app changes.
        // Instagram: swiping left leaves the Reels tab but stays in the app. TikTok has no non-Reels
        // surface to go back to, and YouTube Shorts exits with Back, so those fall back accordingly.
        private val EXIT_PLANS = mapOf(
            "com.instagram.android" to listOf(Exit.SWIPE_LEFT, Exit.BACK, Exit.HOME),
            "com.google.android.youtube" to listOf(Exit.BACK, Exit.HOME),
            "com.zhiliaoapp.musically" to listOf(Exit.HOME),
            "com.ss.android.ugc.trill" to listOf(Exit.HOME),
        )
        private val TIKTOK = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
        private val TAB_LABELS = setOf("Shorts", "Reels")
        private val APP_NAMES = mapOf(
            "com.google.android.youtube" to "YouTube Shorts",
            "com.instagram.android" to "Instagram Reels",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.ss.android.ugc.trill" to "TikTok",
        )
        // Keep all app-specific IDs here; update this list if an app update breaks detection.
        private val IDS = mapOf(
            "com.google.android.youtube" to listOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/shorts_container",
            ),
            "com.instagram.android" to listOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/clips_video_container",
                "com.instagram.android:id/clips_swipe_refresh_container",
            ),
        )
    }
}

