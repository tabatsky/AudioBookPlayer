package jatx.audiobookplayer.library

import androidx.lifecycle.ViewModel
import jatx.audiobookplayer.AppState
import jatx.audiobookplayer.models.LibraryItem

class LibraryViewModel : ViewModel() {
    val libraryItems = AppState.libraryItems
    var lastLibraryItems = listOf<LibraryItem>()
}