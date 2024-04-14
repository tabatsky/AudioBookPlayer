package jatx.audiobookplayer

import android.content.Context
import android.net.Uri

private const val keyDirUri = "dirUri"
private const val keyTempo = "tempo"
private const val keyPlaylistName = "playlistName"

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
}