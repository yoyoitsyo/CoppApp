package com.yoyoitsyo.cooptdwall

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.*
import android.media.AudioManager
import android.media.ToneGenerator

class OverlayService : Service() {
    companion object { const val ACTION_START = "START"; const val ACTION_STOP = "STOP"; private const val CHANNEL = "cooptd_timer" }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var wave = 0
    private var startedAt = 0L
    private var pausedElapsed = 0L
    private var paused = false
    private var lastAlert = -1
    private val prefs by lazy { getSharedPreferences("wall", MODE_PRIVATE) }
    private val tick = object : Runnable { override fun run() { if (!paused) { val interval = (prefs.getInt("interval", 60) * 1000L).coerceAtLeast(5000L); wave = (pausedElapsed + SystemClock.elapsedRealtime() - startedAt).div(interval).toInt(); checkReminder() }; refresh(); handler.postDelayed(this, 1000) } }

    override fun onCreate() { super.onCreate(); windowManager = getSystemService(WINDOW_SERVICE) as WindowManager; createChannel() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        startForeground(17, notification("Timer ready"))
        if (overlay == null) { wave = 0; pausedElapsed = 0; startedAt = SystemClock.elapsedRealtime(); paused = false; createOverlay(); handler.post(tick) }
        return START_STICKY
    }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) { val nm = getSystemService(NotificationManager::class.java); nm.createNotificationChannel(NotificationChannel(CHANNEL, "CoopTD timer", NotificationManager.IMPORTANCE_LOW)) } }
    private fun notification(msg: String): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b.setContentTitle("CoopTD Wall Assistant").setContentText(msg).setSmallIcon(android.R.drawable.ic_media_play).addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop).build()
    }
    private fun createOverlay() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12); setBackgroundColor(Color.rgb(25, 29, 42)) }
        val head = TextView(this).apply { text = "COOPTD WALL ASSIST  ⋮"; setTextColor(Color.WHITE); textSize = 13f }
        val waveText = TextView(this).apply { textSize = 25f; textColor(Color.WHITE) }
        val nextText = TextView(this).apply { textSize = 14f; textColor(Color.rgb(130, 210, 255)) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(s: String, f: () -> Unit) = Button(this).apply { text = s; textSize = 12f; setPadding(4, 0, 4, 0); setOnClickListener { f() } }
        row.addView(button("−") { wave = (wave - 1).coerceAtLeast(0); rebase() }, LinearLayout.LayoutParams(0, 44, 1f))
        lateinit var pauseButton: Button
        pauseButton = button("PAUSE") { togglePause(); pauseButton.text = if (paused) "RESUME" else "PAUSE" }
        row.addView(pauseButton, LinearLayout.LayoutParams(0, 44, 2f))
        row.addView(button("+") { wave++; rebase() }, LinearLayout.LayoutParams(0, 44, 1f))
        box.addView(head); box.addView(waveText); box.addView(nextText); box.addView(row)
        head.setOnTouchListener(object : View.OnTouchListener { var x = 0f; var y = 0f; var px = 0; var py = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean { val lp = overlay?.layoutParams as? WindowManager.LayoutParams ?: return false; when (e.action) { MotionEvent.ACTION_DOWN -> { x=e.rawX; y=e.rawY; px=lp.x; py=lp.y; return true }; MotionEvent.ACTION_MOVE -> { lp.x=px+(e.rawX-x).toInt(); lp.y=py+(e.rawY-y).toInt(); windowManager.updateViewLayout(box,lp); return true } }; return false }
        })
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 12; y = 130 }
        windowManager.addView(box, params); overlay = box
        box.tag = Pair(waveText, nextText)
    }
    private fun refresh() { val v = overlay ?: return; val pair = v.tag as? Pair<*, *> ?: return; val w = pair.first as TextView; val n = pair.second as TextView; w.text = "WAVE $wave"; val upcoming = reminders().filter { it.first >= wave }.minByOrNull { it.first }; n.text = upcoming?.let { "NEXT: ${it.second} · WAVE ${it.first}" } ?: "No upcoming reminders"; val b = v.findViewWithTag<Button>("unused"); if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).notify(17, notification("Wave $wave")) }
    private fun reminders(): List<Pair<Int,String>> = prefs.getString("reminders", "").orEmpty().lines().mapNotNull { line -> val p=line.split(",", limit=3); val num=p.getOrNull(0)?.trim()?.toIntOrNull(); if (num != null && p.size >= 2) num to p.drop(1).joinToString(",").trim() else null }.sortedBy { it.first }
    private fun checkReminder() { if (wave == lastAlert) return; val match = reminders().firstOrNull { it.first == wave } ?: return; lastAlert = wave; if (prefs.getBoolean("sound", true)) try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 250) } catch (_: Exception) {}; if (prefs.getBoolean("vibration", true)) { val vib = getSystemService(VIBRATOR_SERVICE) as Vibrator; if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vib.vibrate(350) }; val nm=getSystemService(NotificationManager::class.java); nm.notify(18, notification("REMINDER: ${match.second} — Wave $wave")) }
    private fun rebase() { pausedElapsed = wave.toLong() * prefs.getInt("interval",60) * 1000L; startedAt = SystemClock.elapsedRealtime(); lastAlert = -1; refresh() }
    private fun togglePause() { if (!paused) { pausedElapsed += SystemClock.elapsedRealtime()-startedAt; paused=true } else { startedAt=SystemClock.elapsedRealtime(); paused=false }; refresh() }
    override fun onDestroy() { handler.removeCallbacks(tick); overlay?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }; overlay=null; super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}
