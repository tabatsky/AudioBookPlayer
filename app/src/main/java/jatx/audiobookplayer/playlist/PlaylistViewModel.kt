package jatx.audiobookplayer.playlist

import androidx.lifecycle.ViewModel
import jatx.audiobookplayer.AppState
import jatx.audiobookplayer.models.PlaylistItem

class PlaylistViewModel : ViewModel() {
    val playlistName = AppState.playlistName

    val highlightablePlaylistItems = AppState.highlightablePlaylistItems
    var lastPlaylistItems: List<PlaylistItem>? = null
}