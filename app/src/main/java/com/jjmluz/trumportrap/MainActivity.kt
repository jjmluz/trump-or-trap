package com.jjmluz.trumportrap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jjmluz.trumportrap.ui.TrumpOrTrapApp
import com.jjmluz.trumportrap.ui.theme.TrumpOrTrapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrumpOrTrapTheme {
                TrumpOrTrapApp()
            }
        }
    }
}
