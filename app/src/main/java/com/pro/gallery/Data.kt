package com.pro.gallery

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

class Media(val id: Long, val uri: Uri, val name: String, val album: String, val date: Long,
            val size: Long, val video: Boolean, val w: Int, val h: Int) {
    /** Lower-cased once at load time so searching never allocates per keystroke. */
    val key: String = name.lowercase()
}

object Session { @JvmField var items: List<Media> = emptyList() }

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("gallery", 0)
    /** Read-only view of the in-memory set kept by SharedPreferences (never mutate it). */
    fun favs(c: Context): Set<String> = sp(c).getStringSet("favs", null) ?: emptySet()
    fun set(c: Context, ids: Collection<Long>, on: Boolean) {
        val s = HashSet(favs(c))
        ids.forEach { if (on) s.add(it.toString()) else s.remove(it.toString()) }
        sp(c).edit().putStringSet("favs", s).apply()
    }
    fun toggle(c: Context, id: Long): Boolean { val on = id.toString() !in favs(c); set(c, listOf(id), on); return on }
}

object Repo {
    fun load(c: Context): List<Media> {
        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE, MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT)
        val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val img = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val vid = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val albums = HashMap<String, String>()
        var out = ArrayList<Media>()
        c.contentResolver.query(MediaStore.Files.getContentUri("external"), proj, sel,
            arrayOf("1", "3"), "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { q ->
            out = ArrayList(q.count)
            while (q.moveToNext()) {
                val id = q.getLong(0); val video = q.getInt(5) == 3
                val a = q.getString(2) ?: "Other"
                out.add(Media(id, ContentUris.withAppendedId(if (video) vid else img, id), q.getString(1) ?: "",
                    albums.getOrPut(a) { a }, q.getLong(3), q.getLong(4), video, q.getInt(6), q.getInt(7)))
            }
        }
        return out
    }

    /** Returns how many items could NOT be deleted. */
    fun delete(c: Context, l: List<Media>): Int {
        var failed = 0
        l.forEach { try { if (c.contentResolver.delete(it.uri, null, null) < 1) failed++ } catch (e: Exception) { failed++ } }
        return failed
    }

    fun share(c: Context, l: List<Media>) {
        val i = if (l.size == 1) Intent(Intent.ACTION_SEND).apply {
            type = if (l[0].video) "video/*" else "image/*"; putExtra(Intent.EXTRA_STREAM, l[0].uri)
        } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(l.map { it.uri }))
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        c.startActivity(Intent.createChooser(i, "Share"))
    }
}
