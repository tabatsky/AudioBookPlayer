package jatx.audiobookplayer

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map

class MainViewModel: ViewModel() {
    val playBtnVisibility = AppState.isPlaying
        .map { if (it) View.GONE else View.VISIBLE }
    val pauseBtnVisibility = AppState.isPlaying
        .map { if (it) View.VISIBLE else View.GONE }

    val currentPosition = AppState.currentPosition
    val duration = AppState.duration
}