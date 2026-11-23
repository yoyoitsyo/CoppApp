package com.yoyoitsyo.cooptdwall

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("wall", MODE_PRIVATE) }
    private lateinit var interval: EditText
    private lateinit var reminders: EditText
    private lateinit var sound: CheckBox
    private lateinit var vibration: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28); setBackgroundColor(Color.rgb(20, 24, 34)) }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 16f; setTextColor(Color.WHITE); setPadding(0, 14, 0, 6) }
        val title = TextView(this).apply { text = "COOPTD WALL ASSISTANT"; textSize = 22f; setTextColor(Color.WHITE) }
        root.addView(title)
        root.addView(label("Seconds per wave (estimated timing)"))
        interval = EditText(this).apply { setText(prefs.getInt("interval", 60).toString()); inputType = 2; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); hint = "e.g. 60" }
        root.addView(interval, ViewGroup.LayoutParams(-1, -2))
        root.addView(label("Reminders — one per line: Wave, Unit, Action"))
        reminders = EditText(this).apply {
            setText(prefs.getString("reminders", "72, Blue Dragon, LIB\n76, Ivy Whisper, PLACE\n85, Witch Doctor, PLACE + SSS cooldown gem"))
            minLines = 6; gravity = Gravity.TOP; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
        }
        root.addView(reminders, ViewGroup.LayoutParams(-1, 260))
        sound = CheckBox(this).apply { text = "Sound alerts"; setTextColor(Color.WHITE); isChecked = prefs.getBoolean("sound", true) }
        vibration = CheckBox(this).apply { text = "Vibration alerts"; setTextColor(Color.WHITE); isChecked = prefs.getBoolean("vibration", true) }
        root.addView(sound); root.addView(vibration)
        val save = Button(this).apply { text = "SAVE REMINDERS"; setOnClickListener { saveSettings(); Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show() } }
        root.addView(save)
        val start = Button(this).apply { text = "START FLOATING TIMER"; setOnClickListener { saveSettings(); startOverlay() } }
        root.addView(start)
        val note = TextView(this).apply {
            text = "How it works: start at Wave 0, then switch to CoopTD. The helper estimates waves from seconds-per-wave; it does not read the game's wave counter. Adjust the interval to match your run. Use the overlay +/- buttons to correct drift."
            textSize = 14f; setTextColor(Color.LTGRAY); setPadding(0, 18, 0, 0)
        }
        root.addView(note)
        setContentView(ScrollView(this).apply { addView(root) })
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4)
    }

    private fun saveSettings() {
        val sec = interval.text.toString().toIntOrNull()?.coerceIn(5, 3600) ?: 60
        prefs.edit().putInt("interval", sec).putString("reminders", reminders.text.toString()).putBoolean("sound", sound.isChecked).putBoolean("vibration", vibration.isChecked).apply()
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 9)
            Toast.makeText(this, "Allow display over other apps, then tap Start again", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Floating timer started", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }
}
