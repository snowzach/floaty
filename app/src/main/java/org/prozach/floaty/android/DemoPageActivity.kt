package org.prozach.floaty.android

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.AppBarConfiguration
import org.prozach.floaty.android.databinding.DemoPageBinding
import kotlin.math.cos
import kotlin.math.sin

class DemoPageActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: DemoPageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DemoPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bitmap: Bitmap = Bitmap.createBitmap(700, 1400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        var baseArcPaint = Paint()
        baseArcPaint.strokeWidth = 60f
        baseArcPaint.setStyle(Paint.Style.STROKE)
        baseArcPaint.setStrokeCap(Paint.Cap.BUTT)
        baseArcPaint.setStrokeMiter(1f)

        val speedDialcolors = arrayOf(
            Paint(baseArcPaint),
            Paint(baseArcPaint),
            Paint(baseArcPaint),
            Paint(baseArcPaint),
            Paint(baseArcPaint),
            Paint(baseArcPaint),
            Paint(baseArcPaint),
            Paint(baseArcPaint),
            Paint(baseArcPaint),
            Paint(baseArcPaint),
        )
        speedDialcolors[0].color = Color.parseColor("#286726")
        speedDialcolors[1].color = Color.parseColor("#2C5E20")
        speedDialcolors[2].color = Color.parseColor("#0A8E09")
        speedDialcolors[3].color = Color.parseColor("#0E900B")
        speedDialcolors[4].color = Color.parseColor("#3DC140")
        speedDialcolors[5].color = Color.parseColor("#3CC333")
        speedDialcolors[6].color = Color.parseColor("#91B938")
        speedDialcolors[7].color = Color.parseColor("#AA8E3B")
        speedDialcolors[8].color = Color.parseColor("#A9513C")
        speedDialcolors[9].color = Color.parseColor("#AD2A3B")

        var rect = RectF(50f, 50f, 600f, 600f)
        for (i in 0..9) {
            canvas.drawArc(rect, (159 + (22*i)).toFloat(), 23f, false, speedDialcolors[i])
        }

        // Dial
        var dialPaint = Paint()
        dialPaint.strokeWidth = 10f
        dialPaint.setStyle(Paint.Style.STROKE)
        dialPaint.setStrokeCap(Paint.Cap.BUTT)
        dialPaint.color = Color.parseColor("#111111")
        canvas.drawCircle(325f, 325f, 150f, dialPaint);

        // Dial Text
        var dialTextPaint = Paint()
        dialTextPaint.setStyle(Paint.Style.FILL)
        dialTextPaint.textSize = 30f
        dialTextPaint.color = Color.parseColor("#111111")
        drawDialText(canvas, rect,"0", 215f, 160f, dialTextPaint)
        drawDialText(canvas, rect,"5", 215f, 200f, dialTextPaint)
        drawDialText(canvas, rect,"10", 215f, 243f, dialTextPaint)
        drawDialText(canvas, rect,"15", 215f, 285f, dialTextPaint)
        drawDialText(canvas, rect,"20", 215f, 331f, dialTextPaint)
        drawDialText(canvas, rect,"30", 215f, 371f, dialTextPaint)

        // Guage Text
        var guageText = Paint()
        guageText.textSize = 100f
        guageText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        guageText.color = Color.parseColor("#111111")
        canvas.drawText("5.2", 255f, 310f, guageText)

        // Guage Text
        var guageUnits = Paint()
        guageUnits.textSize = 40f
        guageUnits.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        guageUnits.color = Color.parseColor("#111111")
        canvas.drawText("mph", 285f, 365f, guageUnits)

        // Needle
        var needlePaint = Paint()
        needlePaint.setStyle(Paint.Style.FILL)
        needlePaint.color = Color.parseColor("#ff0000")
        drawNeedle(canvas, rect, 285f, 270f, 160f, 10f, needlePaint)

        binding.imageV.background = BitmapDrawable(resources, bitmap)
    }

    private fun drawNeedle(
        canvas: Canvas,
        rect: RectF,
        pointRadius: Float,
        angle: Float,
        baseRadius: Float,
        baseWidthAngle: Float,
        paint: Paint,
    ) {
        val path = Path()
        path.fillType = Path.FillType.EVEN_ODD

        val centerX = ((rect.right - rect.left)/2) + rect.left
        val centerY = ((rect.bottom - rect.top)/2) + rect.top

        val angleRadians = Math.toRadians(angle.toDouble())
        val angleRightRadians = Math.toRadians((angle+(baseWidthAngle/2)).toDouble())
        val angleLeftRadians = Math.toRadians((angle-(baseWidthAngle/2)).toDouble())

        val pointX =  ((pointRadius * cos(angleRadians)) + centerX).toFloat()
        val pointY = ((pointRadius * sin(angleRadians)) + centerY).toFloat()
        val baseXRight = ((baseRadius * cos(angleRightRadians)) + centerX).toFloat()
        val baseYRight = ((baseRadius * sin(angleRightRadians)) + centerY).toFloat()
        val baseXLeft = ((baseRadius * cos(angleLeftRadians)) + centerX).toFloat()
        val baseYLeft = ((baseRadius * sin(angleLeftRadians)) + centerY).toFloat()

        path.moveTo(pointX , pointY)
        path.lineTo(baseXRight , baseYRight)
        path.lineTo(baseXLeft , baseYLeft)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawDialText(canvas: Canvas, rect: RectF, t: String, radius: Float, angle: Float, paint: Paint) {

        val centerX = ((rect.right - rect.left)/2) + rect.left
        val centerY = ((rect.bottom - rect.top)/2) + rect.top
        val angleRadians = Math.toRadians(angle.toDouble())
        val pointX =  ((radius * cos(angleRadians)) + centerX).toFloat()
        val pointY = ((radius * sin(angleRadians)) + centerY).toFloat()
        val angleAdjusted = angle + 90f

        canvas.rotate(angleAdjusted, pointX, pointY)
        canvas.drawText(t, pointX, pointY, paint)
        canvas.rotate(0f-angleAdjusted, pointX, pointY)
    }

    private fun textAtAngle(canvas: Canvas, t: String, x: Float, y: Float, a: Float, p: Paint) {
        canvas.rotate(a, x, y)
        canvas.drawText(t, x, y, p)
        canvas.rotate(0f-a, x, y)
    }

}

