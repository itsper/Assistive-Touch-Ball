package com.example.assistive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImageViewMode {
    ALBUMS,
    GRID,
    VIEWER
}

class ImageManager(
    private val service: FloatingBallService,
    private val serviceScope: CoroutineScope,
    private val menuView: View
) {
    // Containers
    private lateinit var layoutImageContainer: FrameLayout
    private lateinit var layoutAlbumsView: View
    private lateinit var layoutGridView: View
    private lateinit var layoutViewerView: View

    // Header elements
    private lateinit var btnImageBack: ImageButton
    private lateinit var txtImageTitle: TextView
    private lateinit var btnImageSort: ImageButton
    private lateinit var btnImageSize: ImageButton
    private lateinit var btnImageClose: ImageButton

    // Recyclers & ViewPager
    private lateinit var recyclerAlbums: RecyclerView
    private lateinit var recyclerGrid: RecyclerView
    private lateinit var viewPagerImages: ViewPager2
    private lateinit var txtViewerCounter: TextView
    private lateinit var txtEmptyImages: View
    private lateinit var btnScanImages: View

    // Adapters
    private var folderAdapter: ImageFolderAdapter? = null
    private var gridAdapter: ImageGridAdapter? = null
    private var viewerAdapter: ImageViewerAdapter? = null

    // State
    private var allFolders = listOf<ImageFolderModel>()
    private var currentFolder: ImageFolderModel? = null
    private var currentImages = listOf<ImageModel>()
    private var currentSortOption = ImageSortOption.DATE_DESC
    private var currentViewMode = ImageViewMode.ALBUMS
    private var isExpanded = false

    fun init() {
        layoutImageContainer = menuView.findViewById(R.id.layout_image_container)
        layoutAlbumsView = layoutImageContainer.findViewById(R.id.layout_albums_view)
        layoutGridView = layoutImageContainer.findViewById(R.id.layout_grid_view)
        layoutViewerView = layoutImageContainer.findViewById(R.id.layout_viewer_view)

        btnImageBack = layoutImageContainer.findViewById(R.id.btn_image_back)
        txtImageTitle = layoutImageContainer.findViewById(R.id.txt_image_title)
        btnImageSort = layoutImageContainer.findViewById(R.id.btn_image_sort)
        btnImageSize = layoutImageContainer.findViewById(R.id.btn_image_size)
        btnImageClose = layoutImageContainer.findViewById(R.id.btn_image_close)

        recyclerAlbums = layoutImageContainer.findViewById(R.id.recycler_image_albums)
        recyclerGrid = layoutImageContainer.findViewById(R.id.recycler_image_grid)
        viewPagerImages = layoutImageContainer.findViewById(R.id.viewpager_images)
        txtViewerCounter = layoutImageContainer.findViewById(R.id.txt_viewer_counter)
        txtEmptyImages = layoutImageContainer.findViewById(R.id.txt_empty_images)
        btnScanImages = layoutImageContainer.findViewById(R.id.btn_scan_images)

        setupRecyclerViews()
        setupListeners()
        scanLocalImages()
    }

    private fun setupRecyclerViews() {
        // Albums list
        recyclerAlbums.layoutManager = LinearLayoutManager(service)
        folderAdapter = ImageFolderAdapter(
            serviceScope = serviceScope,
            context = service,
            onFolderClick = { folder -> openFolder(folder) }
        )
        recyclerAlbums.adapter = folderAdapter

        // Photos Grid (3 columns)
        recyclerGrid.layoutManager = GridLayoutManager(service, 3)
        gridAdapter = ImageGridAdapter(
            serviceScope = serviceScope,
            context = service,
            onImageClick = { index -> openViewer(index) }
        )
        recyclerGrid.adapter = gridAdapter

        // Photo Viewer (ViewPager2 with pinch-to-zoom)
        viewerAdapter = ImageViewerAdapter(
            serviceScope = serviceScope,
            context = service
        )
        viewPagerImages.adapter = viewerAdapter
        viewPagerImages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateViewerCounter(position)
            }
        })
    }

    private fun setupListeners() {
        // Back navigation
        btnImageBack.setOnClickListener {
            handleBackNavigation()
        }

        // Close to ball
        btnImageClose.setOnClickListener {
            closeImagePanel()
        }

        // Sort options
        btnImageSort.setOnClickListener { v ->
            showSortPopupMenu(v)
        }

        // Size toggle
        btnImageSize.setOnClickListener {
            toggleSize()
        }

        // Scan button when empty
        btnScanImages.setOnClickListener {
            scanLocalImages()
        }
    }

    fun openImagePanel() {
        layoutImageContainer.visibility = View.VISIBLE
        currentViewMode = ImageViewMode.ALBUMS
        updateViewVisibility()
        if (allFolders.isEmpty()) {
            scanLocalImages()
        }
    }

    fun scanLocalImages() {
        serviceScope.launch {
            allFolders = ImageFolderModel.scanLocalFolders(service)
            if (allFolders.isNotEmpty()) {
                txtEmptyImages.visibility = View.GONE
                recyclerAlbums.visibility = View.VISIBLE
                folderAdapter?.submitFolders(allFolders)
            } else {
                txtEmptyImages.visibility = View.VISIBLE
                recyclerAlbums.visibility = View.GONE
            }
        }
    }

    private fun openFolder(folder: ImageFolderModel) {
        currentFolder = folder
        currentImages = folder.images.sortedByOption(currentSortOption)
        currentViewMode = ImageViewMode.GRID
        txtImageTitle.text = folder.name
        gridAdapter?.submitImages(currentImages)
        updateViewVisibility()
    }

    private fun openViewer(index: Int) {
        if (index !in currentImages.indices) return
        currentViewMode = ImageViewMode.VIEWER
        txtImageTitle.text = currentFolder?.name ?: "Photo"
        updateViewVisibility()

        viewerAdapter?.submitImages(currentImages)
        viewPagerImages.post {
            viewPagerImages.setCurrentItem(index, false)
            updateViewerCounter(index)
        }
    }

    private fun updateViewerCounter(position: Int) {
        if (currentImages.isNotEmpty()) {
            txtViewerCounter.text = "${position + 1} / ${currentImages.size}"
        }
    }

    private fun handleBackNavigation() {
        when (currentViewMode) {
            ImageViewMode.VIEWER -> {
                currentViewMode = ImageViewMode.GRID
                txtImageTitle.text = currentFolder?.name ?: "Photos"
                updateViewVisibility()
            }
            ImageViewMode.GRID -> {
                currentViewMode = ImageViewMode.ALBUMS
                txtImageTitle.text = "Albums"
                updateViewVisibility()
            }
            ImageViewMode.ALBUMS -> {
                closeImagePanel()
            }
        }
    }

    fun resetSize() {
        if (isExpanded) {
            toggleSize()
        }
    }

    fun closeImagePanel() {
        if (isExpanded) {
            toggleSize()
        }
        layoutImageContainer.visibility = View.GONE
        val menuButtons = menuView.findViewById<View>(R.id.layout_menu_buttons)
        menuButtons.visibility = View.VISIBLE
    }

    private fun updateViewVisibility() {
        when (currentViewMode) {
            ImageViewMode.ALBUMS -> {
                layoutAlbumsView.visibility = View.VISIBLE
                layoutGridView.visibility = View.GONE
                layoutViewerView.visibility = View.GONE
                txtImageTitle.text = "Albums"
                btnImageSort.visibility = View.GONE
            }
            ImageViewMode.GRID -> {
                layoutAlbumsView.visibility = View.GONE
                layoutGridView.visibility = View.VISIBLE
                layoutViewerView.visibility = View.GONE
                btnImageSort.visibility = View.VISIBLE
            }
            ImageViewMode.VIEWER -> {
                layoutAlbumsView.visibility = View.GONE
                layoutGridView.visibility = View.GONE
                layoutViewerView.visibility = View.VISIBLE
                btnImageSort.visibility = View.GONE
            }
        }
    }

    private fun showSortPopupMenu(anchor: View) {
        val popup = PopupMenu(service, anchor)
        ImageSortOption.values().forEachIndexed { index, option ->
            popup.menu.add(0, index, index, option.title)
        }
        popup.setOnMenuItemClickListener { item ->
            val selectedOption = ImageSortOption.values().getOrNull(item.itemId)
            if (selectedOption != null) {
                currentSortOption = selectedOption
                applyCurrentSort()
            }
            true
        }
        popup.show()
    }

    private fun applyCurrentSort() {
        currentImages = currentImages.sortedByOption(currentSortOption)
        gridAdapter?.submitImages(currentImages)
        viewerAdapter?.submitImages(currentImages)
        updateViewerCounter(viewPagerImages.currentItem)
    }

    private fun toggleSize() {
        isExpanded = !isExpanded
        val density = service.resources.displayMetrics.density
        val targetWidthDp = if (isExpanded) 310 else 200
        val targetHeightDp = if (isExpanded) 390 else 234

        val widthPx = (targetWidthDp * density).toInt()
        val heightPx = (targetHeightDp * density).toInt()

        layoutImageContainer.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
        btnImageSize.setImageResource(if (isExpanded) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen)

        try {
            service.windowManager.updateViewLayout(service.menuView, service.menuParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Adapters ---

    class ImageFolderAdapter(
        private val serviceScope: CoroutineScope,
        private val context: Context,
        private val onFolderClick: (ImageFolderModel) -> Unit
    ) : RecyclerView.Adapter<ImageFolderAdapter.FolderViewHolder>() {

        private var folders = listOf<ImageFolderModel>()

        fun submitFolders(newFolders: List<ImageFolderModel>) {
            folders = newFolders
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_folder, parent, false)
            return FolderViewHolder(view)
        }

        override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
            val folder = folders[position]
            holder.txtName.text = folder.name
            holder.txtCount.text = "${folder.images.size} photos"

            val coverUri = folder.coverUri
            if (coverUri != null) {
                holder.imgCover.setImageResource(R.drawable.ic_image)
                val tag = coverUri.toString()
                holder.imgCover.tag = tag
                serviceScope.launch {
                    val bmp = ImageThumbnailLoader.loadThumbnail(context, coverUri, 64)
                    if (holder.imgCover.tag == tag && bmp != null) {
                        holder.imgCover.setImageBitmap(bmp)
                    }
                }
            } else {
                holder.imgCover.setImageResource(R.drawable.ic_folder)
            }

            holder.itemView.setOnClickListener {
                onFolderClick(folder)
            }
        }

        override fun getItemCount(): Int = folders.size

        class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imgCover: ImageView = view.findViewById(R.id.img_folder_cover)
            val txtName: TextView = view.findViewById(R.id.txt_folder_name)
            val txtCount: TextView = view.findViewById(R.id.txt_folder_count)
        }
    }

    class ImageGridAdapter(
        private val serviceScope: CoroutineScope,
        private val context: Context,
        private val onImageClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ImageGridAdapter.GridViewHolder>() {

        private var images = listOf<ImageModel>()

        fun submitImages(newImages: List<ImageModel>) {
            images = newImages
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_grid, parent, false)
            return GridViewHolder(view)
        }

        override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
            val item = images[position]
            val tag = item.uri.toString()
            holder.imgThumb.tag = tag

            val cached = ImageThumbnailLoader.memoryCache.get("${item.uri}_180")
            if (cached != null) {
                holder.imgThumb.setImageBitmap(cached)
            } else {
                holder.imgThumb.setImageDrawable(null)
                serviceScope.launch {
                    val bmp = ImageThumbnailLoader.loadThumbnail(context, item.uri, 180)
                    if (holder.imgThumb.tag == tag && bmp != null) {
                        holder.imgThumb.setImageBitmap(bmp)
                    }
                }
            }

            holder.itemView.setOnClickListener {
                onImageClick(position)
            }
        }

        override fun getItemCount(): Int = images.size

        class GridViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imgThumb: ImageView = view.findViewById(R.id.img_grid_thumb)
        }
    }

    class ImageViewerAdapter(
        private val serviceScope: CoroutineScope,
        private val context: Context
    ) : RecyclerView.Adapter<ImageViewerAdapter.PageViewHolder>() {

        private var images = listOf<ImageModel>()

        fun submitImages(newImages: List<ImageModel>) {
            images = newImages
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_viewer, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val item = images[position]
            val tag = item.uri.toString()
            holder.zoomableImageView.tag = tag
            holder.zoomableImageView.resetZoom()

            // 1. Instantly display cached thumbnail from memory so there is NO blank/black screen
            val cachedThumb = ImageThumbnailLoader.memoryCache.get("${item.uri}_180")
                ?: ImageThumbnailLoader.memoryCache.get("${item.uri}_64")
            if (cachedThumb != null) {
                holder.zoomableImageView.setImageBitmap(cachedThumb)
                holder.progressBar.visibility = View.VISIBLE
            } else {
                holder.zoomableImageView.setImageDrawable(null)
                holder.progressBar.visibility = View.VISIBLE
            }

            // 2. Concurrently load full-resolution uncompressed image
            serviceScope.launch {
                val fullBmp = ImageThumbnailLoader.loadFullImage(context, item.uri, 2560)
                if (holder.zoomableImageView.tag == tag) {
                    holder.progressBar.visibility = View.GONE
                    if (fullBmp != null) {
                        holder.zoomableImageView.setImageBitmap(fullBmp)
                    }
                }
            }
        }

        override fun onViewRecycled(holder: PageViewHolder) {
            super.onViewRecycled(holder)
            holder.zoomableImageView.resetZoom()
        }

        override fun getItemCount(): Int = images.size

        class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val zoomableImageView: ZoomableImageView = view.findViewById(R.id.img_viewer_zoomable)
            val progressBar: ProgressBar = view.findViewById(R.id.progress_viewer)
        }
    }
}

/**
 * High-performance, uncompressed original resolution image decoder supporting all formats:
 * PNG (with transparency), JPG, JPEG, WEBP, GIF, HEIC, HEIF, BMP.
 */
object ImageThumbnailLoader {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 6).coerceAtLeast(2048)

    val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    suspend fun loadThumbnail(context: Context, uri: Uri, sizeDp: Int = 180): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${uri}_$sizeDp"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(128)

        val bitmap = decodeImage(context, uri, targetMaxDim = sizePx)
        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }
        bitmap
    }

    suspend fun loadFullImage(context: Context, uri: Uri, maxDimension: Int = 2560): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${uri}_full_$maxDimension"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val bitmap = decodeImage(context, uri, targetMaxDim = maxDimension)
        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }
        bitmap
    }

    private fun decodeImage(context: Context, uri: Uri, targetMaxDim: Int): Bitmap? {
        // Strategy 1: Modern ImageDecoder (API 28+)
        // Decodes original image data across ALL formats (PNG, JPG, WEBP, HEIC, GIF, BMP) without quality loss
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    val origW = info.size.width
                    val origH = info.size.height
                    val maxDim = maxOf(origW, origH)
                    // If image exceeds max dimension, scale smoothly; otherwise keep 100% full original resolution
                    if (maxDim > targetMaxDim && targetMaxDim > 0) {
                        val scale = targetMaxDim.toFloat() / maxDim.toFloat()
                        val w = (origW * scale).toInt().coerceAtLeast(1)
                        val h = (origH * scale).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(w, h)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Strategy 2: BitmapFactory with FileDescriptor (API 24-27 or fallback)
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                pfd.use { parcelFd ->
                    val fd = parcelFd.fileDescriptor
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(fd, null, boundsOptions)

                    val origW = boundsOptions.outWidth
                    val origH = boundsOptions.outHeight
                    if (origW > 0 && origH > 0) {
                        var inSampleSize = 1
                        val maxDim = maxOf(origW, origH)
                        while (maxDim / inSampleSize > targetMaxDim * 1.5 && inSampleSize < 32) {
                            inSampleSize *= 2
                        }

                        val decodeOptions = BitmapFactory.Options().apply {
                            this.inSampleSize = inSampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }

                        var bmp = BitmapFactory.decodeFileDescriptor(fd, null, decodeOptions)
                        if (bmp != null) {
                            // Check EXIF rotation
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    val exif = android.media.ExifInterface(fd)
                                    val orientation = exif.getAttributeInt(
                                        android.media.ExifInterface.TAG_ORIENTATION,
                                        android.media.ExifInterface.ORIENTATION_NORMAL
                                    )
                                    val rotation = when (orientation) {
                                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                        else -> 0f
                                    }
                                    if (rotation != 0f) {
                                        val matrix = Matrix().apply { postRotate(rotation) }
                                        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                                        if (rotated != bmp) {
                                            bmp.recycle()
                                            bmp = rotated
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                            return bmp
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Strategy 3: ContentResolver openInputStream fallback
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val decodeOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                return BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }
}
