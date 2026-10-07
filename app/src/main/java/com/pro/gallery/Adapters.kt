package com.pro.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class Cell(val m: Media, val album: String?, val count: Int)

class CellAdapter(val onClick: (Cell, Int) -> Unit, val onLong: (Cell) -> Unit) : RecyclerView.Adapter<CellAdapter.VH>() {
    var cells = listOf<Cell>(); var size = 0; var px = 256
    var sel: Set<Long> = emptySet(); var favs: Set<String> = emptySet()

    init { setHasStableIds(true) }

    class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val img: ImageView = root.findViewById(R.id.thumb); val play: View = root.findViewById(R.id.play)
        val fav: View = root.findViewById(R.id.fav); val check: View = root.findViewById(R.id.check)
        val label: TextView = root.findViewById(R.id.label)
    }

    override fun getItemCount() = cells.size

    override fun getItemId(i: Int): Long {
        val c = cells[i]
        return if (c.album == null) c.m.id else -1L - (c.album.hashCode().toLong() and 0x7fffffffL)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
        val h = VH(LayoutInflater.from(p.context).inflate(R.layout.item_cell, p, false))
        h.root.setOnClickListener { val i = h.bindingAdapterPosition; if (i >= 0) onClick(cells[i], i) }
        h.root.setOnLongClickListener {
            val i = h.bindingAdapterPosition
            if (i >= 0 && cells[i].album == null) onLong(cells[i]); true
        }
        return h
    }

    override fun onBindViewHolder(h: VH, i: Int) {
        val c = cells[i]; val media = c.album == null
        h.root.layoutParams.height = size
        Thumbs.grid(h.img, c.m, px)
        h.play.visibility = if (media && c.m.video) View.VISIBLE else View.GONE
        h.fav.visibility = if (media && c.m.id.toString() in favs) View.VISIBLE else View.GONE
        h.check.visibility = if (media && c.m.id in sel) View.VISIBLE else View.GONE
        if (media) h.label.visibility = View.GONE
        else { h.label.visibility = View.VISIBLE; h.label.text = "${c.album}\n${c.count} items" }
    }

    override fun onViewRecycled(h: VH) { Thumbs.cancel(h.img) }
}
