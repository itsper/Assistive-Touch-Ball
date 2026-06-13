package com.example.assistive

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class AutoClickerManager(
    private val service: FloatingBallService,
    private val serviceScope: CoroutineScope,
    private val menuView: View
) {
    private val targetList = mutableListOf<TargetData>()
    private var selectedTargetId = -1
    private var clickerJob: Job? = null
    private var autoClickerIsRepeat = true
    internal var isAutoClickerRunning = false
    private var stopDurationOptionIndex = 0
    private val stopDurationOptions = listOf(0L, 10000L, 30000L, 60000L, 180000L, 300000L, 600000L)
    private val stopDurationLabels = listOf("Stop: Manually", "Stop: 10s", "Stop: 30s", "Stop: 1m", "Stop: 3m", "Stop: 5m", "Stop: 10m")

    fun init() {
        setupClickerPage()
    }

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
                Toast.makeText(service, "Select a target first", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(service, "Select a target first", Toast.LENGTH_SHORT).show()
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

    internal fun updateClickerMenuUI() {
        val targetLabel = menuView.findViewById<TextView>(R.id.txt_clicker_target_label) ?: return
        val intervalText = menuView.findViewById<TextView>(R.id.txt_clicker_interval) ?: return
        val modeToggleBtn = menuView.findViewById<TextView>(R.id.btn_clicker_mode_toggle) ?: return
        val stopText = menuView.findViewById<TextView>(R.id.txt_clicker_stop_duration) ?: return

        val target = targetList.find { it.id == selectedTargetId }
        if (target != null) {
            val typeLabel = if (target.type == TargetType.SWIPE) "Swipe #${target.id}" else "Target #${target.id}"
            targetLabel.text = typeLabel
            targetLabel.setTextColor(Color.parseColor("#FF00E5FF"))
            intervalText.text = "Interval: ${target.intervalMs}ms"
        } else {
            targetLabel.text = "No Target"
            targetLabel.setTextColor(Color.parseColor("#88FFFFFF"))
            intervalText.text = "Interval: --"
        }

        modeToggleBtn.text = if (autoClickerIsRepeat) "Cycle Mode: Repeatedly" else "Cycle Mode: Once"
        stopText.text = stopDurationLabels[stopDurationOptionIndex]
    }

    private fun addTargetView(type: TargetType) {
        val nextId = if (targetList.isEmpty()) 1 else targetList.maxOf { it.id } + 1
        
        val inflater = service.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val density = service.resources.displayMetrics.density
        val displayMetrics = service.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val cascadeOffset = ((nextId - 1) * 25 * density).toInt()

        val view = inflater.inflate(R.layout.floating_target_layout, null)
        val targetText = if (type == TargetType.CLICK) nextId.toString() else "S#$nextId"
        view.findViewById<TextView>(R.id.txt_target_number)?.text = targetText
        
        val targetImg = view.findViewById<ImageView>(R.id.target_image)
        targetImg.setColorFilter(Color.parseColor("#FF00E5FF"))

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

        service.windowManager.addView(view, params)

        var endView: View? = null
        var endParams: WindowManager.LayoutParams? = null

        if (type == TargetType.SWIPE) {
            endView = inflater.inflate(R.layout.floating_target_layout, null)
            endView.findViewById<TextView>(R.id.txt_target_number)?.text = "E#$nextId"
            
            val endImg = endView.findViewById<ImageView>(R.id.target_image)
            endImg.setColorFilter(Color.parseColor("#FFFF8A80"))

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

            service.windowManager.addView(endView, endParams)
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

    internal fun safeRemoveAllTargets() {
        targetList.forEach { target ->
            service.windowManager.removeView(target.view)
            if (target.type == TargetType.SWIPE && target.endView != null) {
                service.windowManager.removeView(target.endView)
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
                        val displayMetrics = service.resources.displayMetrics
                        val density = displayMetrics.density
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels

                        val newX = initialX + (event.rawX - initialTouchX).toInt()
                        val newY = initialY + (event.rawY - initialTouchY).toInt()

                        val limitX = screenWidth - (40 * density).toInt()
                        val limitY = screenHeight - (40 * density).toInt()

                        activeParams.x = newX.coerceIn(0, limitX)
                        activeParams.y = newY.coerceIn(0, limitY)

                        service.windowManager.updateViewLayout(activeView, activeParams)
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
        
        if (!service.isMenuVisible) {
            service.showMenu()
        }
        
        menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_music_container).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_video_container).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_cursor_container).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_clicker_container).visibility = View.VISIBLE
        
        updateClickerMenuUI()
        
        val selectMsg = if (target.type == TargetType.SWIPE) "Swipe #${target.id} Selected" else "Target #${target.id} Selected"
        Toast.makeText(service, selectMsg, Toast.LENGTH_SHORT).show()
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
            service.windowManager.updateViewLayout(target.view, target.params)
            if (target.type == TargetType.SWIPE && target.endView != null && target.endParams != null) {
                service.windowManager.updateViewLayout(target.endView, target.endParams)
            }
        }
    }

    private fun startAutoClicker() {
        if (isAutoClickerRunning) return
        if (targetList.isEmpty()) {
            Toast.makeText(service, "Please add a target first", Toast.LENGTH_SHORT).show()
            return
        }
        isAutoClickerRunning = true
        updateClickerStartStopButton()
        setTargetsClickable(false)

        service.closeMenu()

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
                    
                    if (!service.isMenuVisible) {
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

    internal fun stopAutoClicker() {
        isAutoClickerRunning = false
        clickerJob?.cancel()
        clickerJob = null
        updateClickerStartStopButton()
        setTargetsClickable(true)
    }

    private fun performTargetClick(target: TargetData) {
        val density = service.resources.displayMetrics.density
        val clickX = target.params.x + (20 * density).toInt()
        val clickY = target.params.y + (20 * density).toInt()
        performSimulatedClick(clickX, clickY)
    }

    private fun performSimulatedClick(x: Int, y: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val density = service.resources.displayMetrics.density
            val clickX = x + (7 * density).toInt()
            val clickY = y + (2 * density).toInt()

            val path = android.graphics.Path().apply {
                moveTo(clickX.toFloat(), clickY.toFloat())
                lineTo(clickX.toFloat(), clickY.toFloat())
            }
            val stroke = GestureDescription.StrokeDescription(
                path, 0, 100
            )
            val gesture = GestureDescription.Builder()
                .addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        }
    }

    private fun performTargetSwipe(target: TargetData) {
        val startParams = target.params
        val endParams = target.endParams ?: return
        val density = service.resources.displayMetrics.density
        
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
            val stroke = GestureDescription.StrokeDescription(
                path, 0, durationMs.coerceAtLeast(50L)
            )
            val gesture = GestureDescription.Builder()
                .addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        }
    }

    private fun updateClickerStartStopButton() {
        val startStopBtn = menuView.findViewById<TextView>(R.id.btn_clicker_start_stop) ?: return
        if (isAutoClickerRunning) {
            startStopBtn.text = "STOP"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startStopBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EF5350"))
            } else {
                startStopBtn.setBackgroundColor(Color.parseColor("#EF5350"))
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

    fun onDestroy() {
        stopAutoClicker()
        safeRemoveAllTargets()
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
