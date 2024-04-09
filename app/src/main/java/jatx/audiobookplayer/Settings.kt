package jatx.audiobookplayer

import android.content.Context
import android.net.Uri

const val keyDirUri = "dirUri"

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
}