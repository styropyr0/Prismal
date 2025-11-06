package com.matrix.prismaltest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.matrix.prismal.PrismalFrameLayout
import com.matrix.prismal.PrismalIconButton
import com.matrix.prismal.PrismalSlider
import com.matrix.prismal.PrismalSwitch

class MainActivity : AppCompatActivity() {
    private val resList: List<Int> = listOf(
        R.drawable.bg1,
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
    private lateinit var prismalSwitch: PrismalSwitch
    private lateinit var prismalFrameLayout: PrismalFrameLayout

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageUri: Uri? = result.data?.data
                imageUri?.let {
                    val rootLayout = findViewById<LinearLayout>(R.id.root)
                    rootLayout.background = contentResolver.openInputStream(it)?.use { input ->
                        android.graphics.drawable.Drawable.createFromStream(input, it.toString())
                    }

                    prismalIconButton.updateBackground()
                    prismalSlider.updateBackground()
                    prismalSwitch.updateBackground()
                    prismalFrameLayout.updateBackground()
                    prevPrismalButton.updateBackground()
                    nextPrismalButton.updateBackground()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        nextPrismalButton = findViewById(R.id.next)
        prevPrismalButton = findViewById(R.id.prev)
        prismalIconButton = findViewById(R.id.prismalIconButton)
        prismalSlider = findViewById(R.id.prismalSlider)
        prismalSwitch = findViewById(R.id.prismalSwitch)
        prismalFrameLayout = findViewById<PrismalFrameLayout>(R.id.prismalFrame).apply {
            setShadowProperties("#3AF5F5F5".toColorInt(), 0.2f)
        }

        setListeners()
    }

    private fun setListeners() {
        nextPrismalButton.setOnClickListener {
            currIndex = if (currIndex == resList.size - 1) 0 else currIndex + 1
            findViewById<LinearLayout>(R.id.root).setBackgroundResource(resList[currIndex])

            updateAllPrismalBackgrounds(it)
        }

        prismalSwitch.setOnToggleChangedListener { isChecked->
            prismalFrameLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        prevPrismalButton.setOnClickListener {
            currIndex = if (currIndex == 0) resList.size - 1 else currIndex - 1
            findViewById<LinearLayout>(R.id.root).setBackgroundResource(resList[currIndex])

            updateAllPrismalBackgrounds(it)
        }

        prismalIconButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            imagePicker.launch(intent)
        }
    }

    private fun updateAllPrismalBackgrounds(triggeredButton: Any) {
        prismalIconButton.updateBackground()
        prismalSlider.updateBackground()
        prismalSwitch.updateBackground()
        prismalFrameLayout.updateBackground()
        prevPrismalButton.updateBackground()
        nextPrismalButton.updateBackground()

        (triggeredButton as? PrismalIconButton)?.updateBackground()
    }
}
