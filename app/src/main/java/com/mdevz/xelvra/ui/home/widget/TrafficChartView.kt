package com.mdevz.xelvra.ui.home.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.mdevz.xelvra.R
import kotlin.math.max

class TrafficChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val uploadColor =
        ContextCompat.getColor(
            context,
            R.color.traffic_upload
        )

    private val downloadColor =
        ContextCompat.getColor(
            context,
            R.color.traffic_download
        )

    private val uploadPaint =
        createLinePaint(uploadColor)

    private val downloadPaint =
        createLinePaint(downloadColor)

    // Paint Glow (Hanya untuk efek visual outer line tanpa ubah data)
    private val uploadGlowPaint =
        createGlowPaint(uploadColor)

    private val downloadGlowPaint =
        createGlowPaint(downloadColor)

    private val uploadFillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

    private val downloadFillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

    private val uploadMarkerPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = uploadColor
            style = Paint.Style.FILL
        }

    private val downloadMarkerPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = downloadColor
            style = Paint.Style.FILL
        }

    private val gridPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color =
                ColorUtils.setAlphaComponent(
                    ContextCompat.getColor(
                        context,
                        R.color.traffic_grid
                    ),
                    55
                )

            style = Paint.Style.STROKE
            strokeWidth = dp(1f)

            pathEffect =
                DashPathEffect(
                    floatArrayOf(
                        dp(2f),
                        dp(6f)
                    ),
                    0f
                )
        }

    private var upload:
        List<Long> = emptyList()

    private var download:
        List<Long> = emptyList()

    fun setTraffic(
        uploadHistory: List<Long>,
        downloadHistory: List<Long>
    ) {
        if (
            upload == uploadHistory &&
            download == downloadHistory
        ) {
            return
        }

        upload = uploadHistory
        download = downloadHistory
        invalidate()
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        val width =
            width.toFloat()

        val height =
            height.toFloat()

        if (
            width <= 0f ||
            height <= 0f
        ) {
            return
        }

        val left =
            dp(8f)

        val right =
            width - dp(8f)

        val top =
            dp(10f)

        // Penyesuaian margin bawah kanvas saja agar tidak menabrak Legend XML
        val bottom =
            height - dp(30f)

        if (bottom <= top) return

        drawGrid(
            canvas = canvas,
            left = left,
            right = right,
            top = top,
            bottom = bottom
        )

        val maximum =
            max(
                upload.maxOrNull() ?: 0L,
                download.maxOrNull() ?: 0L
            )
                .coerceAtLeast(1L)

        drawSeries(
            canvas = canvas,
            values = download,
            maximum = maximum,
            linePaint = downloadPaint,
            glowPaint = downloadGlowPaint,
            fillPaint = downloadFillPaint,
            color = downloadColor,
            markerPaint = downloadMarkerPaint,
            left = left,
            right = right,
            top = top,
            bottom = bottom
        )

        drawSeries(
            canvas = canvas,
            values = upload,
            maximum = maximum,
            linePaint = uploadPaint,
            glowPaint = uploadGlowPaint,
            fillPaint = uploadFillPaint,
            color = uploadColor,
            markerPaint = uploadMarkerPaint,
            left = left,
            right = right,
            top = top,
            bottom = bottom
        )
    }

    private fun drawGrid(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        val chartHeight =
            bottom - top

        for (i in 1..3) {
            val y =
                top +
                    chartHeight *
                    i.toFloat() /
                    4f

            canvas.drawLine(
                left,
                y,
                right,
                y,
                gridPaint
            )
        }
    }

    private fun drawSeries(
        canvas: Canvas,
        values: List<Long>,
        maximum: Long,
        linePaint: Paint,
        glowPaint: Paint,
        fillPaint: Paint,
        color: Int,
        markerPaint: Paint,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        if (values.isEmpty()) {
            return
        }

        val chartWidth =
            right - left

        val chartHeight =
            bottom - top

        // LOGIKA ASLI KAMU (100% DIPERTAHANKAN)
        val denominator =
            (60 - 1).coerceAtLeast(1)

        val points =
            values.mapIndexed {
                    index,
                    value ->

                val x =
                    left +
                        chartWidth *
                        index.toFloat() /
                        denominator.toFloat()

                val ratio =
                    value.toFloat() /
                        maximum.toFloat()

                val y =
                    bottom -
                        ratio
                            .coerceIn(
                                0f,
                                1f
                            ) *
                        chartHeight

                Point(
                    x = x,
                    y = y
                )
            }

        if (points.isEmpty()) {
            return
        }

        // LOGIKA KURVA ASLI KAMU
        val linePath =
            createSmoothPath(points)

        if (
            points.size > 1 &&
            values.any { it > 0L }
        ) {
            val fillPath =
                Path(linePath).apply {
                    lineTo(
                        points.last().x,
                        bottom
                    )

                    lineTo(
                        points.first().x,
                        bottom
                    )

                    close()
                }

            fillPaint.shader =
                LinearGradient(
                    0f,
                    top,
                    0f,
                    bottom,
                    ColorUtils.setAlphaComponent(
                        color,
                        55
                    ),
                    ColorUtils.setAlphaComponent(
                        color,
                        0
                    ),
                    Shader.TileMode.CLAMP
                )

            canvas.drawPath(
                fillPath,
                fillPaint
            )

            fillPaint.shader = null
        }

        // PERTUKARAN VISUAL: Menggambar efek Glow halus lalu Garis Utama
        canvas.drawPath(
            linePath,
            glowPaint
        )

        canvas.drawPath(
            linePath,
            linePaint
        )

        val lastPoint = points.last()
        canvas.drawCircle(
            lastPoint.x,
            lastPoint.y,
            dp(2.8f),
            markerPaint
        )
    }

    // LOGIKA KURVA ASLI KAMU (100% UTUH)
    private fun createSmoothPath(
        points: List<Point>
    ): Path {
        val path =
            Path()

        if (points.isEmpty()) {
            return path
        }

        path.moveTo(
            points.first().x,
            points.first().y
        )

        if (points.size == 1) {
            return path
        }

        for (i in 1 until points.size) {
            val previous =
                points[i - 1]

            val current =
                points[i]

            val middleX =
                (
                    previous.x +
                        current.x
                ) / 2f

            path.cubicTo(
                middleX,
                previous.y,
                middleX,
                current.y,
                current.x,
                current.y
            )
        }

        return path
    }

    private fun createLinePaint(
        color: Int
    ): Paint {
        return Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = dp(2.25f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    // Efek modern tambahan tanpa merusak logika data
    private fun createGlowPaint(
        color: Int
    ): Paint {
        return Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            this.color = ColorUtils.setAlphaComponent(color, 24)
            style = Paint.Style.STROKE
            strokeWidth = dp(4f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    private fun dp(
        value: Float
    ): Float {
        return value *
            resources.displayMetrics.density
    }

    private data class Point(
        val x: Float,
        val y: Float
    )
}
