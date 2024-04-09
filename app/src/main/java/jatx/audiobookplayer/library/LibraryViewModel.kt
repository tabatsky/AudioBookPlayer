package jatx.audiobookplayer.library

import androidx.lifecycle.ViewModel
import jatx.audiobookplayer.AppState

class LibraryViewModel : ViewModel() {
    val libraryItems = AppState.libraryItems
}