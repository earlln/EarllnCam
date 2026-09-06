package com.example.earllncam

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Full-screen viewer for one capture, with the rest of the log to either side:
 * swiping left or right walks the same newest-first list the gallery shows,
 * playing videos and displaying photos in place. Handing the file straight to
 * an external viewer, as tapping a row used to, ends that ability at the first
 * file -- the other app knows nothing about EarllnCam's list -- so the button
 * in the caption bar is what opens the current file elsewhere.
 */
class ViewerActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var captionName: TextView
    private lateinit var captionSubtitle: TextView
    private lateinit var positionText: TextView
    private lateinit var adapter: PageAdapter

    private var items: List<MediaVault.Item> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        pager = findViewById(R.id.pager)
        captionName = findViewById(R.id.captionName)
        captionSubtitle = findViewById(R.id.captionSubtitle)
        positionText = findViewById(R.id.positionText)

        adapter = PageAdapter(lifecycleScope)
        pager.adapter = adapter
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                showCaption(position)
                adapter.playOnly(position)
            }
        })

        findViewById<ImageButton>(R.id.openExternalButton).setOnClickListener { openExternally() }

        val startUri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        load(startUri)
    }

    override fun onPause() {
        super.onPause()
        adapter.pauseAll()
    }

    private fun load(startUri: Uri?) {
        lifecycleScope.launch {
            items = withContext(Dispatchers.IO) { MediaVault.listItems(this@ViewerActivity) }
            if (items.isEmpty()) {
                finish()
                return@launch
            }
            adapter.submit(items)
            val start = items.indexOfFirst { it.uri == startUri }.coerceAtLeast(0)
            pager.setCurrentItem(start, false)
            showCaption(start)
            adapter.playOnly(start)
        }
    }

    private fun showCaption(position: Int) {
        val item = items.getOrNull(position) ?: return
        captionName.text = item.displayName
        captionSubtitle.text = MediaLabels.subtitle(this, item)
        positionText.text = getString(R.string.viewer_position, position + 1, items.size)
    }

    private fun openExternally() {
        val item = items.getOrNull(pager.currentItem) ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(item.uri, item.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private class PageAdapter(
        private val scope: CoroutineScope
    ) : RecyclerView.Adapter<PageAdapter.Holder>() {

        private val items = mutableListOf<MediaVault.Item>()
        private val attached = mutableSetOf<Holder>()

        fun submit(newItems: List<MediaVault.Item>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        /** Only the page being looked at plays; the neighbours RecyclerView keeps alive do not. */
        fun playOnly(position: Int) {
            for (holder in attached) {
                if (holder.bindingAdapterPosition == position) holder.play() else holder.pause()
            }
        }

        fun pauseAll() = attached.forEach { it.pause() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.page_media, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.bind(item, scope)
        }

        override fun onViewAttachedToWindow(holder: Holder) {
            attached.add(holder)
        }

        override fun onViewDetachedFromWindow(holder: Holder) {
            holder.pause()
            attached.remove(holder)
        }

        override fun onViewRecycled(holder: Holder) {
            holder.recycle()
            attached.remove(holder)
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val photo: ImageView = view.findViewById(R.id.pagePhoto)
            private val video: VideoView = view.findViewById(R.id.pageVideo)
            private var loadJob: Job? = null
            private var isVideo = false

            fun bind(item: MediaVault.Item, scope: CoroutineScope) {
                loadJob?.cancel()
                isVideo = item.isVideo
                photo.setImageDrawable(null)
                video.setOnPreparedListener(null)

                photo.visibility = if (item.isVideo) View.GONE else View.VISIBLE
                video.visibility = if (item.isVideo) View.VISIBLE else View.GONE

                if (item.isVideo) {
                    video.setVideoURI(item.uri)
                } else {
                    val context = itemView.context
                    loadJob = scope.launch {
                        val bitmap = withContext(Dispatchers.IO) { decode(context, item.uri) }
                        if (bitmap != null) photo.setImageBitmap(bitmap)
                    }
                }
            }

            fun play() {
                if (isVideo) video.start()
            }

            fun pause() {
                if (isVideo && video.isPlaying) video.pause()
            }

            fun recycle() {
                loadJob?.cancel()
                loadJob = null
                if (isVideo) video.stopPlayback()
                photo.setImageDrawable(null)
            }

            private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    // Full-resolution captures are far larger than any phone
                    // screen; sampling down keeps a swipe from allocating tens
                    // of megabytes per page.
                    val sample = max(
                        1,
                        max(info.size.width / MAX_EDGE_PX, info.size.height / MAX_EDGE_PX)
                    )
                    decoder.setTargetSampleSize(sample)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()

            private companion object {
                const val MAX_EDGE_PX = 1440
            }
        }
    }

    companion object {
        private const val EXTRA_URI = "com.example.earllncam.extra.URI"

        fun intent(context: Context, item: MediaVault.Item): Intent =
            Intent(context, ViewerActivity::class.java)
                .putExtra(EXTRA_URI, item.uri.toString())
    }
}
