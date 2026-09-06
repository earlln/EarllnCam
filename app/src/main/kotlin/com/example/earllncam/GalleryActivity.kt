package com.example.earllncam

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Lists everything EarllnCam has saved, newest first, so an old capture can be
 * found without digging through the system gallery. Tapping a row hands the
 * MediaStore uri to whatever viewer the phone already uses.
 *
 * The app owns these files, so they are normally readable without any runtime
 * permission; the read-media permission is still asked for once, because
 * ownership is lost if the app is reinstalled and the list would otherwise
 * come back empty. Deleting is offered here too -- per row, or the whole list
 * at once -- since captures kept from before a reinstall can only be removed
 * with the user's say-so.
 */
class GalleryActivity : AppCompatActivity() {

    private lateinit var listView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var countText: TextView
    private lateinit var deleteAllButton: Button
    private lateinit var adapter: MediaAdapter

    private val requestReadPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { load() }

    private val requestDeleteConsent = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { load() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        listView = findViewById(R.id.mediaList)
        emptyView = findViewById(R.id.emptyGroup)
        countText = findViewById(R.id.galleryCount)
        deleteAllButton = findViewById(R.id.deleteAllButton)
        findViewById<TextView>(R.id.versionText).text = AppVersion.label(this)

        adapter = MediaAdapter(lifecycleScope, ::openItem, ::confirmDeleteItem)
        deleteAllButton.setOnClickListener { confirmDeleteAll() }
        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter

        val missing = readPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestReadPermission.launch(missing.toTypedArray())
    }

    override fun onStart() {
        super.onStart()
        // A capture may have run (or cleanup deleted the old ones) since the
        // list was last built.
        load()
    }

    private fun readPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun load() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { MediaVault.listItems(this@GalleryActivity) }
            adapter.submit(items)
            countText.text = getString(R.string.gallery_count, items.size)
            emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            listView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            deleteAllButton.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun openItem(item: MediaVault.Item) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(item.uri, item.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteItem(item: MediaVault.Item) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.gallery_delete_confirm, item.displayName))
            .setPositiveButton(R.string.gallery_delete) { _, _ -> delete(listOf(item.uri)) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteAll() {
        val uris = adapter.currentUris()
        if (uris.isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.gallery_delete_all_confirm, uris.size))
            .setPositiveButton(R.string.gallery_delete) { _, _ -> delete(uris) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun delete(uris: List<Uri>) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                MediaVault.deleteUris(this@GalleryActivity, uris)
            }
            // Captures saved by an earlier install are not ours to delete on our
            // own; the system asks the user, and the list refreshes either way
            // once that dialog returns.
            val consent = result.consent
            if (consent != null) {
                requestDeleteConsent.launch(IntentSenderRequest.Builder(consent).build())
            } else {
                Toast.makeText(this@GalleryActivity, R.string.gallery_deleted, Toast.LENGTH_SHORT)
                    .show()
                load()
            }
        }
    }

    private class MediaAdapter(
        private val scope: CoroutineScope,
        private val onClick: (MediaVault.Item) -> Unit,
        private val onDelete: (MediaVault.Item) -> Unit
    ) : RecyclerView.Adapter<MediaAdapter.Holder>() {

        private val items = mutableListOf<MediaVault.Item>()

        fun submit(newItems: List<MediaVault.Item>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun currentUris(): List<Uri> = items.map { it.uri }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_media, parent, false)
            return Holder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val context = holder.itemView.context

            holder.name.text = item.displayName
            holder.subtitle.text = subtitleFor(context, item)
            holder.videoBadge.visibility = if (item.isVideo) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onClick(item) }
            holder.deleteButton.setOnClickListener { onDelete(item) }

            holder.thumbnail.setImageDrawable(null)
            holder.thumbnailJob?.cancel()
            holder.boundUri = item.uri
            holder.thumbnailJob = scope.launch {
                val bitmap = withContext(Dispatchers.IO) { loadThumbnail(context, item) }
                if (bitmap != null && holder.boundUri == item.uri) {
                    holder.thumbnail.setImageBitmap(bitmap)
                }
            }
        }

        override fun onViewRecycled(holder: Holder) {
            holder.thumbnailJob?.cancel()
            holder.thumbnailJob = null
            holder.boundUri = null
        }

        private fun loadThumbnail(context: Context, item: MediaVault.Item): Bitmap? =
            runCatching {
                context.contentResolver.loadThumbnail(item.uri, Size(THUMB_PX, THUMB_PX), null)
            }.getOrNull()

        private fun subtitleFor(context: Context, item: MediaVault.Item): String {
            val type = context.getString(
                if (item.isVideo) R.string.gallery_type_video else R.string.gallery_type_photo
            )
            val parts = mutableListOf(type)
            if (item.isVideo && item.durationMs > 0) parts.add(formatDuration(item.durationMs))
            if (item.sizeBytes > 0) parts.add(formatSize(item.sizeBytes))
            parts.add(DATE_FORMAT.format(Date(item.dateAddedSeconds * 1000L)))
            return parts.joinToString(" · ")
        }

        private fun formatDuration(durationMs: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
            return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
        }

        private fun formatSize(bytes: Long): String = when {
            bytes >= 1024 * 1024 ->
                String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
            val videoBadge: ImageView = view.findViewById(R.id.videoBadge)
            val name: TextView = view.findViewById(R.id.itemName)
            val subtitle: TextView = view.findViewById(R.id.itemSubtitle)
            val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
            var thumbnailJob: Job? = null
            var boundUri: Uri? = null
        }

        private companion object {
            const val THUMB_PX = 256
            val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        }
    }
}
