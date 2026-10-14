package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MediaBrowserScreen
import com.example.ui.MediaBrowserViewModel
import com.example.ui.theme.ArtfluxTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MediaBrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val colorPalette by viewModel.colorPalette.collectAsStateWithLifecycle()
            ArtfluxTheme(theme = theme, colorPalette = colorPalette) {
                MediaBrowserScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
