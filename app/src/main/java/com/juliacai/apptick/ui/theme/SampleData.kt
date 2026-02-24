package com.juliacai.apptick.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.juliacai.apptick.R

@Composable
fun getSampleAppIcon(): Painter {
    return painterResource(id = R.drawable.ic_launcher_foreground)
}
