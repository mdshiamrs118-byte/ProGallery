package com.pro.gallery

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun shape(color: Int, r: Float) = GradientDrawable().apply { setColor(color); cornerRadius = r }

/** All app popups: custom rounded dialogs, sheets and toasts (no system AlertDialog / Toast). */
object Ui {
    val ACCENT = Color.parseColor("#6C8CFF"); val SURFACE2 = Color.parseColor("#262A34")
    val TEXT = Color.parseColor("#F2F4F8"); val SUB = Color.parseColor("#9AA3B2"); val DANGER = Color.parseColor("#FF5C6C")

    private fun base(c: Context, title: String, msg: String?): Pair<Dialog, View> {
        val d = Dialog(c, R.style.CardDialog)
        val v = LayoutInflater.from(c).inflate(R.layout.dialog_card, null)
        v.findViewById<TextView>(R.id.dTitle).text = title
        val m = v.findViewById<TextView>(R.id.dMsg)
        if (msg == null) m.visibility = View.GONE else m.text = msg
        d.setContentView(v)
        d.window?.setLayout((c.resources.displayMetrics.widthPixels * 0.88).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        return d to v
    }

    private fun btn(c: Context, row: ViewGroup, t: String, bg: Int, fg: Int, click: () -> Unit) {
        row.addView(TextView(c).apply {
            text = t; setTextColor(fg); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(c.dp(20), c.dp(12), c.dp(20), c.dp(12)); background = shape(bg, c.dp(14).toFloat())
            setOnClickListener { click() }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = c.dp(8) })
    }

    fun dialog(c: Context, title: String, msg: String, ok: String, cancel: String? = "Cancel",
               danger: Boolean = false, onOk: () -> Unit) {
        val (d, v) = base(c, title, msg)
        val row = v.findViewById<ViewGroup>(R.id.dButtons)
        if (cancel != null) btn(c, row, cancel, SURFACE2, TEXT) { d.dismiss() }
        btn(c, row, ok, if (danger) DANGER else ACCENT, Color.WHITE) { d.dismiss(); onOk() }
        d.show()
    }

    fun sheet(c: Context, title: String, items: List<String>, checked: Int = -1, onPick: (Int) -> Unit) {
        val (d, v) = base(c, title, null)
        val body = v.findViewById<ViewGroup>(R.id.dBody)
        items.forEachIndexed { i, s ->
            body.addView(TextView(c).apply {
                text = s; textSize = 16f; setTextColor(if (i == checked) ACCENT else TEXT)
                setPadding(c.dp(16), c.dp(14), c.dp(16), c.dp(14))
                if (i == checked) background = shape(0x226C8CFF, c.dp(12).toFloat())
                setOnClickListener { d.dismiss(); onPick(i) }
            })
        }
        v.findViewById<View>(R.id.dButtons).visibility = View.GONE
        d.show()
    }

    fun details(c: Context, title: String, rows: List<Pair<String, String>>) {
        val (d, v) = base(c, title, null)
        val body = v.findViewById<ViewGroup>(R.id.dBody)
        rows.forEach { (k, value) ->
            body.addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL; setPadding(0, c.dp(6), 0, c.dp(6))
                addView(TextView(c).apply { text = k; textSize = 12f; setTextColor(SUB) })
                addView(TextView(c).apply { text = value; textSize = 15f; setTextColor(TEXT) })
            })
        }
        btn(c, v.findViewById(R.id.dButtons), "Close", ACCENT, Color.WHITE) { d.dismiss() }
        d.show()
    }

    fun toast(a: Activity, msg: String) {
        val root = a.findViewById<ViewGroup>(android.R.id.content)
        val tv = TextView(a).apply {
            text = msg; setTextColor(Color.WHITE); textSize = 14f; alpha = 0f; elevation = a.dp(8).toFloat()
            setPadding(a.dp(22), a.dp(12), a.dp(22), a.dp(12)); background = shape(Color.parseColor("#F0303542"), a.dp(24).toFloat())
        }
        root.addView(tv, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = a.dp(110) })
        tv.animate().alpha(1f).setDuration(180).start()
        tv.postDelayed({ tv.animate().alpha(0f).setDuration(220).withEndAction { root.removeView(tv) }.start() }, 1900)
    }
}
