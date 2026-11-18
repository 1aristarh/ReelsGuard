package com.petar.reelsguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelsGuardService : AccessibilityService() {

    private val timer by lazy { Store.get(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false

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
        val root = rootInActiveWindow
        val surface = root?.let { onReels(it) }
        if (surface == null) {
            timer.onLeftReels()
            return false
        }
        val now = System.currentTimeMillis()
        Store.lastHit = surface; Store.lastHitAt = now
        val blocked = timer.onReelsVisible(now)
        Store.save(this, timer)
        if (blocked) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            try {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
            return false
        }
        return true
    }

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

