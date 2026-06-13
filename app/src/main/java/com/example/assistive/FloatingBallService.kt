package com.example.assistive

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingBallService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    private lateinit var menuView: View
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: TextView

    private lateinit var ballParams: WindowManager.LayoutParams
    private lateinit var menuParams: WindowManager.LayoutParams

    private var isFlashlightOn = false
    private var isBallVisible = false
    private var isMenuVisible = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var actionMap: Map<Int, () -> Unit>

    // Media3 player & coroutine variables
    private var player: ExoPlayer? = null
    private var folderList = listOf<FolderModel>() // Tracks grouped directories
    private var allSongsList = listOf<AudioModel>() // Cache of all scanned local songs
    private var audioList = listOf<AudioModel>()   // Tracks actively playing folder list contents
    private var currentTrackIndex = -1
    private var activeMusicViewHolder: MusicViewHolder? = null

    // Video player & coroutine variables
    private var videoPlayer: ExoPlayer? = null
    private var videoFolderList = listOf<VideoFolderModel>()
    private var allVideosList = listOf<VideoModel>()
    private var videoList = listOf<VideoModel>()
    private var currentVideoIndex = -1
    private var activeVideoViewHolder: VideoViewHolder? = null
    private var videoProgressJob: Job? = null
    private var isUserTrackingVideoSeekBar = false

    // Virtual Mouse Cursor variables
    private var cursorView: View? = null
    private var isCursorVisible = false
    private lateinit var cursorParams: WindowManager.LayoutParams

    // Auto Clicker variables
    private val targetList = mutableListOf<TargetData>()
    private var selectedTargetId = -1
    private var clickerJob: Job? = null
    private var autoClickerIntervalMs = 500L
    private var autoClickerIsRepeat = true
    private var isAutoClickerRunning = false
    private var stopDurationOptionIndex = 0
    private val stopDurationOptions = listOf(0L, 10000L, 30000L, 60000L, 180000L, 300000L, 600000L)
    private val stopDurationLabels = listOf("Stop: Manually", "Stop: 10s", "Stop: 30s", "Stop: 1m", "Stop: 3m", "Stop: 5m", "Stop: 10m")

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var progressJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_STOP_SERVICE" -> {
                disableSelf()
                return START_NOT_STICKY
            }
            "ACTION_UPDATE_PREFS" -> {
                if (isBallVisible) {
                    applyLiveSettings()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        ballView = inflater.inflate(R.layout.floating_ball_layout, null)
        menuView = inflater.inflate(R.layout.floating_menu_layout, null)

        viewPager = menuView.findViewById(R.id.menu_viewpager)
        pageIndicator = menuView.findViewById(R.id.txt_page_indicator)

        setupActionMap()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        ballParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or   // ADD THIS
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        setupOutsideTouchDismiss()
        setupBallTouchListener()

        // Apply custom sizes, opacities, and selected character skin
        applyLiveSettings()
        addBallView()

        // Initialize player and scan files
        initPlayer()
        scanLocalSongs()
        initVideoPlayer()
        scanLocalVideos()

        val musicViewHolder = MusicViewHolder(menuView.findViewById(R.id.layout_music_container))
        val videoViewHolder = VideoViewHolder(menuView.findViewById(R.id.layout_video_container))
        setupMusicPage(musicViewHolder)
        setupVideoPage(videoViewHolder)
        setupTouchpadPage()
        setupClickerPage()
    }

    // --- UPGRADED TO HANDLE THE CHARACTER SKIN UPDATES LIVE ---
    private fun applyLiveSettings() {
        val prefs = getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE)
        val savedSizeDp = prefs.getFloat("ball_size", 60f)
        val savedOpacity = prefs.getFloat("ball_opacity", 0.8f)

        // Fetch the active chosen skin file identifier
        val savedIconKey = prefs.getString("ball_icon_key", "chopper") ?: "chopper"

        // Map key strings safely back to your exact drawable resource IDs
        val selectedResId = when (savedIconKey) {
            "luffy"     -> R.drawable.luffy
            "zoro"      -> R.drawable.zoro
            "sanji"     -> R.drawable.sanji
            "nami"      -> R.drawable.nami
            "ussop"     -> R.drawable.ussop
            "robin"     -> R.drawable.robin
            "franky"    -> R.drawable.franky
            "brook"     -> R.drawable.brook
            "jimbe"     -> R.drawable.jimbe
            "law"       -> R.drawable.law
            "strawhat"  -> R.drawable.strawhat
            "strawhat1" -> R.drawable.strawhat1
            else        -> R.drawable.chopper // Fallback safe default
        }

        // Convert DP to Pixels dynamically based on device display density profile
        val density = resources.displayMetrics.density
        val sizeInPx = (savedSizeDp * density).toInt()

        // Locate and modify dimensions and skin asset resource on your inner view
        val ballImage = ballView.findViewById<View>(R.id.ball_image)
        if (ballImage != null) {
            // 1. Swap image resource background to your selected character
            ballImage.setBackgroundResource(selectedResId)

            // 2. Adjust dimensional sizing safely
            val layoutParams = ballImage.layoutParams
            layoutParams.width = sizeInPx
            layoutParams.height = sizeInPx
            ballImage.layoutParams = layoutParams

            // 3. Apply transparency setting
            ballImage.alpha = savedOpacity
        }

        // Force layout engine refresh update onto window hierarchy safely
        if (isBallVisible) {
            windowManager.updateViewLayout(ballView, ballParams)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        safeRemoveBall()
        safeRemoveMenu()
        safeRemoveAllTargets()
        stopAutoClicker()

        // Release Media3 player resources
        stopProgressUpdates()
        player?.release()
        player = null

        stopVideoProgressUpdates()
        videoPlayer?.release()
        videoPlayer = null

        serviceJob.cancel()
    }

    private fun addBallView() {
        if (!isBallVisible) { windowManager.addView(ballView, ballParams); isBallVisible = true }
    }

    private fun safeRemoveBall() {
        if (isBallVisible) { windowManager.removeView(ballView); isBallVisible = false }
    }

    private fun addMenuView() {
        if (!isMenuVisible) {
            menuParams.alpha = 1f
            windowManager.addView(menuView, menuParams)
            isMenuVisible = true
        }
    }

    private fun safeRemoveMenu() {
        if (isMenuVisible) {
            windowManager.removeView(menuView)
            isMenuVisible = false
            safeRemoveCursor()
            menuParams.alpha = 1f
        }
    }

    private fun addCursorView() {
        if (cursorView == null) {
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            cursorView = inflater.inflate(R.layout.floating_cursor_layout, null)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            cursorParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = screenWidth / 2
                y = screenHeight / 2
            }
        }

        if (!isCursorVisible) {
            windowManager.addView(cursorView, cursorParams)
            isCursorVisible = true
        }
    }

    private fun safeRemoveCursor() {
        if (isCursorVisible && cursorView != null) {
            windowManager.removeView(cursorView)
            isCursorVisible = false
        }
    }

    private fun performSimulatedClick(x: Int, y: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val density = resources.displayMetrics.density
            val clickX = x + (7 * density).toInt()
            val clickY = y + (2 * density).toInt()

            val path = android.graphics.Path().apply {
                moveTo(clickX.toFloat(), clickY.toFloat())
                lineTo(clickX.toFloat(), clickY.toFloat())
            }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
                path, 0, 100  // increased from 50 to 100ms
            )
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        }
    }

    private fun setupTouchpadPage() {
        val backBtn = menuView.findViewById<View>(R.id.btn_cursor_page_back)
        backBtn.setOnClickListener {
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.VISIBLE
            menuView.findViewById<View>(R.id.layout_cursor_container).visibility = View.GONE
            safeRemoveCursor()
        }

        val clickBtn = menuView.findViewById<View>(R.id.btn_touchpad_click)
        clickBtn.setOnClickListener {
            if (isCursorVisible && ::cursorParams.isInitialized) {
                val clickX = cursorParams.x
                val clickY = cursorParams.y
                // Briefly hide menu so it doesn't block the gesture, then restore
                windowManager.updateViewLayout(menuView, menuParams.apply {
                    alpha = 0f
                })
                mainHandler.postDelayed({
                    performSimulatedClick(clickX, clickY)
                    mainHandler.postDelayed({
                        if (isMenuVisible) {
                            menuParams.alpha = 1f
                            windowManager.updateViewLayout(menuView, menuParams)
                        }
                    }, 120)
                }, 60)
            }
        }

        val sensor = menuView.findViewById<View>(R.id.touchpad_sensor)

        var lastTouchX = 0f
        var lastTouchY = 0f
        var startX = 0f
        var startY = 0f
        var startTime = 0L
        var isDrag = false

        sensor.setOnTouchListener { _, event ->
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val density = displayMetrics.density

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    lastTouchX = event.x
                    lastTouchY = event.y
                    startTime = System.currentTimeMillis()
                    isDrag = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    val dragThreshold = 12 * density
                    if (kotlin.math.abs(event.x - startX) > dragThreshold || kotlin.math.abs(event.y - startY) > dragThreshold) {
                        isDrag = true
                    }
                    if (isCursorVisible && ::cursorParams.isInitialized) {
                        val speedMultiplier = 1.8f
                        cursorParams.x = (cursorParams.x + dx * speedMultiplier).toInt().coerceIn(0, screenWidth)
                        cursorParams.y = (cursorParams.y + dy * speedMultiplier).toInt().coerceIn(0, screenHeight)
                        windowManager.updateViewLayout(cursorView, cursorParams)
                    }
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    true
                }
                else -> false
            }
        }
    }

    private fun setupOutsideTouchDismiss() {
        menuView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                closeMenu()
                true
            } else {
                false
            }
        }
    }

    private fun setupBallTouchListener() {
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var clickStartTime = 0L

        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = ballParams.x; initialY = ballParams.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    clickStartTime = System.currentTimeMillis(); true
                }
                MotionEvent.ACTION_MOVE -> {
                    ballParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    ballParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(ballView, ballParams); true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - clickStartTime
                    val movedX = abs(event.rawX - initialTouchX)
                    val movedY = abs(event.rawY - initialTouchY)
                    if (elapsed < 250 && movedX < 12 && movedY < 12) showMenu()
                    true
                }
                else -> false
            }
        }
    }

    private fun closeMenuThenDo(delayMs: Long = 120L, action: () -> Unit) {
        safeRemoveMenu()
        addBallView()
        mainHandler.postDelayed(action, delayMs)
    }

    private fun closeMenu() {
        safeRemoveMenu()
        addBallView()
    }

    private fun setupActionMap() {
        val map = mutableMapOf<Int, () -> Unit>()

        map[R.id.btn_home]       = { performGlobalAction(GLOBAL_ACTION_HOME); closeMenu() }
        map[R.id.btn_back]       = { closeMenuThenDo(120L) { performGlobalAction(GLOBAL_ACTION_BACK) } }
        map[R.id.btn_recents]    = { performGlobalAction(GLOBAL_ACTION_RECENTS); closeMenu() }
        map[R.id.btn_screenshot] = {
            closeMenuThenDo(200L) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
        }
        map[R.id.btn_brightness] = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
                val cr = contentResolver
                val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 125)
                val next = when { current < 80 -> 130; current < 180 -> 255; else -> 30 }
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, next)
            }
            closeMenu()
        }
        map[R.id.btn_rotate] = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
                val cr = contentResolver
                val state = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (state == 1) 0 else 1)
            }
            closeMenu()
        }
        map[R.id.btn_wifi] = {
            closeMenuThenDo(100L) {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                else
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
        map[R.id.btn_data] = {
            closeMenuThenDo(100L) {
                startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        map[R.id.btn_bluetooth] = {
            closeMenuThenDo(100L) {
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            }
        }
        map[R.id.btn_airplane] = {
            closeMenuThenDo(100L) {
                startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        map[R.id.btn_hotspot] = {
            closeMenuThenDo(100L) {
                startActivity(Intent("android.settings.TETHER_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        map[R.id.btn_notification] = {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            closeMenu()
        }
        map[R.id.btn_onehanded] = {
            closeMenuThenDo(100L) {
                if (Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_BUTTON)
                val intent = Intent("android.settings.ONE_HANDED_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
        }
        map[R.id.btn_volume] = {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            closeMenu()
        }
        map[R.id.btn_flashlight] = {
            try {
                val cam = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                isFlashlightOn = !isFlashlightOn
                cam.setTorchMode(cam.cameraIdList[0], isFlashlightOn)
            } catch (e: Exception) { e.printStackTrace() }
            closeMenu()
        }
        map[R.id.btn_music] = {
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_music_container).visibility = View.VISIBLE
            menuView.findViewById<View>(R.id.layout_video_container).visibility = View.GONE
        }
        map[R.id.btn_video] = {
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_music_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_video_container).visibility = View.VISIBLE
        }
        map[R.id.btn_cursor] = {
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_music_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_video_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_cursor_container).visibility = View.VISIBLE
            addCursorView()
        }
        map[R.id.btn_clicker] = {
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_music_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_video_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_cursor_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_clicker_container).visibility = View.VISIBLE
            updateClickerMenuUI()
        }
        map[R.id.btn_close] = { closeMenu() }

        actionMap = map
    }

    private fun showMenu() {
        // Reset visibilities when menu is displayed
        menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.VISIBLE
        menuView.findViewById<View>(R.id.layout_music_container).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_video_container).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_cursor_container).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_clicker_container).visibility = View.GONE

        val prefs = getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE)
        val defaultOrder = "btn_home,btn_back,btn_recents,btn_screenshot,btn_volume,btn_flashlight,btn_notification,btn_brightness,btn_rotate,btn_wifi,btn_data,btn_bluetooth,btn_airplane,btn_hotspot,btn_onehanded,btn_music,btn_video,btn_cursor,btn_clicker"
        val savedOrder = prefs.getString("tool_order", defaultOrder) ?: defaultOrder
        val orderedKeys = savedOrder.split(",").filter { it.isNotEmpty() }

        val keyToIdMap = mapOf(
            "btn_home"         to R.id.btn_home,
            "btn_back"         to R.id.btn_back,
            "btn_recents"      to R.id.btn_recents,
            "btn_screenshot"   to R.id.btn_screenshot,
            "btn_volume"       to R.id.btn_volume,
            "btn_flashlight"   to R.id.btn_flashlight,
            "btn_notification" to R.id.btn_notification,
            "btn_brightness"   to R.id.btn_brightness,
            "btn_rotate"       to R.id.btn_rotate,
            "btn_wifi"         to R.id.btn_wifi,
            "btn_data"         to R.id.btn_data,
            "btn_bluetooth"    to R.id.btn_bluetooth,
            "btn_airplane"     to R.id.btn_airplane,
            "btn_hotspot"      to R.id.btn_hotspot,
            "btn_onehanded"    to R.id.btn_onehanded,
            "btn_music"        to R.id.btn_music,
            "btn_video"        to R.id.btn_video,
            "btn_cursor"       to R.id.btn_cursor,
            "btn_clicker"      to R.id.btn_clicker
        )

        val activeResIds = mutableListOf<Int>()
        val processedKeys = mutableSetOf<String>()

        orderedKeys.forEach { key ->
            processedKeys.add(key)
            val enabledByDefault = ALL_TOOLS.find { it.key == key }?.enabledByDefault ?: false
            val isEnabled = prefs.getBoolean(key, enabledByDefault)
            val resId = keyToIdMap[key]
            if (resId != null && isEnabled) {
                activeResIds.add(resId)
            }
        }

        // Handle ALL_TOOLS unmapped elements check if available
        ALL_TOOLS.forEach { tool ->
            if (tool.key !in processedKeys) {
                val isEnabled = prefs.getBoolean(tool.key, tool.enabledByDefault)
                val resId = keyToIdMap[tool.key]
                if (resId != null && isEnabled) {
                    activeResIds.add(resId)
                }
            }
        }

        activeResIds.add(R.id.btn_close)
        val pages = activeResIds.chunked(6)
        val totalPagesCount = pages.size

        viewPager.adapter = MenuPagerAdapter(pages, actionMap)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pageIndicator.text = "${position + 1} / $totalPagesCount"
                pageIndicator.visibility = if (totalPagesCount > 1) View.VISIBLE else View.GONE
            }
        })

        viewPager.setCurrentItem(0, false)

        val displayMetrics = resources.displayMetrics
        val screenWidth  = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density      = displayMetrics.density

        val menuWidthPx  = (220 * density).toInt()
        val menuHeightPx = (234 * density).toInt()
        val ballSizePx   = (60  * density).toInt()
        val edgePadPx    = (8   * density).toInt()

        val ballCenterX = ballParams.x + ballSizePx / 2
        val isOnLeftSide = ballCenterX < screenWidth / 2

        val targetX = if (isOnLeftSide) edgePadPx else screenWidth - menuWidthPx - edgePadPx
        var targetY = ballParams.y - (menuHeightPx / 2) + (ballSizePx / 2)
        targetY = targetY.coerceIn(edgePadPx, screenHeight - menuHeightPx - edgePadPx)

        menuParams.x = targetX
        menuParams.y = targetY

        safeRemoveBall()
        addMenuView()
    }

    private fun startMyForegroundService() {
        val channelId = "assistive_ball_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Assistive Touch", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Assistive Ball Active")
            .setContentText("Running overlay controls")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    // --- Media3 ExoPlayer Controls ---
    private fun initPlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(this).build().apply {
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

    private fun scanLocalSongs() {
        serviceScope.launch {
            // Fetch flat song list
            allSongsList = AudioModel.scanLocalAudioFiles(this@FloatingBallService)
            
            // Fetch grouped system folder directory configurations asynchronously
            folderList = FolderModel.scanLocalFolders(this@FloatingBallService)
            
            // Default safe baseline fallback setup (Load all songs initially)
            if (allSongsList.isNotEmpty()) {
                audioList = allSongsList
                loadMediaItems()
            }
            updatePlayerUI()

            // Refresh UI and list if currently displaying empty state
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

    private var isUserTrackingSeekBar = false

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
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

        // Update Play/Pause button graphic state
        if (p.isPlaying) {
            holder.btnPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            holder.btnPlayPause.setImageResource(R.drawable.ic_play)
        }

        // Update Shuffle Button Highlight State
        if (p.shuffleModeEnabled) {
            holder.btnShuffle.setColorFilter(android.graphics.Color.parseColor("#FFFFFF"))
        } else {
            holder.btnShuffle.setColorFilter(android.graphics.Color.parseColor("#88FFFFFF"))
        }

        // Update track titles and details
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

        // Initialize our unified layout adapter engine matching handlers
        lateinit var unifiedAdapter: PlaylistAdapter
        unifiedAdapter = PlaylistAdapter(
            onFolderClicked = { selectedFolder ->
                // 1. User tapped a folder! Swapping actively playing queue context cleanly
                audioList = selectedFolder.songs
                loadMediaItems() // Loads tracks into ExoPlayer internally
                
                // 2. Feed nested song lists into the recycler layout display view
                unifiedAdapter.setSongs(audioList)
            },
            onSongClicked = { selectedSongIndex ->
                // User tapped a song inside that folder! Trigger track playback execution state loop
                playTrack(selectedSongIndex)
                
                // Collapse panel interface straight back out to main active music overlay frame cleanly
                holder.layoutPlaylist.visibility = View.GONE
                holder.layoutPlayer.visibility = View.VISIBLE
            },
            onAllSongClicked = { allSongIndex ->
                // User tapped a song in the "All Songs" list!
                // Swap actively playing queue to ALL scanned songs:
                audioList = allSongsList
                loadMediaItems()
                playTrack(allSongIndex)
                
                holder.layoutPlaylist.visibility = View.GONE
                holder.layoutPlayer.visibility = View.VISIBLE
            }
        )

        holder.playlistRecycler.layoutManager = LinearLayoutManager(this@FloatingBallService)
        holder.playlistRecycler.adapter = unifiedAdapter

        // Bind scan button in empty playlist view
        holder.txtEmptyPlaylist.findViewById<View>(R.id.btn_scan_music)?.setOnClickListener {
            Toast.makeText(this@FloatingBallService, "Scanning media files...", Toast.LENGTH_SHORT).show()
            scanLocalSongs()
            scanLocalVideos()
        }

        // Playlist panel access toggle button
        holder.btnPlaylistToggle.setOnClickListener {
            holder.layoutPlayer.visibility = View.GONE
            holder.layoutPlaylist.visibility = View.VISIBLE

            if (folderList.isEmpty() && allSongsList.isEmpty()) {
                holder.txtEmptyPlaylist.visibility = View.VISIBLE
                holder.playlistRecycler.visibility = View.GONE
            } else {
                holder.txtEmptyPlaylist.visibility = View.GONE
                holder.playlistRecycler.visibility = View.VISIBLE
                
                // Always launch with high-level folder list layout and all songs list below it
                unifiedAdapter.setFoldersAndAllSongs(folderList, allSongsList)
            }
        }

        // Dynamic Multi-Level Back Button Interception Action
        holder.btnPlaylistBack.setOnClickListener {
            if (!unifiedAdapter.isDisplayingFolders) {
                // If viewing tracks inside a directory, drop back to the top-level folders and all songs listing
                unifiedAdapter.setFoldersAndAllSongs(folderList, allSongsList)
            } else {
                // If already at folders index level, completely return to the main overlay widget panel
                holder.layoutPlaylist.visibility = View.GONE
                holder.layoutPlayer.visibility = View.VISIBLE
            }
        }

        // play controls
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
                Toast.makeText(this@FloatingBallService, message, Toast.LENGTH_SHORT).show()
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

        // Refresh dynamic UI elements
        updatePlayerUI()

        // Sync local running progress callbacks
        if (player?.isPlaying == true) {
            startProgressUpdates()
        } else {
            stopProgressUpdates()
        }
    }

    // --- Media3 Video ExoPlayer Controls ---
    private fun initVideoPlayer() {
        if (videoPlayer == null) {
            videoPlayer = ExoPlayer.Builder(this).build().apply {
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

    private fun scanLocalVideos() {
        serviceScope.launch {
            allVideosList = VideoModel.scanLocalVideoFiles(this@FloatingBallService)
            videoFolderList = VideoFolderModel.scanLocalFolders(this@FloatingBallService)
            if (allVideosList.isNotEmpty()) {
                videoList = allVideosList
                loadVideoMediaItems()
            }
            updateVideoPlayerUI()

            // Refresh UI and list if currently displaying empty state
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

        // Bind PlayerView to Player instance
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

        holder.playlistRecycler.layoutManager = LinearLayoutManager(this@FloatingBallService)
        holder.playlistRecycler.adapter = videoAdapter

        // Bind scan button in empty playlist view
        holder.txtEmptyPlaylist.findViewById<View>(R.id.btn_scan_video)?.setOnClickListener {
            Toast.makeText(this@FloatingBallService, "Scanning media files...", Toast.LENGTH_SHORT).show()
            scanLocalSongs()
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

    // --- Video Playlist RecyclerView Adapter ---
    private class VideoPlaylistAdapter(
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
                PlaylistAdapter.FolderViewHolder(view)
            } else {
                val view = inflater.inflate(R.layout.video_item, parent, false)
                VideoItemViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is PlaylistAdapter.FolderViewHolder) {
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
            return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
        }

        override fun getItemCount(): Int {
            return if (isDisplayingFolders) {
                currentFolders.size + allVideos.size
            } else {
                folderVideos.size
            }
        }

        class VideoItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.txt_video_item_title)
            val duration: TextView = view.findViewById(R.id.txt_video_item_duration)
        }
    }

    // --- Custom ViewPager2 Adapters ---
    private inner class MenuPagerAdapter(
        private val pages: List<List<Int>>,
        private val actions: Map<Int, () -> Unit>
    ) : RecyclerView.Adapter<GridViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = inflater.inflate(R.layout.floating_menu_page, parent, false)
            return GridViewHolder(view)
        }

        override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
            holder.gridLayout.removeAllViews()

            val context = holder.itemView.context
            val density = context.resources.displayMetrics.density
            val widthPx  = (90 * density).toInt()
            val heightPx = (60 * density).toInt()
            val marginPx = (4  * density).toInt()

            val inflater = LayoutInflater.from(context)
            val fullTemplate = inflater.inflate(R.layout.floating_menu_page, null) as ViewGroup
            val targetedGrid = fullTemplate.findViewById<GridLayout>(R.id.page_grid)

            pages[position].forEach { resId ->
                val button = targetedGrid.findViewById<View>(resId)
                if (button != null) {
                    targetedGrid.removeView(button)
                    button.visibility = View.VISIBLE
                    button.setOnClickListener { actions[resId]?.invoke() }

                    val params = GridLayout.LayoutParams().apply {
                        width  = widthPx
                        height = heightPx
                        setMargins(marginPx, marginPx, marginPx, marginPx)
                    }
                    holder.gridLayout.addView(button, params)
                }
            }
        }

        override fun getItemCount(): Int = pages.size
    }

    private class GridViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val gridLayout: GridLayout = view.findViewById(R.id.page_grid)
    }

    private class MusicViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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

    private class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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

    // --- Playlist RecyclerView Adapter ---
    private class PlaylistAdapter(
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

    // --- AUTO CLICKER METHODS ---
    private fun setupClickerPage() {
        val backBtn = menuView.findViewById<View>(R.id.btn_clicker_page_back)
        backBtn.setOnClickListener {
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.VISIBLE
            menuView.findViewById<View>(R.id.layout_clicker_container).visibility = View.GONE
        }

        val addTargetBtn = menuView.findViewById<View>(R.id.btn_clicker_add_target)
        addTargetBtn.setOnClickListener {
            addTargetView(TargetType.CLICK)
        }

        val addSwipeBtn = menuView.findViewById<View>(R.id.btn_clicker_add_swipe)
        addSwipeBtn.setOnClickListener {
            addTargetView(TargetType.SWIPE)
        }

        val removeTargetBtn = menuView.findViewById<View>(R.id.btn_clicker_remove_target)
        removeTargetBtn.setOnClickListener {
            stopAutoClicker()
            safeRemoveAllTargets()
        }

        val minusIntervalBtn = menuView.findViewById<View>(R.id.btn_clicker_interval_minus)
        val plusIntervalBtn = menuView.findViewById<View>(R.id.btn_clicker_interval_plus)

        minusIntervalBtn.setOnClickListener {
            val target = targetList.find { it.id == selectedTargetId }
            if (target != null) {
                if (target.intervalMs > 100L) {
                    target.intervalMs -= 100L
                } else if (target.intervalMs > 10L) {
                    target.intervalMs -= 10L
                }
                updateClickerMenuUI()
            } else {
                Toast.makeText(this, "Select a target first", Toast.LENGTH_SHORT).show()
            }
        }

        plusIntervalBtn.setOnClickListener {
            val target = targetList.find { it.id == selectedTargetId }
            if (target != null) {
                if (target.intervalMs < 100L) {
                    target.intervalMs += 10L
                } else {
                    target.intervalMs += 100L
                }
                updateClickerMenuUI()
            } else {
                Toast.makeText(this, "Select a target first", Toast.LENGTH_SHORT).show()
            }
        }

        val modeToggleBtn = menuView.findViewById<TextView>(R.id.btn_clicker_mode_toggle)
        modeToggleBtn.setOnClickListener {
            autoClickerIsRepeat = !autoClickerIsRepeat
            updateClickerMenuUI()
        }

        val minusStopBtn = menuView.findViewById<View>(R.id.btn_clicker_stop_minus)
        val plusStopBtn = menuView.findViewById<View>(R.id.btn_clicker_stop_plus)
        val stopText = menuView.findViewById<TextView>(R.id.txt_clicker_stop_duration)

        minusStopBtn.setOnClickListener {
            if (stopDurationOptionIndex > 0) {
                stopDurationOptionIndex--
                stopText.text = stopDurationLabels[stopDurationOptionIndex]
            }
        }

        plusStopBtn.setOnClickListener {
            if (stopDurationOptionIndex < stopDurationOptions.lastIndex) {
                stopDurationOptionIndex++
                stopText.text = stopDurationLabels[stopDurationOptionIndex]
            }
        }

        val startStopBtn = menuView.findViewById<TextView>(R.id.btn_clicker_start_stop)
        startStopBtn.setOnClickListener {
            if (isAutoClickerRunning) {
                stopAutoClicker()
            } else {
                startAutoClicker()
            }
        }

        updateClickerMenuUI()
    }

    private fun updateClickerMenuUI() {
        val targetLabel = menuView.findViewById<TextView>(R.id.txt_clicker_target_label) ?: return
        val intervalText = menuView.findViewById<TextView>(R.id.txt_clicker_interval) ?: return
        val modeToggleBtn = menuView.findViewById<TextView>(R.id.btn_clicker_mode_toggle) ?: return
        val stopText = menuView.findViewById<TextView>(R.id.txt_clicker_stop_duration) ?: return

        val target = targetList.find { it.id == selectedTargetId }
        if (target != null) {
            val typeLabel = if (target.type == TargetType.SWIPE) "Swipe #${target.id}" else "Target #${target.id}"
            targetLabel.text = typeLabel
            targetLabel.setTextColor(android.graphics.Color.parseColor("#FF00E5FF"))
            intervalText.text = "Interval: ${target.intervalMs}ms"
        } else {
            targetLabel.text = "No Target"
            targetLabel.setTextColor(android.graphics.Color.parseColor("#88FFFFFF"))
            intervalText.text = "Interval: --"
        }

        modeToggleBtn.text = if (autoClickerIsRepeat) "Cycle Mode: Repeatedly" else "Cycle Mode: Once"
        stopText.text = stopDurationLabels[stopDurationOptionIndex]
    }

    private fun addTargetView(type: TargetType) {
        val nextId = if (targetList.isEmpty()) 1 else targetList.maxOf { it.id } + 1
        
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val density = resources.displayMetrics.density
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val cascadeOffset = ((nextId - 1) * 25 * density).toInt()

        // Create click target or swipe start target
        val view = inflater.inflate(R.layout.floating_target_layout, null)
        val targetText = if (type == TargetType.CLICK) nextId.toString() else "S#$nextId"
        view.findViewById<TextView>(R.id.txt_target_number)?.text = targetText
        
        val targetImg = view.findViewById<ImageView>(R.id.target_image)
        // Set Cyan color for Click / Swipe Start
        targetImg.setColorFilter(android.graphics.Color.parseColor("#FF00E5FF"))

        val targetX = (screenWidth / 2 - (20 * density).toInt() + cascadeOffset).coerceIn(0, screenWidth - (40 * density).toInt())
        val targetY = (screenHeight / 2 - (20 * density).toInt() + cascadeOffset).coerceIn(0, screenHeight - (40 * density).toInt())

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetX
            y = targetY
        }

        windowManager.addView(view, params)

        var endView: View? = null
        var endParams: WindowManager.LayoutParams? = null

        if (type == TargetType.SWIPE) {
            // Create Swipe End target
            endView = inflater.inflate(R.layout.floating_target_layout, null)
            endView.findViewById<TextView>(R.id.txt_target_number)?.text = "E#$nextId"
            
            val endImg = endView.findViewById<ImageView>(R.id.target_image)
            // Set soft Red/Orange color for Swipe End
            endImg.setColorFilter(android.graphics.Color.parseColor("#FFFF8A80"))

            val endXVal = (targetX + (60 * density).toInt()).coerceIn(0, screenWidth - (40 * density).toInt())
            val endYVal = targetY

            endParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = endXVal
                y = endYVal
            }

            windowManager.addView(endView, endParams)
        }

        val target = TargetData(nextId, type, view, params, endView, endParams)
        setupTargetTouchListener(target, isEndView = false)
        if (type == TargetType.SWIPE) {
            setupTargetTouchListener(target, isEndView = true)
        }
        
        targetList.add(target)
        selectedTargetId = target.id
        updateClickerMenuUI()
    }

    private fun safeRemoveAllTargets() {
        targetList.forEach { target ->
            windowManager.removeView(target.view)
            if (target.type == TargetType.SWIPE && target.endView != null) {
                windowManager.removeView(target.endView)
            }
        }
        targetList.clear()
        selectedTargetId = -1
        updateClickerMenuUI()
    }

    private fun setupTargetTouchListener(target: TargetData, isEndView: Boolean = false) {
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var startTime = 0L

        val activeView = if (isEndView) target.endView else target.view
        val activeParams = if (isEndView) target.endParams else target.params

        if (activeView == null || activeParams == null) return

        activeView.setOnTouchListener { _, event ->
            if (isAutoClickerRunning) {
                false
            } else {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = activeParams.x
                        initialY = activeParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        startTime = System.currentTimeMillis()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val displayMetrics = resources.displayMetrics
                        val density = displayMetrics.density
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels

                        val newX = initialX + (event.rawX - initialTouchX).toInt()
                        val newY = initialY + (event.rawY - initialTouchY).toInt()

                        val limitX = screenWidth - (40 * density).toInt()
                        val limitY = screenHeight - (40 * density).toInt()

                        activeParams.x = newX.coerceIn(0, limitX)
                        activeParams.y = newY.coerceIn(0, limitY)

                        windowManager.updateViewLayout(activeView, activeParams)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - startTime
                        val distanceX = abs(event.rawX - initialTouchX)
                        val distanceY = abs(event.rawY - initialTouchY)
                        
                        if (duration < 250 && distanceX < 10 && distanceY < 10) {
                            selectTargetAndShowMenu(target)
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun selectTargetAndShowMenu(target: TargetData) {
        selectedTargetId = target.id
        
        if (!isMenuVisible) {
            showMenu()
        }
        
        menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_music_container).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_video_container).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_cursor_container).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_clicker_container).visibility = View.VISIBLE
        
        updateClickerMenuUI()
        
        val selectMsg = if (target.type == TargetType.SWIPE) "Swipe #${target.id} Selected" else "Target #${target.id} Selected"
        Toast.makeText(this, selectMsg, Toast.LENGTH_SHORT).show()
    }

    private fun setTargetsClickable(clickable: Boolean) {
        targetList.forEach { target ->
            if (clickable) {
                target.params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                if (target.type == TargetType.SWIPE && target.endParams != null && target.endView != null) {
                    target.endParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                }
            } else {
                target.params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                if (target.type == TargetType.SWIPE && target.endParams != null && target.endView != null) {
                    target.endParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                }
            }
            windowManager.updateViewLayout(target.view, target.params)
            if (target.type == TargetType.SWIPE && target.endView != null && target.endParams != null) {
                windowManager.updateViewLayout(target.endView, target.endParams)
            }
        }
    }

    private fun startAutoClicker() {
        if (isAutoClickerRunning) return
        if (targetList.isEmpty()) {
            Toast.makeText(this, "Please add a target first", Toast.LENGTH_SHORT).show()
            return
        }
        isAutoClickerRunning = true
        updateClickerStartStopButton()
        setTargetsClickable(false)

        closeMenu()

        val durationLimit = stopDurationOptions[stopDurationOptionIndex]

        clickerJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            while (isAutoClickerRunning) {
                if (durationLimit > 0L && System.currentTimeMillis() - startTime >= durationLimit) {
                    stopAutoClicker()
                    break
                }
                
                for (target in targetList) {
                    if (!isAutoClickerRunning) break
                    
                    delay(target.intervalMs)
                    
                    if (durationLimit > 0L && System.currentTimeMillis() - startTime >= durationLimit) {
                        break
                    }
                    
                    if (!isMenuVisible) {
                        if (target.type == TargetType.CLICK) {
                            performTargetClick(target)
                        } else if (target.type == TargetType.SWIPE) {
                            performTargetSwipe(target)
                        }
                    }
                }
                
                if (!autoClickerIsRepeat) {
                    stopAutoClicker()
                    break
                }
            }
        }
    }

    private fun stopAutoClicker() {
        isAutoClickerRunning = false
        clickerJob?.cancel()
        clickerJob = null
        updateClickerStartStopButton()
        setTargetsClickable(true)
    }

    private fun performTargetClick(target: TargetData) {
        val density = resources.displayMetrics.density
        val clickX = target.params.x + (20 * density).toInt()
        val clickY = target.params.y + (20 * density).toInt()
        performSimulatedClick(clickX, clickY)
    }

    private fun performTargetSwipe(target: TargetData) {
        val startParams = target.params
        val endParams = target.endParams ?: return
        val density = resources.displayMetrics.density
        
        val startX = startParams.x + (20 * density).toInt()
        val startY = startParams.y + (20 * density).toInt()
        val endX = endParams.x + (20 * density).toInt()
        val endY = endParams.y + (20 * density).toInt()

        performSimulatedSwipe(startX, startY, endX, endY, 300L)
    }

    private fun performSimulatedSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 300L) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = android.graphics.Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
                path, 0, durationMs.coerceAtLeast(50L)
            )
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        }
    }

    private fun updateClickerStartStopButton() {
        val startStopBtn = menuView.findViewById<TextView>(R.id.btn_clicker_start_stop) ?: return
        if (isAutoClickerRunning) {
            startStopBtn.text = "STOP"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startStopBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#EF5350"))
            } else {
                startStopBtn.setBackgroundColor(android.graphics.Color.parseColor("#EF5350"))
            }
        } else {
            startStopBtn.text = "START"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startStopBtn.backgroundTintList = null
            } else {
                startStopBtn.setBackgroundResource(R.drawable.btn_item_bg)
            }
        }
    }
}

// --- Target Types ---
enum class TargetType {
    CLICK,
    SWIPE
}

// --- Helper Data Model for Multi-Target Auto Clicker ---
data class TargetData(
    val id: Int,
    val type: TargetType,
    val view: View,
    val params: WindowManager.LayoutParams,
    val endView: View? = null,
    val endParams: WindowManager.LayoutParams? = null,
    var intervalMs: Long = 500L
)