package com.pro.gallery

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import java.util.concurrent.Executors

/**
 * Tiny image loader that replaces Glide (~700 KB smaller).
 * - Grid thumbnails come from the system thumbnail cache via ContentResolver.loadThumbnail (API 29+).
 * - Viewer images are decoded down to MAX px with ImageDecoder (EXIF-aware), after a fast preview.
 * - A byte-bounded LruCache keeps memory low; recycled views cancel their pending work.
 */
object Thumbs {
    private const val MAX = 2048
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceIn(8 shl 20, 48 shl 20)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun grid(v: ImageView, m: Media, px: Int) {
        val key = "${m.id}@$px"
        v.tag = key
        val hit = cache.get(key)
        if (hit != null) { v.setImageBitmap(hit); return }
        v.setImageDrawable(null)
        val cr = v.context.applicationContext.contentResolver
        val uri = m.uri
        pool.execute {
            if (v.tag != key) return@execute
            val bmp = try { cr.loadThumbnail(uri, Size(px, px), null) } catch (e: Exception) { null }
            if (bmp != null) cache.put(key, bmp)
            main.post { if (v.tag == key && bmp != null) v.setImageBitmap(bmp) }
        }
    }

    fun full(v: ImageView, m: Media) {
        val key = "F${m.id}"
        v.tag = key
        val hit = cache.get(key)
        if (hit != null) { v.setImageBitmap(hit); return }
        v.setImageDrawable(null)
        val cr = v.context.applicationContext.contentResolver
        val uri = m.uri
        val video = m.video
        pool.execute {
            if (v.tag != key) return@execute
            if (!video) {
                val preview = try { cr.loadThumbnail(uri, Size(512, 512), null) } catch (e: Exception) { null }
                if (preview != null) main.post { if (v.tag == key) v.setImageBitmap(preview) }
            }
            if (v.tag != key) return@execute
            val bmp = try {
                if (video) cr.loadThumbnail(uri, Size(1080, 1080), null)
                else ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { d, info, _ ->
                    val mx = maxOf(info.size.width, info.size.height)
                    if (mx > MAX) {
                        val s = MAX.toFloat() / mx
                        d.setTargetSize(maxOf(1, (info.size.width * s).toInt()), maxOf(1, (info.size.height * s).toInt()))
                    }
                }
            } catch (e: Exception) { null }
            if (bmp != null) cache.put(key, bmp)
            main.post { if (v.tag == key && bmp != null) v.setImageBitmap(bmp) }
        }
    }

    fun cancel(v: ImageView) { v.tag = null; v.setImageDrawable(null) }
}
