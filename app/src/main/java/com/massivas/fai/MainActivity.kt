package com.massivas.fai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.massivas.fai.ui.MainScreen
import com.massivas.fai.ui.MassivasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MassivasTheme {
                MainScreen()
            }
        }
    }
}
