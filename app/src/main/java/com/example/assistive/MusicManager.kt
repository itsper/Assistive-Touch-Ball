package com.example.assistive

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MusicManager(
    private val service: FloatingBallService,
    private val serviceScope: CoroutineScope,
    private val menuView: View
) {
    internal var player: ExoPlayer? = null
    private var folderList = listOf<FolderModel>()
    private var allSongsList = listOf<AudioModel>()
    private var audioList = listOf<AudioModel>()
    private var currentTrackIndex = -1
    private var activeMusicViewHolder: MusicViewHolder? = null
    private var progressJob: Job? = null
    private var isUserTrackingSeekBar = false

    fun init() {
        initPlayer()
        scanLocalSongs()
        val musicViewHolder = MusicViewHolder(menuView.findViewById(R.id.layout_music_container))
        setupMusicPage(musicViewHolder)
    }

    private fun initPlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(service).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updatePlayerUI()
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlayerUI()
                        if (isPlaying) {
                            startProgressUpdates()
                        } else {
                            stopProgressUpdates()
                        }
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val currentIdx = currentMediaItemIndex
                        if (currentIdx >= 0 && currentIdx < audioList.size) {
                            currentTrackIndex = currentIdx
                        }
                        updatePlayerUI()
                    }
                })
            }
        }
    }

    fun scanLocalSongs() {
        serviceScope.launch {
            allSongsList = AudioModel.scanLocalAudioFiles(service)
            folderList = FolderModel.scanLocalFolders(service)
            
            if (allSongsList.isNotEmpty()) {
                audioList = allSongsList
                loadMediaItems()
            }
            updatePlayerUI()

            activeMusicViewHolder?.let { holder ->
                if (allSongsList.isNotEmpty()) {
                    holder.txtEmptyPlaylist.visibility = View.GONE
                    holder.playlistRecycler.visibility = View.VISIBLE
                    val adapter = holder.playlistRecycler.adapter as? PlaylistAdapter
                    adapter?.setFoldersAndAllSongs(folderList, allSongsList)
                } else {
                    holder.txtEmptyPlaylist.visibility = View.VISIBLE
                    holder.playlistRecycler.visibility = View.GONE
                }
            }
        }
    }

    private fun loadMediaItems() {
        player?.let { p ->
            p.clearMediaItems()
            audioList.forEach { song ->
                p.addMediaItem(MediaItem.fromUri(song.uri))
            }
            p.prepare()
        }
    }

    private fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            if (audioList.isNotEmpty()) {
                if (currentTrackIndex == -1) {
                    currentTrackIndex = 0
                    p.seekTo(0, 0)
                }
                p.play()
            }
        }
    }

    private fun playNext() {
        val p = player ?: return
        if (p.hasNextMediaItem()) {
            p.seekToNext()
        } else if (audioList.isNotEmpty()) {
            p.seekTo(0, 0)
        }
        p.play()
    }

    private fun playTrack(index: Int) {
        val p = player ?: return
        if (index >= 0 && index < audioList.size) {
            currentTrackIndex = index
            p.seekTo(index, 0)
            p.play()
            updatePlayerUI()
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (true) {
                updateProgressUI()
                delay(500)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    private fun updateProgressUI() {
        val p = player ?: return
        val holder = activeMusicViewHolder
        if (holder == null) {
            stopProgressUpdates()
            return
        }
        val duration = p.duration
        val position = p.currentPosition
        
        holder.txtCurrentTime.text = formatTime(position)
        holder.txtTotalDuration.text = if (duration > 0) formatTime(duration) else "0:00"

        if (!isUserTrackingSeekBar && duration > 0) {
            holder.playerSeekBar.progress = ((position * 100) / duration).toInt()
        } else if (!isUserTrackingSeekBar) {
            holder.playerSeekBar.progress = 0
        }
    }

    private fun updatePlayerUI() {
        val holder = activeMusicViewHolder ?: return
        val p = player ?: return

        if (p.isPlaying) {
            holder.btnPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            holder.btnPlayPause.setImageResource(R.drawable.ic_play)
        }

        if (p.shuffleModeEnabled) {
            holder.btnShuffle.setColorFilter(Color.parseColor("#FFFFFF"))
        } else {
            holder.btnShuffle.setColorFilter(Color.parseColor("#88FFFFFF"))
        }

        if (audioList.isNotEmpty() && currentTrackIndex >= 0 && currentTrackIndex < audioList.size) {
            val track = audioList[currentTrackIndex]
            holder.txtTrackTitle.text = track.title
            holder.txtTrackArtist.text = track.artist
        } else {
            holder.txtTrackTitle.text = "No Song Selected"
            holder.txtTrackArtist.text = "Unknown Artist"
        }

        updateProgressUI()
    }

    private fun setupMusicPage(holder: MusicViewHolder) {
        activeMusicViewHolder = holder

        holder.layoutPlayer.visibility = View.VISIBLE
        holder.layoutPlaylist.visibility = View.GONE

        holder.btnMusicPlayerBack.setOnClickListener {
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.VISIBLE
            menuView.findViewById<View>(R.id.layout_music_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_video_container).visibility = View.GONE
        }

        lateinit var unifiedAdapter: PlaylistAdapter
        unifiedAdapter = PlaylistAdapter(
            onFolderClicked = { selectedFolder ->
                audioList = selectedFolder.songs
                loadMediaItems()
                unifiedAdapter.setSongs(audioList)
            },
            onSongClicked = { selectedSongIndex ->
                playTrack(selectedSongIndex)
                holder.layoutPlaylist.visibility = View.GONE
                holder.layoutPlayer.visibility = View.VISIBLE
            },
            onAllSongClicked = { allSongIndex ->
                audioList = allSongsList
                loadMediaItems()
                playTrack(allSongIndex)
                holder.layoutPlaylist.visibility = View.GONE
                holder.layoutPlayer.visibility = View.VISIBLE
            }
        )

        holder.playlistRecycler.layoutManager = LinearLayoutManager(service)
        holder.playlistRecycler.adapter = unifiedAdapter

        holder.txtEmptyPlaylist.findViewById<View>(R.id.btn_scan_music)?.setOnClickListener {
            Toast.makeText(service, "Scanning media files...", Toast.LENGTH_SHORT).show()
            scanLocalSongs()
            service.videoManager.scanLocalVideos()
        }

        holder.btnPlaylistToggle.setOnClickListener {
            holder.layoutPlayer.visibility = View.GONE
            holder.layoutPlaylist.visibility = View.VISIBLE

            if (folderList.isEmpty() && allSongsList.isEmpty()) {
                holder.txtEmptyPlaylist.visibility = View.VISIBLE
                holder.playlistRecycler.visibility = View.GONE
            } else {
                holder.txtEmptyPlaylist.visibility = View.GONE
                holder.playlistRecycler.visibility = View.VISIBLE
                unifiedAdapter.setFoldersAndAllSongs(folderList, allSongsList)
            }
        }

        holder.btnPlaylistBack.setOnClickListener {
            if (!unifiedAdapter.isDisplayingFolders) {
                unifiedAdapter.setFoldersAndAllSongs(folderList, allSongsList)
            } else {
                holder.layoutPlaylist.visibility = View.GONE
                holder.layoutPlayer.visibility = View.VISIBLE
            }
        }

        holder.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        holder.btnNext.setOnClickListener {
            playNext()
        }

        holder.btnShuffle.setOnClickListener {
            player?.let { p ->
                p.shuffleModeEnabled = !p.shuffleModeEnabled
                updatePlayerUI()
                val message = if (p.shuffleModeEnabled) "Shuffle: ON" else "Shuffle: OFF"
                Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
            }
        }

        holder.playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.let { p ->
                        val estimatedTime = (progress * p.duration) / 100
                        holder.txtCurrentTime.text = formatTime(estimatedTime)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingSeekBar = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val p = player ?: return
                seekBar?.let { sb ->
                    if (p.duration > 0) {
                        val seekTargetMs = (sb.progress.toLong() * p.duration) / 100
                        p.seekTo(seekTargetMs)
                    }
                }
                isUserTrackingSeekBar = false
                updateProgressUI()
            }
        })

        updatePlayerUI()

        if (player?.isPlaying == true) {
            startProgressUpdates()
        } else {
            stopProgressUpdates()
        }
    }

    fun onDestroy() {
        stopProgressUpdates()
        player?.release()
        player = null
    }
}

class MusicViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val layoutPlayer: View = view.findViewById(R.id.layout_player)
    val btnMusicPlayerBack: ImageButton = view.findViewById(R.id.btn_music_player_back)
    val imgDisc: ImageView = view.findViewById(R.id.img_disc)
    val txtTrackTitle: TextView = view.findViewById(R.id.txt_track_title)
    val txtTrackArtist: TextView = view.findViewById(R.id.txt_track_artist)
    val playerSeekBar: SeekBar = view.findViewById(R.id.player_seekbar)
    val txtCurrentTime: TextView = view.findViewById(R.id.txt_current_time)
    val txtTotalDuration: TextView = view.findViewById(R.id.txt_total_duration)
    val btnPlaylistToggle: ImageButton = view.findViewById(R.id.btn_playlist_toggle)
    val btnPlayPause: ImageButton = view.findViewById(R.id.btn_play_pause)
    val btnNext: ImageButton = view.findViewById(R.id.btn_next)
    val btnShuffle: ImageButton = view.findViewById(R.id.btn_shuffle)

    val layoutPlaylist: View = view.findViewById(R.id.layout_playlist)
    val btnPlaylistBack: ImageButton = view.findViewById(R.id.btn_playlist_back)
    val playlistRecycler: RecyclerView = view.findViewById(R.id.playlist_recycler)
    val txtEmptyPlaylist: View = view.findViewById(R.id.txt_empty_playlist)
}

class PlaylistAdapter(
    private val onFolderClicked: (FolderModel) -> Unit,
    private val onSongClicked: (Int) -> Unit,
    private val onAllSongClicked: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_FOLDER = 0
    private val TYPE_SONG = 1

    private var currentFolders = listOf<FolderModel>()
    private var allSongs = listOf<AudioModel>()
    private var folderSongs = listOf<AudioModel>()
    var isDisplayingFolders = true
        private set

    fun setFoldersAndAllSongs(folders: List<FolderModel>, allSongs: List<AudioModel>) {
        this.currentFolders = folders
        this.allSongs = allSongs
        this.isDisplayingFolders = true
        notifyDataSetChanged()
    }

    fun setSongs(songs: List<AudioModel>) {
        this.folderSongs = songs
        this.isDisplayingFolders = false
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        if (isDisplayingFolders) {
            return if (position < currentFolders.size) TYPE_FOLDER else TYPE_SONG
        }
        return TYPE_SONG
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOLDER) {
            val view = inflater.inflate(R.layout.folder_item, parent, false)
            FolderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.playlist_item, parent, false)
            SongViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FolderViewHolder) {
            val folder = currentFolders[position]
            holder.name.text = folder.name
            holder.count.text = "${folder.songs.size} tracks"
            holder.itemView.setOnClickListener { onFolderClicked(folder) }
        } else if (holder is SongViewHolder) {
            if (isDisplayingFolders) {
                val songIndex = position - currentFolders.size
                val song = allSongs[songIndex]
                holder.title.text = song.title
                holder.artist.text = song.artist
                holder.itemView.setOnClickListener { onAllSongClicked(songIndex) }
            } else {
                val song = folderSongs[position]
                holder.title.text = song.title
                holder.artist.text = song.artist
                holder.itemView.setOnClickListener { onSongClicked(position) }
            }
        }
    }

    override fun getItemCount(): Int {
        return if (isDisplayingFolders) {
            currentFolders.size + allSongs.size
        } else {
            folderSongs.size
        }
    }

    class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.txt_folder_name)
        val count: TextView = view.findViewById(R.id.txt_folder_count)
    }

    class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txt_item_title)
        val artist: TextView = view.findViewById(R.id.txt_item_artist)
    }
}
