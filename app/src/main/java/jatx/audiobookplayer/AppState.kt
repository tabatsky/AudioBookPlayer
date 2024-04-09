package jatx.audiobookplayer

import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import jatx.audiobookplayer.models.HighlightablePlaylistItem
import jatx.audiobookplayer.models.LibraryItem
import jatx.audiobookplayer.models.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppState {

    private val _mp3Files = MutableLiveData<List<DocumentFile>>(listOf())
    private val mp3Files: LiveData<List<DocumentFile>> = _mp3Files

    private val _activePlaylistItem = MutableLiveData<PlaylistItem?>(null)
    val activePlaylistItem: LiveData<PlaylistItem?> = _activePlaylistItem

    val libraryItems = mp3Files
        .map { list ->
            list
                .mapNotNull { documentFile ->
                    documentFile.parentFile?.name?.let {
                        LibraryItem(it)
                    }
                }
                .distinct()
                .sortedBy { it.name }
        }

    private val _playlistName = MutableLiveData("")
    val playlistName: LiveData<String> = _playlistName

    val playlistItems: LiveData<List<PlaylistItem>> = mp3Files
            .combineWith(playlistName) { files, playlist ->
                files!!
                    .filter { it.parentFile?.name == playlist }
                    .mapNotNull { documentFile ->
                        val name = documentFile.name
                        val uri = documentFile.uri
                        name?.let {
                            PlaylistItem(name, uri)
                        }
                    }
                    .sortedBy { it.name }
            }

    val highlightablePlaylistItems = playlistItems.combineWith(activePlaylistItem) { playlistItems, playlistItem ->
        playlistItems?.map {
            HighlightablePlaylistItem(it, it.name == playlistItem?.name)
        }
    }

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    fun updateIsPlaying(value: Boolean) {
        _isPlaying.value = value
    }

    fun updatePlaylistName(name: String) {
        _playlistName.value = name
    }

    fun updatePlaylistItem(playlistItem: PlaylistItem?) {
        _activePlaylistItem.value = playlistItem
    }

    suspend fun updateMp3Files(pickedDir: DocumentFile) {
        val files = withContext(Dispatchers.IO) { scanPickedDirForMp3(pickedDir) }
        _mp3Files.value = files
    }

    private fun scanPickedDirForMp3(pickedDir: DocumentFile): List<DocumentFile> {
        val result = arrayListOf<DocumentFile>()

        pickedDir.listFiles().sortedBy { it.name }.forEach {
            if (it.isDirectory) {
                result.addAll(scanPickedDirForMp3(it))
            } else if (it.isFile && it.name?.endsWith(".mp3") == true) {
                result.add(it)
            }
        }

        return result
    }
}

fun <T, K, R> LiveData<T>.combineWith(
    liveData: LiveData<K>,
    block: (T?, K?) -> R
): LiveData<R> {
    val result = MediatorLiveData<R>()
    result.addSource(this) {
        result.value = block(this.value, liveData.value)
    }
    result.addSource(liveData) {
        result.value = block(this.value, liveData.value)
    }
    return result
}