package com.juliacai.apptick.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import com.juliacai.apptick.BaseActivity

class ColorPickerActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ColorPickerScreen(
                onBackClick = { finish() }
            )
        }
    }
}
