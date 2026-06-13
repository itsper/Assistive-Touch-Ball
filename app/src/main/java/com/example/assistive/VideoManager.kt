package com.example.assistive

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class VideoManager(
    private val service: FloatingBallService,
    private val serviceScope: CoroutineScope,
    private val menuView: View
) {
    internal var videoPlayer: ExoPlayer? = null
    private var videoFolderList = listOf<VideoFolderModel>()
    private var allVideosList = listOf<VideoModel>()
    private var videoList = listOf<VideoModel>()
    private var currentVideoIndex = -1
    private var activeVideoViewHolder: VideoViewHolder? = null
    private var videoProgressJob: Job? = null
    private var isUserTrackingVideoSeekBar = false

    fun init() {
        initVideoPlayer()
        scanLocalVideos()
        val videoViewHolder = VideoViewHolder(menuView.findViewById(R.id.layout_video_container))
        setupVideoPage(videoViewHolder)
    }

    private fun initVideoPlayer() {
        if (videoPlayer == null) {
            videoPlayer = ExoPlayer.Builder(service).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateVideoPlayerUI()
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateVideoPlayerUI()
                        if (isPlaying) {
                            startVideoProgressUpdates()
                        } else {
                            stopVideoProgressUpdates()
                        }
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val currentIdx = currentMediaItemIndex
                        if (currentIdx >= 0 && currentIdx < videoList.size) {
                            currentVideoIndex = currentIdx
                        }
                        updateVideoPlayerUI()
                    }
                })
            }
        }
    }

    fun scanLocalVideos() {
        serviceScope.launch {
            allVideosList = VideoModel.scanLocalVideoFiles(service)
            videoFolderList = VideoFolderModel.scanLocalFolders(service)
            if (allVideosList.isNotEmpty()) {
                videoList = allVideosList
                loadVideoMediaItems()
            }
            updateVideoPlayerUI()

            activeVideoViewHolder?.let { holder ->
                if (allVideosList.isNotEmpty()) {
                    holder.txtEmptyPlaylist.visibility = View.GONE
                    holder.playlistRecycler.visibility = View.VISIBLE
                    val adapter = holder.playlistRecycler.adapter as? VideoPlaylistAdapter
                    adapter?.setFoldersAndAllVideos(videoFolderList, allVideosList)
                } else {
                    holder.txtEmptyPlaylist.visibility = View.VISIBLE
                    holder.playlistRecycler.visibility = View.GONE
                }
            }
        }
    }

    private fun loadVideoMediaItems() {
        videoPlayer?.let { p ->
            p.clearMediaItems()
            videoList.forEach { video ->
                p.addMediaItem(MediaItem.fromUri(video.uri))
            }
            p.prepare()
        }
    }

    private fun toggleVideoPlayPause() {
        val p = videoPlayer ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            if (videoList.isNotEmpty()) {
                if (currentVideoIndex == -1) {
                    currentVideoIndex = 0
                    p.seekTo(0, 0)
                }
                p.play()
            }
        }
    }

    private fun playVideoNext() {
        val p = videoPlayer ?: return
        if (p.hasNextMediaItem()) {
            p.seekToNext()
        } else if (videoList.isNotEmpty()) {
            p.seekTo(0, 0)
        }
        p.play()
    }

    private fun playVideo(index: Int) {
        val p = videoPlayer ?: return
        if (index >= 0 && index < videoList.size) {
            currentVideoIndex = index
            p.seekTo(index, 0)
            p.play()
            updateVideoPlayerUI()
        }
    }

    private fun startVideoProgressUpdates() {
        videoProgressJob?.cancel()
        videoProgressJob = serviceScope.launch {
            while (true) {
                updateVideoProgressUI()
                delay(500)
            }
        }
    }

    private fun stopVideoProgressUpdates() {
        videoProgressJob?.cancel()
        videoProgressJob = null
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    private fun updateVideoProgressUI() {
        val p = videoPlayer ?: return
        val holder = activeVideoViewHolder
        if (holder == null) {
            stopVideoProgressUpdates()
            return
        }
        val duration = p.duration
        val position = p.currentPosition
        
        holder.txtCurrentTime.text = formatTime(position)
        holder.txtTotalDuration.text = if (duration > 0) formatTime(duration) else "0:00"

        if (!isUserTrackingVideoSeekBar && duration > 0) {
            holder.playerSeekBar.progress = ((position * 100) / duration).toInt()
        } else if (!isUserTrackingVideoSeekBar) {
            holder.playerSeekBar.progress = 0
        }
    }

    private fun updateVideoPlayerUI() {
        val holder = activeVideoViewHolder ?: return
        val p = videoPlayer ?: return

        if (p.isPlaying) {
            holder.btnPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            holder.btnPlayPause.setImageResource(R.drawable.ic_play)
        }

        if (videoList.isNotEmpty() && currentVideoIndex >= 0 && currentVideoIndex < videoList.size) {
            val video = videoList[currentVideoIndex]
            holder.txtVideoTitle.text = video.title
        } else {
            holder.txtVideoTitle.text = "No Video Selected"
        }

        updateVideoProgressUI()
    }

    private fun setupVideoPage(holder: VideoViewHolder) {
        activeVideoViewHolder = holder

        holder.layoutPlayer.visibility = View.VISIBLE
        holder.layoutPlaylist.visibility = View.GONE

        holder.btnVideoPlayerBack.setOnClickListener {
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.VISIBLE
            menuView.findViewById<View>(R.id.layout_music_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_video_container).visibility = View.GONE
        }

        holder.playerView.player = videoPlayer

        lateinit var videoAdapter: VideoPlaylistAdapter
        videoAdapter = VideoPlaylistAdapter(
            onFolderClicked = { selectedFolder ->
                videoList = selectedFolder.videos
                loadVideoMediaItems()
                videoAdapter.setVideos(videoList)
            },
            onVideoClicked = { selectedVideoIndex ->
                playVideo(selectedVideoIndex)
                holder.layoutPlaylist.visibility = View.GONE
                holder.layoutPlayer.visibility = View.VISIBLE
            },
            onAllVideoClicked = { allVideoIndex ->
                videoList = allVideosList
                loadVideoMediaItems()
                playVideo(allVideoIndex)
                holder.layoutPlaylist.visibility = View.GONE
                holder.layoutPlayer.visibility = View.VISIBLE
            }
        )

        holder.playlistRecycler.layoutManager = LinearLayoutManager(service)
        holder.playlistRecycler.adapter = videoAdapter

        holder.txtEmptyPlaylist.findViewById<View>(R.id.btn_scan_video)?.setOnClickListener {
            Toast.makeText(service, "Scanning media files...", Toast.LENGTH_SHORT).show()
            service.musicManager.scanLocalSongs()
            scanLocalVideos()
        }

        holder.btnPlaylistToggle.setOnClickListener {
            holder.layoutPlayer.visibility = View.GONE
            holder.layoutPlaylist.visibility = View.VISIBLE

            if (videoFolderList.isEmpty() && allVideosList.isEmpty()) {
                holder.txtEmptyPlaylist.visibility = View.VISIBLE
                holder.playlistRecycler.visibility = View.GONE
            } else {
                holder.txtEmptyPlaylist.visibility = View.GONE
                holder.playlistRecycler.visibility = View.VISIBLE
                videoAdapter.setFoldersAndAllVideos(videoFolderList, allVideosList)
            }
        }

        holder.btnPlaylistBack.setOnClickListener {
            if (!videoAdapter.isDisplayingFolders) {
                videoAdapter.setFoldersAndAllVideos(videoFolderList, allVideosList)
            } else {
                holder.layoutPlaylist.visibility = View.GONE
                holder.layoutPlayer.visibility = View.VISIBLE
            }
        }

        holder.btnPlayPause.setOnClickListener {
            toggleVideoPlayPause()
        }

        holder.btnNext.setOnClickListener {
            playVideoNext()
        }

        holder.playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    videoPlayer?.let { p ->
                        val estimatedTime = (progress * p.duration) / 100
                        holder.txtCurrentTime.text = formatTime(estimatedTime)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingVideoSeekBar = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val p = videoPlayer ?: return
                seekBar?.let { sb ->
                    if (p.duration > 0) {
                        val seekTargetMs = (sb.progress.toLong() * p.duration) / 100
                        p.seekTo(seekTargetMs)
                    }
                }
                isUserTrackingVideoSeekBar = false
                updateVideoProgressUI()
            }
        })

        updateVideoPlayerUI()

        if (videoPlayer?.isPlaying == true) {
            startVideoProgressUpdates()
        } else {
            stopVideoProgressUpdates()
        }
    }

    fun onDestroy() {
        stopVideoProgressUpdates()
        videoPlayer?.release()
        videoPlayer = null
    }
}

class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val layoutPlayer: View = view.findViewById(R.id.layout_video_player)
    val btnVideoPlayerBack: ImageButton = view.findViewById(R.id.btn_video_player_back)
    val playerView: PlayerView = view.findViewById(R.id.video_player_view)
    val txtVideoTitle: TextView = view.findViewById(R.id.txt_video_title)
    val playerSeekBar: SeekBar = view.findViewById(R.id.video_seekbar)
    val txtCurrentTime: TextView = view.findViewById(R.id.txt_video_current_time)
    val txtTotalDuration: TextView = view.findViewById(R.id.txt_video_total_duration)
    val btnPlaylistToggle: ImageButton = view.findViewById(R.id.btn_video_playlist_toggle)
    val btnPlayPause: ImageButton = view.findViewById(R.id.btn_video_play_pause)
    val btnNext: ImageButton = view.findViewById(R.id.btn_video_next)

    val layoutPlaylist: View = view.findViewById(R.id.layout_video_playlist)
    val btnPlaylistBack: ImageButton = view.findViewById(R.id.btn_video_playlist_back)
    val playlistRecycler: RecyclerView = view.findViewById(R.id.video_playlist_recycler)
    val txtEmptyPlaylist: View = view.findViewById(R.id.txt_empty_video_playlist)
}

class VideoPlaylistAdapter(
    private val onFolderClicked: (VideoFolderModel) -> Unit,
    private val onVideoClicked: (Int) -> Unit,
    private val onAllVideoClicked: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_FOLDER = 0
    private val TYPE_VIDEO = 1

    private var currentFolders = listOf<VideoFolderModel>()
    private var allVideos = listOf<VideoModel>()
    private var folderVideos = listOf<VideoModel>()
    var isDisplayingFolders = true
        private set

    fun setFoldersAndAllVideos(folders: List<VideoFolderModel>, allVideos: List<VideoModel>) {
        this.currentFolders = folders
        this.allVideos = allVideos
        this.isDisplayingFolders = true
        notifyDataSetChanged()
    }

    fun setVideos(videos: List<VideoModel>) {
        this.folderVideos = videos
        this.isDisplayingFolders = false
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        if (isDisplayingFolders) {
            return if (position < currentFolders.size) TYPE_FOLDER else TYPE_VIDEO
        }
        return TYPE_VIDEO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOLDER) {
            val view = inflater.inflate(R.layout.folder_item, parent, false)
            FolderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.video_item, parent, false)
            VideoItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FolderViewHolder) {
            val folder = currentFolders[position]
            holder.name.text = folder.name
            holder.count.text = "${folder.videos.size} videos"
            holder.itemView.setOnClickListener { onFolderClicked(folder) }
        } else if (holder is VideoItemViewHolder) {
            if (isDisplayingFolders) {
                val videoIndex = position - currentFolders.size
                val video = allVideos[videoIndex]
                holder.title.text = video.title
                holder.duration.text = formatVideoTime(video.duration)
                holder.itemView.setOnClickListener { onAllVideoClicked(videoIndex) }
            } else {
                val video = folderVideos[position]
                holder.title.text = video.title
                holder.duration.text = formatVideoTime(video.duration)
                holder.itemView.setOnClickListener { onVideoClicked(position) }
            }
        }
    }

    private fun formatVideoTime(ms: Long): String {
        if (ms < 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    override fun getItemCount(): Int {
        return if (isDisplayingFolders) {
            currentFolders.size + allVideos.size
        } else {
            folderVideos.size
        }
    }

    class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.txt_folder_name)
        val count: TextView = view.findViewById(R.id.txt_folder_count)
    }

    class VideoItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txt_video_item_title)
        val duration: TextView = view.findViewById(R.id.txt_video_item_duration)
    }
}
