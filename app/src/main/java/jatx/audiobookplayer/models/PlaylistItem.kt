package jatx.audiobookplayer.models

import android.net.Uri

data class PlaylistItem(
    val name: String,
    val uri: Uri
)

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
