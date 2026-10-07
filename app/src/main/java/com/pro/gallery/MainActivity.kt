package com.pro.gallery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {
    private var all = listOf<Media>()
    private var cells = listOf<Cell>()
    private var tab = 0; private var album: String? = null; private var query = ""
    private var sort = 0; private var span = 3; private var asked = false
    private var stale = true; private var loading = false; private var pending = false; private var resumed = false
    private val sel = linkedSetOf<Long>()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var adapter: CellAdapter
    private lateinit var list: RecyclerView
    private lateinit var tabs: List<TextView>
    private lateinit var search: EditText
    private val perms = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private val applyQuery = Runnable { refresh() }
    private val reload = Runnable { if (resumed && granted()) load() }

    /** Reload only when the media store really changed (not on every resume). */
    private val observer = object : ContentObserver(ui) {
        override fun onChange(selfChange: Boolean) {
            stale = true; ui.removeCallbacks(reload); ui.postDelayed(reload, 500)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_main)
        list = findViewById(R.id.list); search = findViewById(R.id.search)
        adapter = CellAdapter(::openCell, ::longPress); adapter.sel = sel
        list.layoutManager = GridLayoutManager(this, span); list.adapter = adapter
        list.setHasFixedSize(true); list.setItemViewCacheSize(24); list.itemAnimator = null
        tabs = listOf(findViewById(R.id.tab0), findViewById(R.id.tab1), findViewById(R.id.tab2))
        tabs.forEachIndexed { i, t -> t.setOnClickListener { tab = i; album = null; sel.clear(); refresh() } }
        findViewById<View>(R.id.btnSearch).setOnClickListener {
            val shown = search.visibility == View.VISIBLE
            search.visibility = if (shown) View.GONE else View.VISIBLE
            if (shown) search.setText("") else search.requestFocus()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) {
                query = e.toString(); ui.removeCallbacks(applyQuery); ui.postDelayed(applyQuery, 120)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        findViewById<View>(R.id.btnSort).setOnClickListener {
            Ui.sheet(this, "Sort by", listOf("Newest first", "Oldest first", "Largest first", "Name A–Z"), sort) { sort = it; refresh() }
        }
        findViewById<View>(R.id.btnGrid).setOnClickListener { span = if (span >= 5) 2 else span + 1; refresh(); Ui.toast(this, "$span columns") }
        findViewById<View>(R.id.selClose).setOnClickListener { clearSel() }
        findViewById<View>(R.id.selShare).setOnClickListener { Repo.share(this, all.filter { it.id in sel }) }
        findViewById<View>(R.id.selFav).setOnClickListener {
            val favs = Prefs.favs(this); Prefs.set(this, sel.toList(), !sel.all { it.toString() in favs })
            sel.clear(); refresh()
        }
        findViewById<View>(R.id.selDel).setOnClickListener {
            Ui.dialog(this, "Delete ${sel.size} item(s)?", "They will be permanently removed from your device.", "Delete", danger = true) {
                val victims = all.filter { it.id in sel }
                sel.clear(); updateBar(); adapter.notifyDataSetChanged()
                Thread {
                    val failed = Repo.delete(this, victims)
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        if (failed > 0) Ui.toast(this, "$failed item(s) couldn't be deleted")
                        load()
                    }
                }.start()
            }
        }
        val img = MediaStore.Images.Media.EXTERNAL_CONTENT_URI; val vid = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        contentResolver.registerContentObserver(img, true, observer)
        contentResolver.registerContentObserver(vid, true, observer)
    }

    override fun onResume() { super.onResume(); resumed = true; ensurePermission() }
    override fun onPause() { resumed = false; super.onPause() }
    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer); ui.removeCallbacksAndMessages(null); super.onDestroy()
    }

    private fun granted() = perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun ensurePermission() {
        if (granted()) { if (stale && !loading) load() }
        else if (!asked) {
            asked = true
            Ui.dialog(this, "Allow access", "Pro Gallery needs access to your photos and videos to show your library.", "Allow", "Not now") {
                requestPermissions(perms, 1)
            }
        } else refresh()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r); if (granted()) load() else refresh()
    }

    private fun load() {
        if (loading) { pending = true; return }
        loading = true; stale = false; pending = false
        Thread {
            val r = try { Repo.load(this) } catch (e: Exception) { null }
            runOnUiThread {
                loading = false
                if (!isDestroyed) { if (r != null) all = r; refresh(); if (pending) load() }
            }
        }.start()
    }

    private fun refresh() {
        val q = query.lowercase()
        var items = if (q.isEmpty()) all else all.filter { it.key.contains(q) }
        val favs = Prefs.favs(this)
        if (tab == 2) items = items.filter { it.id.toString() in favs }
        val albums = tab == 1 && album == null
        cells = if (albums) items.groupBy { it.album }.toSortedMap().map { (k, v) -> Cell(v.first(), k, v.size) }
        else {
            album?.let { a -> items = items.filter { it.album == a } }
            items = when (sort) {
                1 -> items.sortedBy { it.date }; 2 -> items.sortedByDescending { it.size }
                3 -> items.sortedBy { it.key }; else -> items.sortedByDescending { it.date }
            }
            items.map { Cell(it, null, 0) }
        }
        val cols = if (albums) 2 else span
        (list.layoutManager as GridLayoutManager).spanCount = cols
        adapter.cells = cells; adapter.favs = favs
        adapter.size = (resources.displayMetrics.widthPixels - dp(16)) / cols - dp(6)
        adapter.px = adapter.size.coerceIn(160, 512)
        adapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.title).text = album ?: listOf("Gallery", "Albums", "Favorites")[tab]
        tabs.forEachIndexed { i, t ->
            t.setTextColor(if (i == tab) Color.WHITE else Ui.SUB)
            t.background = if (i == tab) shape(Ui.ACCENT, dp(13).toFloat()) else null
        }
        val e = findViewById<TextView>(R.id.empty)
        e.visibility = if (cells.isEmpty()) View.VISIBLE else View.GONE
        e.text = if (!granted()) "Storage permission is required to show your photos." else if (tab == 2) "No favorites yet.\nTap the heart on any photo." else "Nothing here yet."
        updateBar()
    }

    private fun openCell(c: Cell, pos: Int) {
        if (c.album != null) { album = c.album; refresh(); return }
        if (sel.isNotEmpty()) { toggle(c.m.id); return }
        Session.items = cells.map { it.m }
        startActivity(Intent(this, ViewerActivity::class.java).putExtra("i", pos))
    }

    private fun longPress(c: Cell) = toggle(c.m.id)

    private fun toggle(id: Long) {
        if (!sel.remove(id)) sel.add(id)
        val i = cells.indexOfFirst { it.album == null && it.m.id == id }
        if (i >= 0) adapter.notifyItemChanged(i)
        updateBar()
    }

    private fun clearSel() { if (sel.isEmpty()) return; sel.clear(); adapter.notifyDataSetChanged(); updateBar() }

    private fun updateBar() {
        findViewById<View>(R.id.selBar).visibility = if (sel.isEmpty()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.selCount).text = "${sel.size} selected"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            sel.isNotEmpty() -> clearSel()
            album != null -> { album = null; refresh() }
            else -> super.onBackPressed()
        }
    }
}
