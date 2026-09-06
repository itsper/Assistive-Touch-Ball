package com.example.assistive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    private val imageMatrixInternal = Matrix()
    private val minScale = 1.0f
    private val maxScale = 5.0f
    private var currentScale = 1.0f
    private val lastTouch = PointF()

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = imageMatrixInternal

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val targetScale = currentScale * scaleFactor

                if (targetScale in minScale..maxScale) {
                    currentScale = targetScale
                    imageMatrixInternal.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                    fixTranslation()
                    imageMatrix = imageMatrixInternal
                }
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale > 1.05f) {
                    resetZoom()
                } else {
                    val targetScale = 2.5f
                    val scaleFactor = targetScale / currentScale
                    currentScale = targetScale
                    imageMatrixInternal.postScale(scaleFactor, scaleFactor, e.x, e.y)
                    fixTranslation()
                    imageMatrix = imageMatrixInternal
                }
                return true
            }
        })
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        enableFiltering()
        fitToScreen()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        enableFiltering()
        fitToScreen()
    }

    private fun enableFiltering() {
        val d = drawable
        if (d is BitmapDrawable) {
            d.paint.isFilterBitmap = true
            d.paint.isAntiAlias = true
            d.paint.isDither = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        enableFiltering()
        super.onDraw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            fitToScreen()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed && currentScale <= 1.05f) {
            fitToScreen()
        }
    }

    fun fitToScreen() {
        if (width <= 0 || height <= 0) {
            post { fitToScreen() }
            return
        }

        val d = drawable ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val drawableW = d.intrinsicWidth.toFloat()
        val drawableH = d.intrinsicHeight.toFloat()
        if (drawableW <= 0f || drawableH <= 0f) return

        imageMatrixInternal.reset()
        val scale = min(viewW / drawableW, viewH / drawableH)
        val dx = (viewW - drawableW * scale) / 2f
        val dy = (viewH - drawableH * scale) / 2f

        imageMatrixInternal.postScale(scale, scale)
        imageMatrixInternal.postTranslate(dx, dy)
        currentScale = 1.0f
        imageMatrix = imageMatrixInternal
        invalidate()
    }

    fun resetZoom() {
        currentScale = 1.0f
        fitToScreen()
    }

    private fun fixTranslation() {
        val rect = getDisplayedRect()
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        var deltaX = 0f
        var deltaY = 0f

        if (rect.width() <= viewW) {
            deltaX = (viewW - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0) deltaX = -rect.left
            if (rect.right < viewW) deltaX = viewW - rect.right
        }

        if (rect.height() <= viewH) {
            deltaY = (viewH - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0) deltaY = -rect.top
            if (rect.bottom < viewH) deltaY = viewH - rect.bottom
        }

        imageMatrixInternal.postTranslate(deltaX, deltaY)
    }

    private fun getDisplayedRect(): RectF {
        val rect = RectF()
        drawable?.let { d ->
            rect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
            imageMatrixInternal.mapRect(rect)
        }
        return rect
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouch.set(event.x, event.y)
                if (currentScale > 1.0f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentScale > 1.0f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val dx = event.x - lastTouch.x
                    val dy = event.y - lastTouch.y

                    val rect = getDisplayedRect()
                    val viewW = width.toFloat()

                    // Check if reaching edges to allow ViewPager2 swipe transitions
                    val atLeftEdge = rect.left >= 0 && dx > 0
                    val atRightEdge = rect.right <= viewW && dx < 0

                    if (atLeftEdge || atRightEdge) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }

                    imageMatrixInternal.postTranslate(dx, dy)
                    fixTranslation()
                    imageMatrix = imageMatrixInternal
                    lastTouch.set(event.x, event.y)
                } else {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
