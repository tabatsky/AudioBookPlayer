package jatx.audiobookplayer.playlist

import androidx.lifecycle.ViewModel
import jatx.audiobookplayer.AppState

class PlaylistViewModel : ViewModel() {
    val playlistName = AppState.playlistName

    val highlightablePlaylistItems = AppState.highlightablePlaylistItems
}