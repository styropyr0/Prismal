package com.matrix.prismaltest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.matrix.prismal.PrismalFrameLayout
import com.matrix.prismal.PrismalIconButton
import com.matrix.prismal.PrismalSlider
import com.matrix.prismal.PrismalSwitch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val resList: List<Int> = listOf(
        R.drawable.bg2,
        R.drawable.bg3,
        R.drawable.bg4,
        R.drawable.bg5,
        R.drawable.bg6,
        R.drawable.bg7,
        R.drawable.bg8,
        R.drawable.bg9,
        R.drawable.bg10,
        R.drawable.bg11
    )

    private var currIndex = 0
    private lateinit var nextPrismalButton: PrismalIconButton
    private lateinit var prevPrismalButton: PrismalIconButton

    private lateinit var prismalIconButton: PrismalIconButton
    private lateinit var prismalSlider: PrismalSlider
    private lateinit var prismalSlider1: PrismalSlider
    private lateinit var prismalSwitch: PrismalSwitch

    private lateinit var prismalIconButton2: PrismalIconButton
    private lateinit var prismalIconButton3: PrismalIconButton
    private lateinit var prismalFrameLayout2: PrismalFrameLayout
    private var pickedImageUri: Uri? = null

    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 1000)
        }
    }

    private val notifFrameIds = listOf(
        R.id.notif1, R.id.notif2, R.id.notif3, R.id.notif4,
        R.id.notif5, R.id.notif6, R.id.notif7, R.id.notif8
    )
    private val notifFrames = mutableListOf<PrismalFrameLayout>()

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageUri: Uri? = result.data?.data
                imageUri?.let {
                    pickedImageUri = it
                    val rootLayout = findViewById<LinearLayout>(R.id.root)
                    rootLayout.background = contentResolver.openInputStream(it)?.use { input ->
                        android.graphics.drawable.Drawable.createFromStream(input, it.toString())
                    }
                    updateAllPrismalBackgrounds()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        tvClock = findViewById(R.id.tvClock)
        tvDate = findViewById(R.id.tvDate)

        nextPrismalButton = findViewById(R.id.next)
        prevPrismalButton = findViewById(R.id.prev)
        prismalIconButton = findViewById(R.id.prismalIconButton)
        prismalSlider = findViewById(R.id.prismalSlider)
        prismalSlider1 = findViewById(R.id.prismalSlider1)
        prismalSwitch = findViewById(R.id.prismalSwitch)

        prismalIconButton2 = findViewById(R.id.prismalIconButton2)
        prismalIconButton3 = findViewById(R.id.prismalIconButton3)
        prismalFrameLayout2 = findViewById(R.id.prismalFrame2)

        notifFrameIds.forEach { id ->
            notifFrames.add(findViewById(id))
        }

        applyPlaygroundGlassToHomeFrames()
        setListeners()
    }

    override fun onResume() {
        super.onResume()
        applyPlaygroundGlassToHomeFrames()
        updateAllPrismalBackgrounds()
        clockHandler.post(clockRunnable)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockRunnable)
    }

    private fun updateClock() {
        val timeFmt = SimpleDateFormat("h:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        val now = Date()
        tvClock.text = timeFmt.format(now)
        tvDate.text = dateFmt.format(now)
    }

    private fun applyPlaygroundGlassToHomeFrames() {
        GlassPlaygroundPrefs.applyTo(this, prismalFrameLayout2)
        notifFrames.forEach { frame ->
            GlassPlaygroundPrefs.applyTo(this, frame)
        }
    }

    private fun goToPlayground() {
        startActivity(Intent(this, GlassPlaygroundActivity::class.java))
    }

    private fun setListeners() {
        nextPrismalButton.setOnClickListener {
            currIndex = if (currIndex == resList.size - 1) 0 else currIndex + 1
            findViewById<LinearLayout>(R.id.root).setBackgroundResource(resList[currIndex])
            updateAllPrismalBackgrounds()
        }

        prevPrismalButton.setOnClickListener {
            currIndex = if (currIndex == 0) resList.size - 1 else currIndex - 1
            findViewById<LinearLayout>(R.id.root).setBackgroundResource(resList[currIndex])
            updateAllPrismalBackgrounds()
        }

        prismalSwitch.setOnToggleChangedListener { isChecked ->
            notifFrames.forEach { frame ->
                frame.visibility = if (isChecked) View.GONE else View.VISIBLE
            }
        }

        findViewById<Button>(R.id.btnGlassPlayground).setOnClickListener {
            goToPlayground()
        }

        prismalIconButton3.setOnClickListener {
            val intent = Intent(this, DragShowActivity::class.java)
            pickedImageUri?.let {
                intent.putExtra("BACKGROUND_URI", it.toString())
            } ?: run {
                intent.putExtra("BACKGROUND_RES_ID", resList[currIndex])
            }
            startActivity(intent)
        }

        prismalIconButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            imagePicker.launch(intent)
        }
    }

    private fun updateAllPrismalBackgrounds() {
        prismalIconButton.updateBackground()
        prismalSlider.updateBackground()
        prismalSlider1.updateBackground()
        prismalSwitch.updateBackground()
        prevPrismalButton.updateBackground()
        nextPrismalButton.updateBackground()
        prismalIconButton2.updateBackground()
        prismalIconButton3.updateBackground()
        prismalFrameLayout2.updateBackground()
        notifFrames.forEach { it.updateBackground() }
    }
}