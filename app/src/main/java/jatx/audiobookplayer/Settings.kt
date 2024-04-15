package jatx.audiobookplayer

import android.content.Context
import android.net.Uri
import jatx.audiobookplayer.models.PlaylistItem

private const val keyDirUri = "dirUri"
private const val keyTempo = "tempo"
private const val keyPlaylistName = "playlistName"
private const val keyPlaylistItemName = "playlistItemName"
private const val keyPlaylistItemUri = "playlistItemUri"
private const val keyProgress = "lastProgress"

class Settings(private val context: Context) {

    private val sp  = context.getSharedPreferences("AudioBookPlayer", 0)

    var audioBooksDirUri: Uri
        get() {
            val dirUriStr = sp.getString(keyDirUri, Uri.fromFile(context.getExternalFilesDir(null)).toString())
            return Uri.parse(dirUriStr)
        }
        set(value) {
            val dirUriStr = value.toString()
            val editor = sp.edit()
            editor.putString(keyDirUri, dirUriStr)
            editor.commit()
        }

    var tempo: Double
        get() = sp.getString(keyTempo, "1.0")?.toDouble() ?: 1.0
        set(value) {
            val editor = sp.edit()
            editor.putString(keyTempo, value.toString())
            editor.commit()
        }

    var playlistName: String
        get() = sp.getString(keyPlaylistName, "") ?: ""
        set(value) {
            val editor = sp.edit()
            editor.putString(keyPlaylistName, value)
            editor.commit()
        }

    var lastPlaylistItem: PlaylistItem
        get() {
            val name = sp.getString(keyPlaylistItemName, "") ?: ""
            val uriStr = sp.getString(keyPlaylistItemUri, "") ?: ""
            val uri = Uri.parse(uriStr)
            return PlaylistItem(name, uri)
        }
        set(value) {
            val editor = sp.edit()
            editor.putString(keyPlaylistItemName, value.name)
            editor.putString(keyPlaylistItemUri, value.uri.toString())
            editor.commit()
        }

    var lastProgress: Float
        get() = sp.getFloat(keyProgress, 0f).takeIf { !it.isNaN() } ?: 0f
        set(value) {
            val editor = sp.edit()
            editor.putFloat(keyProgress, value)
            editor.commit()
        }
}