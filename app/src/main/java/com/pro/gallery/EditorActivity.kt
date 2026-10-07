package com.pro.gallery

import android.content.ContentValues
import android.graphics.*
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import android.app.Activity

class EditorActivity : Activity() {
    private lateinit var orig: Bitmap
    private lateinit var base: Bitmap
    private val undo = ArrayList<Bitmap>()
    private var br = 0; private var ct = 100; private var sat = 100; private var fi = 0
    private var dirty = false; private var tabIdx = 0
    private lateinit var preview: ImageView
    private lateinit var tools: LinearLayout
    private lateinit var tabs: List<TextView>

    private val filters: List<Pair<String, FloatArray?>> = listOf(
        "Original" to null,
        "Mono" to ColorMatrix().apply { setSaturation(0f) }.array,
        "Noir" to floatArrayOf(.42f, .84f, .14f, 0f, -40f, .42f, .84f, .14f, 0f, -40f, .42f, .84f, .14f, 0f, -40f, 0f, 0f, 0f, 1f, 0f),
        "Sepia" to floatArrayOf(.393f, .769f, .189f, 0f, 0f, .349f, .686f, .168f, 0f, 0f, .272f, .534f, .131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
        "Warm" to floatArrayOf(1.1f, 0f, 0f, 0f, 10f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, .9f, 0f, -10f, 0f, 0f, 0f, 1f, 0f),
        "Cool" to floatArrayOf(.9f, 0f, 0f, 0f, -10f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.1f, 0f, 10f, 0f, 0f, 0f, 1f, 0f),
        "Vivid" to ColorMatrix().apply { setSaturation(1.6f) }.array,
        "Fade" to floatArrayOf(.9f, 0f, 0f, 0f, 25f, 0f, .9f, 0f, 0f, 25f, 0f, 0f, .9f, 0f, 25f, 0f, 0f, 0f, 1f, 0f)
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_editor)
        val uri = intent.data ?: return finish()
        preview = findViewById(R.id.preview); tools = findViewById(R.id.tools)
        tabs = listOf(findViewById(R.id.eTab0), findViewById(R.id.eTab1), findViewById(R.id.eTab2))
        tabs.forEachIndexed { i, t -> t.setOnClickListener { show(i) } }
        findViewById<View>(R.id.eClose).setOnClickListener { onBackPressed() }
        findViewById<View>(R.id.eSave).setOnClickListener { save() }
        findViewById<View>(R.id.eUndo).setOnClickListener {
            if (undo.isNotEmpty()) { base = undo.removeAt(undo.size - 1); preview.setImageBitmap(base) } else Ui.toast(this, "Nothing to undo")
        }
        findViewById<View>(R.id.eReset).setOnClickListener {
            if (!::base.isInitialized) return@setOnClickListener
            br = 0; ct = 100; sat = 100; fi = 0; undo.clear(); base = orig; preview.setImageBitmap(base); dirty = false; refreshFilter(); show(tabIdx)
        }
        show(0)
        Thread {
            try {
                val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { d, info, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val m = maxOf(info.size.width, info.size.height)
                    if (m > 2048) { val s = 2048f / m; d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt()) }
                }
                runOnUiThread { if (isDestroyed) return@runOnUiThread; orig = bmp; base = bmp; preview.setImageBitmap(bmp) }
            } catch (e: Exception) { runOnUiThread { if (!isDestroyed) { Ui.toast(this, "Couldn't open image"); finish() } } }
        }.start()
    }

    private fun cm(): ColorMatrix {
        val m = ColorMatrix(); m.setSaturation(sat / 100f)
        val c = ct / 100f; val t = (-0.5f * c + 0.5f) * 255f + br * 1.5f
        m.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        filters[fi].second?.let { m.postConcat(ColorMatrix(it)) }
        return m
    }
    private fun refreshFilter() { preview.colorFilter = ColorMatrixColorFilter(cm()) }

    private fun push(nb: Bitmap) { undo.add(base); if (undo.size > 5) undo.removeAt(0); base = nb; preview.setImageBitmap(nb); dirty = true }
    private fun ready() = ::base.isInitialized
    private fun xform(m: Matrix) { if (ready()) push(Bitmap.createBitmap(base, 0, 0, base.width, base.height, m, true)) }
    private fun crop(rw: Int, rh: Int) {
        if (!ready()) return
        val w = base.width; val h = base.height
        var nw = w; var nh = (w * rh / rw.toFloat()).toInt()
        if (nh > h) { nh = h; nw = (h * rw / rh.toFloat()).toInt() }
        push(Bitmap.createBitmap(base, (w - nw) / 2, (h - nh) / 2, nw, nh))
    }

    private fun chip(t: String, on: Boolean, click: () -> Unit) = TextView(this).apply {
        text = t; textSize = 14f; gravity = Gravity.CENTER; setTextColor(if (on) Color.WHITE else Ui.TEXT)
        setPadding(dp(16), dp(10), dp(16), dp(10)); background = shape(if (on) Ui.ACCENT else Ui.SURFACE2, dp(16).toFloat())
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
        setOnClickListener { click() }
    }

    private fun scroller(l: List<View>): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(context).apply { l.forEach { addView(it) } })
    }

    private fun row(label: String, min: Int, max: Int, v: Int, set: (Int) -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply { text = label; setTextColor(Ui.SUB); textSize = 14f }, LinearLayout.LayoutParams(dp(90), -2))
        addView(SeekBar(context).apply {
            this.max = max - min; progress = v - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { set(p + min); dirty = true; refreshFilter() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun show(t: Int) {
        tabIdx = t; tools.removeAllViews()
        when (t) {
            0 -> { tools.addView(row("Brightness", -100, 100, br) { br = it }); tools.addView(row("Contrast", 50, 150, ct) { ct = it }); tools.addView(row("Saturation", 0, 200, sat) { sat = it }) }
            1 -> tools.addView(scroller(filters.mapIndexed { i, f -> chip(f.first, i == fi) { fi = i; dirty = true; refreshFilter(); show(1) } }))
            else -> tools.addView(scroller(listOf(
                chip("Rotate left", false) { xform(Matrix().apply { postRotate(-90f) }) },
                chip("Rotate right", false) { xform(Matrix().apply { postRotate(90f) }) },
                chip("Flip H", false) { xform(Matrix().apply { postScale(-1f, 1f) }) },
                chip("Flip V", false) { xform(Matrix().apply { postScale(1f, -1f) }) },
                chip("Crop 1:1", false) { crop(1, 1) }, chip("Crop 4:3", false) { crop(4, 3) },
                chip("Crop 16:9", false) { crop(16, 9) }, chip("Crop 3:4", false) { crop(3, 4) })))
        }
        tabs.forEachIndexed { i, v -> v.setTextColor(if (i == t) Ui.ACCENT else Ui.SUB) }
    }

    private fun save() {
        if (!ready()) return
        Ui.toast(this, "Saving…")
        Thread {
            val ok = try {
                val res = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
                Canvas(res).drawBitmap(base, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm()) })
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "EDIT_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ProGallery")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val u = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)!!
                contentResolver.openOutputStream(u)!!.use { res.compress(Bitmap.CompressFormat.JPEG, 95, it) }; res.recycle()
                cv.clear(); cv.put(MediaStore.Images.Media.IS_PENDING, 0); contentResolver.update(u, cv, null, null); true
            } catch (e: Exception) { false }
            runOnUiThread {
                if (ok) { dirty = false; Ui.dialog(this, "Saved", "Your edited copy is in Pictures/ProGallery. The original is untouched.", "Done", null) { finish() } }
                else Ui.toast(this, "Save failed")
            }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (dirty) Ui.dialog(this, "Discard edits?", "Your changes haven't been saved.", "Discard", danger = true) { finish() } else finish()
    }
}
