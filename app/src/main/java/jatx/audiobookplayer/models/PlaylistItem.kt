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
    val mmr = MediaMetadataRetriever()
    mmr.setDataSource(App.activityProvider.currentActivity?.applicationContext!!, this)
    val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    val duration = durationStr!!.toInt()

    return duration
}
