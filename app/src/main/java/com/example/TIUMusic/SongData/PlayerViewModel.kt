package com.example.TIUMusic.SongData

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerViewModel : ViewModel() {
    private var _musicItem = MutableStateFlow(MusicItem("", "", "", "", 0));
    val musicItem = _musicItem.asStateFlow();

    private var _playlist : MutableStateFlow<List<MusicItem>?> = MutableStateFlow(null);
    val playlist = _playlist.asStateFlow();

    private var _currentPlaylistIndex : MutableState<Int?> = mutableStateOf(null);
    val currentPlaylistIndex : State<Int?> = _currentPlaylistIndex;

    private val _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    private val _expanded = mutableStateOf(false)
    val expanded: State<Boolean> = _expanded

    private val _offsetY = mutableStateOf(0f)
    val offsetY: State<Float> = _offsetY

    private val _duration = mutableFloatStateOf(0.0f);
    val duration: State<Float> = _duration;

    private val _currentTime = mutableFloatStateOf(0.0f);
    val currentTime : State<Float> = _currentTime;

    fun setMusicItem(item : MusicItem) {
        _musicItem.value = item;
    }

    fun setPlaylist(items : List<MusicItem>?) {
        _playlist.value = items;
    }

    fun changeSong(nextSong: Boolean) : Boolean {
        if (playlist.value != null &&
            currentPlaylistIndex.value != null &&
            currentPlaylistIndex.value!! + (if (nextSong) 1 else -1)
                in (0 .. playlist.value!!.size - 1)) {
            _currentPlaylistIndex.value = _currentPlaylistIndex.value!! + (if (nextSong) 1 else -1);
            setMusicItem(playlist.value!![currentPlaylistIndex.value!!]);
            return true;
        }
        return false;
    }

    fun shufflePlaylist() {
        _playlist.value?.shuffled();
    }

    fun setCurrentPlaylistIndex(index : Int?) {
        if (index != null && playlist.value != null) {
            _currentPlaylistIndex.value = index;
            setMusicItem(_playlist.value!![currentPlaylistIndex.value!!]);
        }
    }

    fun setCurrentTime(currTime : Float) {
        _currentTime.floatValue = currTime;
    }

    fun setDuration(duration : Float) {
        _duration.floatValue = duration;
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setExpanded(expand: Boolean) {
        _expanded.value = expand
    }

    fun updateOffset(offset: Float) {
        _offsetY.value = offset
    }
}
