package com.example.assistive

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class TouchpadManager(
    private val service: FloatingBallService,
    private val menuView: View
) {
    internal var cursorView: View? = null
    internal var isCursorVisible = false
    internal lateinit var cursorParams: WindowManager.LayoutParams

    fun init() {
        setupTouchpadPage()
    }

    internal fun addCursorView() {
        if (cursorView == null) {
            val inflater = service.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            cursorView = inflater.inflate(R.layout.floating_cursor_layout, null)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val displayMetrics = service.resources.displayMetrics
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
            service.windowManager.addView(cursorView, cursorParams)
            isCursorVisible = true
        }
    }

    internal fun safeRemoveCursor() {
        if (isCursorVisible && cursorView != null) {
            service.windowManager.removeView(cursorView)
            isCursorVisible = false
        }
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
                service.windowManager.updateViewLayout(menuView, service.menuParams.apply {
                    alpha = 0f
                })
                service.mainHandler.postDelayed({
                    performSimulatedClick(clickX, clickY)
                    service.mainHandler.postDelayed({
                        if (service.isMenuVisible) {
                            service.menuParams.alpha = 1f
                            service.windowManager.updateViewLayout(menuView, service.menuParams)
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
        var isDrag = false

        sensor.setOnTouchListener { _, event ->
            val displayMetrics = service.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val density = displayMetrics.density

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    lastTouchX = event.x
                    lastTouchY = event.y
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
                        service.windowManager.updateViewLayout(cursorView, cursorParams)
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

    fun onDestroy() {
        safeRemoveCursor()
    }
}
