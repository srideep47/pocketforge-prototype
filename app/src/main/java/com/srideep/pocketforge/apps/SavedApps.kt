package com.srideep.pocketforge.apps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.srideep.pocketforge.R
import java.io.File

/**
 * Pages the user chose to keep as apps of their own, each pinned to the home screen.
 *
 * A saved app is a frozen copy of index.html, not a link to the project: the project keeps
 * changing as the user asks for more, and the dev server is not running when a pinned app is
 * opened from the launcher. The pages are self-contained by construction (SiteArtifact strips
 * external links), so the one file is the whole app.
 */
object SavedApps {

    class SavedApp(val id: String, val name: String, val index: File)

    private const val ICON_SIZE = 432

    private val TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val H1 = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAG = Regex("<[^>]+>")

    private fun root(context: Context) = File(context.filesDir, "apps")

    fun indexOf(context: Context, id: String): File = File(File(root(context), id), "index.html")

    fun nameOf(context: Context, id: String): String =
        runCatching { File(File(root(context), id), "name.txt").readText() }.getOrDefault("App")

    /** The page's own title, else its first heading, cleaned of markup. */
    fun titleOf(html: String): String? =
        (TITLE.find(html) ?: H1.find(html))?.groupValues?.get(1)
            ?.replace(TAG, "")?.replace(Regex("\\s+"), " ")?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun save(context: Context, html: String, name: String): SavedApp {
        val id = "app_" + System.currentTimeMillis()
        val dir = File(root(context), id).apply { mkdirs() }
        File(dir, "index.html").writeText(html)
        File(dir, "name.txt").writeText(name)
        return SavedApp(id, name, File(dir, "index.html"))
    }

    /**
     * Asks the launcher to pin [app]. The launcher shows its own confirmation, so a true
     * result means the request was made, not that the user accepted it.
     */
    fun pin(context: Context, app: SavedApp, screenshot: File?): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val intent = Intent(context, AppPlayerActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            // Distinct data per app, so each opens in a task of its own (documentLaunchMode).
            .setData(Uri.parse("pocketforge://app/" + app.id))
            .putExtra(AppPlayerActivity.EXTRA_APP_ID, app.id)
        val icon = screenshot?.let(::miniature)?.let(IconCompat::createWithAdaptiveBitmap)
            ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        val shortcut = ShortcutInfoCompat.Builder(context, app.id)
            .setShortLabel(app.name.take(24))
            .setLongLabel(app.name.take(48))
            .setIcon(icon)
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /**
     * The app's own screen, shrunk into the adaptive icon's safe zone on a dark tile: the icon
     * is recognisably the app that was built, not a generic badge.
     */
    private fun miniature(screenshot: File): Bitmap? {
        val shot = BitmapFactory.decodeFile(screenshot.absolutePath) ?: return null
        val icon = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(icon)
        canvas.drawColor(Color.rgb(11, 15, 23))
        // The launcher masks the outer third of an adaptive icon; keep the screen inside it.
        val height = ICON_SIZE * 0.62f
        val width = height * shot.width / shot.height
        val frame = RectF((ICON_SIZE - width) / 2, (ICON_SIZE - height) / 2, (ICON_SIZE + width) / 2, (ICON_SIZE + height) / 2)
        val clip = Path().apply { addRoundRect(frame, 18f, 18f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(shot, null, frame, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        shot.recycle()
        return icon
    }
}
