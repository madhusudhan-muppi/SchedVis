package com.example.schedvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.schedvis.ui.SchedVisApp
import com.example.schedvis.ui.theme.SchedVisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SchedVisTheme {
                SchedVisApp()
            }
        }
    }
}