package com.example.TIUMusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.TIUMusic.Libs.Visualizer.VisualizerViewModel
import com.example.TIUMusic.Libs.Visualizer.ensureVisualizerPermissionAllowed
import com.example.TIUMusic.Libs.YoutubeLib.YoutubeViewModel
import com.example.TIUMusic.Libs.YoutubeLib.Ytmusic
import com.example.TIUMusic.Libs.YoutubeLib.createNotificationChannel
import com.example.TIUMusic.Libs.YoutubeLib.ensurePlayerNotificationPermissionAllowed
import com.example.TIUMusic.Libs.YoutubeLib.models.YouTubeClient
import com.example.TIUMusic.Screens.NowPlayingSheet
import com.example.TIUMusic.SongData.PlayerViewModel
import com.example.TIUMusic.ui.theme.TIUMusicTheme
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.client.call.body
import kotlinx.coroutines.runBlocking


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureVisualizerPermissionAllowed(this);
        ensurePlayerNotificationPermissionAllowed(this);
        createNotificationChannel(this);
        val playerViewModel = PlayerViewModel() // Could be a bad idea
        val visualizerViewModel = VisualizerViewModel()
        val youtubeViewModel = YoutubeViewModel(this)
        setContent {
            TIUMusicTheme {
                NavHost(
                    playerViewModel = playerViewModel,
                    visualizerViewModel = visualizerViewModel,
                    youtubeViewModel = youtubeViewModel
                )
            }
        }
    }
}
