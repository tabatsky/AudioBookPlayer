package jatx.audiobookplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import jatx.audiobookplayer.library.LibraryAdapter
import jatx.audiobookplayer.models.HighlightablePlaylistItem
import jatx.audiobookplayer.models.LibraryItem
import jatx.audiobookplayer.playlist.PlaylistAdapter

@BindingAdapter("items")
fun <T> setItems(rv: RecyclerView, list: List<T>) {
    val adapter = rv.adapter
    if (adapter is LibraryAdapter) {
        adapter.updateItems(list.map { it as LibraryItem })
    } else if (adapter is PlaylistAdapter) {
        adapter.updateItems(list.map { it as HighlightablePlaylistItem })
    }
}

@BindingAdapter("bgColor")
fun setBgColor(view: View, color: Any) {
    if (color is Number) {
        view.setBackgroundColor(color.toInt())
    }
}

fun Context.registerExportedReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
    ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
}

fun Context.showToast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}