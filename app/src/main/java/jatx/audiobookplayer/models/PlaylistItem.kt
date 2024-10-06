package jatx.audiobookplayer.models

import android.media.MediaMetadataRetriever
import android.net.Uri
import jatx.audiobookplayer.App
import jatx.audiobookplayer.millisToTimeString

data class PlaylistItem(
    val name: String,
    val uri: Uri
) {
    private val duration = uri.getAudioDuration()
    val durationStr = duration.millisToTimeString()
    val title = uri.getAudioTitle() ?: name
}

val colorTransparent = 0x00000000
val colorBlue = 0xFF000099

data class HighlightablePlaylistItem(
    val playlistItem: PlaylistItem,
    val highlighted: Boolean
) {
    val color = if (highlighted) colorBlue else colorTransparent

    override fun equals(other: Any?) = (other is HighlightablePlaylistItem) &&
            (other.playlistItem == playlistItem) &&
            (other.highlighted == highlighted)

    override fun hashCode() = 1024 * playlistItem.hashCode() + if (highlighted) 1 else 0
}

fun Uri.getAudioDuration(): Int {
    return try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(App.activityProvider.currentActivity?.applicationContext!!, this)
        val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val duration = durationStr!!.toInt()

        duration
    } catch (e: Exception) {
        0
    }
}

fun Uri.getAudioTitle(): String? {
    return try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(App.activityProvider.currentActivity?.applicationContext!!, this)
        val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)

        val cyrillic = 'а'..'я'
        val latin = 'a'..'z'
        val digits = '0'..'9'
        val special = listOf(' ', '.', ',', '-', '—')

        val symbolSet = title?.lowercase()?.toCharArray()?.toSet()?.toMutableSet()
        val isCorrect = symbolSet?.let {
            // TODO: use filter
            cyrillic.forEach {
                symbolSet.remove(it)
            }
            latin.forEach {
                symbolSet.remove(it)
            }
            digits.forEach {
                symbolSet.remove(it)
            }
            special.forEach {
                symbolSet.remove(it)
            }
            symbolSet.isEmpty()
        } ?: false

        if (isCorrect) title else null
    } catch (e: Exception) {
        null
    }
}