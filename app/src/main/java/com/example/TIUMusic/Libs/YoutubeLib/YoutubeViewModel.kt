package com.example.TIUMusic.Libs.YoutubeLib

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat.startForeground
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import coil3.util.CoilUtils
import com.example.TIUMusic.Libs.Visualizer.VisualizerSettings
import com.example.TIUMusic.MainActivity
import com.example.TIUMusic.R
import com.example.TIUMusic.SongData.PlayerViewModel
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface MediaNotificationSeek {
    fun onSeek(seekTime : Float);
}


class YoutubePlayerHelper {
    var ytPlayer : YouTubePlayer? = null;
    var ytVideoTracker : YouTubePlayerTracker = YouTubePlayerTracker();
    var seekToTime : Float = 0.0f
    var mediaNotificationSeekListeners = ArrayList<MediaNotificationSeek>();

    val duration : Float get() = ytVideoTracker.videoDuration;
    val currentSecond : Float get() = ytVideoTracker.currentSecond;

    public fun addMediaNotificationSeekListener(listener : MediaNotificationSeek) {
        mediaNotificationSeekListeners.add(listener);
    }

    public fun seekTo(second : Float) {
        if (ytPlayer != null) {
            seekToTime = second;
            ytPlayer!!.seekTo(seekToTime);
            for (listener in mediaNotificationSeekListeners)
                listener.onSeek(second);
        }
    }

    public fun play() {
        ytPlayer?.play();
    }

    public fun pause() {
        seekToTime = currentSecond;
        ytPlayer?.pause();
    }
}

object YoutubeSettings {
    var NotificationEnabled = false;
}

class YoutubeViewModel(private val playerViewModel: PlayerViewModel) : ViewModel() {
    private var _mediaSession : MutableStateFlow<MediaSession?> = MutableStateFlow(null);
    val mediaSession : StateFlow<MediaSession?> = _mediaSession.asStateFlow();

    private val _ytHelper = MutableStateFlow(YoutubePlayerHelper());
    val ytHelper : StateFlow<YoutubePlayerHelper> = _ytHelper.asStateFlow();

    private var _ytPlayerView : MutableStateFlow<YouTubePlayerView?> = MutableStateFlow(null);
    val ytPlayerView : StateFlow<YouTubePlayerView?> = _ytPlayerView.asStateFlow();

    var reloadDuration : Boolean = false;

    var notificationChannel : NotificationChannel? = null;

    var notificationBuilder : Notification.Builder? = null;

    var mediaMetadata : YoutubeMetadata = YoutubeMetadata("", "");

    val mediaMetadataBuilder : MediaMetadata.Builder = MediaMetadata.Builder();

    val playbackStateBuilder : PlaybackState.Builder =
        PlaybackState.Builder()
        .setActions(availableActions)
        .setState(PlaybackState.STATE_NONE, 0, 1.0f);

    var videoDuration : Long = 0;

    companion object {
        var ___ran : Boolean = false;
        val availableActions : Long =
            (PlaybackState.ACTION_SEEK_TO
                    or PlaybackState.ACTION_PAUSE
                    or PlaybackState.ACTION_STOP
                    or PlaybackState.ACTION_PLAY
                    or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackState.ACTION_SKIP_TO_NEXT);

        private fun getState(state : PlayerConstants.PlayerState) : Int {
            return when (state) {
                PlayerConstants.PlayerState.PLAYING -> PlaybackState.STATE_PLAYING;
                PlayerConstants.PlayerState.ENDED -> PlaybackState.STATE_STOPPED;
                PlayerConstants.PlayerState.PAUSED -> PlaybackState.STATE_PAUSED;
                PlayerConstants.PlayerState.UNKNOWN -> PlaybackState.STATE_NONE;
                PlayerConstants.PlayerState.BUFFERING -> PlaybackState.STATE_BUFFERING;
                PlayerConstants.PlayerState.UNSTARTED -> PlaybackState.STATE_NONE;
                PlayerConstants.PlayerState.VIDEO_CUED -> PlaybackState.STATE_NONE;
            }
        }
    }

    init {
        assert(!___ran) {
            error("YoutubeViewModel Should only run once");
        }
        ___ran = true;
    }

    fun init(context: Context) {
        if (!VisualizerSettings.VisualizerEnabled)
            return; // Just to be sure
        _mediaSession.value = MediaSession(context, "MusicService");

        notificationChannel = createNotificationChannel(context);

        _mediaSession.value!!.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                _ytHelper.update { current ->
                    current.play();
                    current;
                }
            }

            override fun onPause() {
                _ytHelper.update { current ->
                    current.pause();
                    current;
                }
            }

            override fun onSkipToNext() {
                if (playerViewModel.changeSong(true)) {
                    val musicItem = playerViewModel.musicItem.value;
                    loadAndPlayVideo(
                        videoId = musicItem.videoId,
                        metadata = YoutubeMetadata(
                            title = musicItem.title,
                            artist = musicItem.artist,
                            artBitmapURL = musicItem.imageUrl,
                            displayTitle = musicItem.title,
                        ),
                        durationMs = 0,
                        context = context
                    )
                }
            }

            override fun onSkipToPrevious() {
                if (ytHelper.value.currentSecond >= 5) {
                    ytHelper.value.seekTo(0f);
                }
                else {
                    if (playerViewModel.changeSong(false)) {
                        val musicItem = playerViewModel.musicItem.value;
                        loadAndPlayVideo(
                            videoId = musicItem.videoId,
                            metadata = YoutubeMetadata(
                                title = musicItem.title,
                                artist = musicItem.artist,
                                artBitmapURL = musicItem.imageUrl
                            ),
                            durationMs = 0,
                            context = context
                        )
                    }
                }
            }

            override fun onSeekTo(pos: Long) {
                _ytHelper.update { current ->
                    current.seekTo(pos / 1000f);
                    current;
                }
            }
        })
    }

    fun updateYoutubePlayerView(youTubePlayerView: YouTubePlayerView) {
        _ytPlayerView.update { youTubePlayerView }
    }

    fun updateYoutubePlayer(ytPlayer : YouTubePlayer) {
        _ytHelper.update { it ->
            it.ytPlayer = ytPlayer;
            it.ytPlayer?.addListener(it.ytVideoTracker);
            it;
        }
    }

    fun updateMediaMetadata(metadata: YoutubeMetadata = mediaMetadata, durationMs: Long, context : Context) {
        if (!YoutubeSettings.NotificationEnabled)
            return;
        if (mediaMetadata == metadata)
            return;
        mediaMetadata = metadata;
        updateMediaMetadata(
            metadata.displayTitle,
            metadata.displaySubtitle,
            metadata.title,
            metadata.artist,
            durationMs,
            metadata.artBitmapURL ?: "",
            context
        )

    }

    fun checkUpdateDuration(durationMs: Long) {
        if (durationMs != 0L && videoDuration != durationMs) {
            updateVideoDuration(durationMs);
        }
    }

    fun updateVideoDuration(durationMs: Long) {
        videoDuration = durationMs;
        mediaMetadataBuilder.apply {
            putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs);
        }
        _mediaSession.update {
            it?.setMetadata(mediaMetadataBuilder.build());
            it;
        }

    }

    fun loadAndPlayVideo(
        videoId : String,
        metadata: YoutubeMetadata,
        durationMs: Long,
        context: Context
    ) {
        reloadDuration = true;
        ytHelper.value.pause();
        _ytHelper.value.ytPlayer?.loadVideo(videoId, 0f);
        updateMediaMetadata(metadata, durationMs, context);
    }

    fun updatePlaybackState(state : PlayerConstants.PlayerState, position : Long, playbackSpeed : Float) {
        if (!YoutubeSettings.NotificationEnabled)
            return;
        println(state);
        _mediaSession.update { it ->
            it!!.setPlaybackState(
                playbackStateBuilder
                    .setState(getState(state), position, playbackSpeed)
                    .build()
            )
            it;
        }
    }

    fun updatePlaybackState(state : Int, position : Long, playbackSpeed : Float) {
        if (!YoutubeSettings.NotificationEnabled)
            return;
        _mediaSession.update { it ->
            it!!.setPlaybackState(
                playbackStateBuilder
                    .setState(state, position, playbackSpeed)
                    .build()
            )
            it;
        }
    }

    fun setMediaSessionActive(active : Boolean) {
        if (!YoutubeSettings.NotificationEnabled)
            return;
        _mediaSession.update { it ->
            it!!.isActive = true;
            it;
        }
    }

    fun addMediaNotificationSeekListener(listener: MediaNotificationSeek) {
        if (!YoutubeSettings.NotificationEnabled)
            return;
        _ytHelper.update { it ->
            it.addMediaNotificationSeekListener(listener);
            it;
        }
    }


    private fun mediaNotification(
        title : String,
        artist : String,
        albumArt: Bitmap?,
        context: Context
    ) {
        if (!VisualizerSettings.VisualizerEnabled)
            return;
        Log.d("Title", title);
        notificationBuilder = Notification.Builder(context, notificationChannel!!.id)
            .setSmallIcon(R.drawable.tiumusicmark)
            .setContentTitle(title)
            .setContentText(artist)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.value!!.sessionToken))
        if (albumArt != null)
            notificationBuilder!!.setLargeIcon(albumArt);
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                println("nope");
                return@with
            }
            // notificationId is a unique int for each notification that you must define.
            notify(NotificationID, notificationBuilder!!.build());
        }
    }


    private fun updateMediaMetadata(
        displayTitle : String,
        displaySubtitle : String,
        title : String,
        artist : String,
        durationMs : Long,
        artBitmapUrl : String,
        context: Context
    ) {
        if (!YoutubeSettings.NotificationEnabled)
            return;
        mediaMetadataBuilder.apply {
            // To provide most control over how an item is displayed set the
            // display fields in the metadata
            putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, displayTitle)
            putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, displaySubtitle)
            // And at minimum the title and artist for legacy support
            putString(MediaMetadata.METADATA_KEY_TITLE, title)
            putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
        }
        // println("wtf");
        _mediaSession.update { it ->
            it!!.setMetadata(mediaMetadataBuilder.build());
            it;
        }
        if (artBitmapUrl == "")
            return;
        var imageBitmap : Bitmap? = null;
        viewModelScope.launch(Dispatchers.IO) {
            val headers = NetworkHeaders.Builder()
                .set("Cache-Control", "no-cache")
                .build()
            val request = ImageRequest.Builder(context)
                .data(artBitmapUrl)
                .httpHeaders(headers)
                .build()
            val imageLoader = ImageLoader.Builder(context).build();
            val result = imageLoader.execute(request);
            if (result is SuccessResult) {
                imageBitmap = (result.image.toBitmap());
            }
            else if (result is ErrorResult) {
                cancel(result.throwable.localizedMessage ?: "ErrorResult", result.throwable)
            }
        }.invokeOnCompletion { throwable ->
            if (throwable != null){
                Log.e("MediaMetadata", throwable.message.toString());
                return@invokeOnCompletion;
            }
            Log.d("MediaMetadata", "Load image Successful");
            mediaNotification(title, artist, imageBitmap, context);
            mediaMetadataBuilder.apply {
                if (imageBitmap != null)
                    putBitmap(MediaMetadata.METADATA_KEY_ART, imageBitmap)
            }
            _mediaSession.update { it ->
                it!!.setMetadata(mediaMetadataBuilder.build());
                it;
            }
        }

    }
}

var testBitmap : Bitmap? = null;
fun createTestBitmap(context: Context) {
    testBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.tiumusicdarkbackground);
}


val NotificationID = 0;

fun createNotificationChannel(context: Context): NotificationChannel {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is not in the Support Library.
    val name = "TIUMusic"
    val descriptionText = "Music Player"
    val importance = NotificationManager.IMPORTANCE_NONE
    val channel = NotificationChannel("Player", name, importance).apply {
        description = descriptionText
    }
    // Register the channel with the system.
    val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
    return channel;
}