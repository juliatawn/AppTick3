package com.juliacai.apptick.deviceApps

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import com.juliacai.apptick.newAppLimit.AppSearchScreen

class AppSearchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppSearchScreen()
            }
        }
    }
}
