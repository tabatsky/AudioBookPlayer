package jatx.audiobookplayer.playlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jatx.audiobookplayer.models.HighlightablePlaylistItem
import jatx.audiobookplayer.R
import jatx.audiobookplayer.databinding.ItemPlaylistBinding

class PlaylistAdapter: ListAdapter<HighlightablePlaylistItem, PlaylistAdapter.VH>(HighlightablePlaylistItemDiffUtil()) {

    var onItemClick: ((HighlightablePlaylistItem) -> Unit)? = null

    fun updateItems(items: List<HighlightablePlaylistItem>) {
        submitList(items)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val word = getItem(position)
        holder.bindTo(word)
    }

    inner class VH(private val v: View): RecyclerView.ViewHolder(v) {
        private val binding = ItemPlaylistBinding.bind(v)

        fun bindTo(highlightablePlaylistItem: HighlightablePlaylistItem) {
            binding.highlightablePlaylistItem = highlightablePlaylistItem
            v.setOnClickListener {
                onItemClick?.invoke(highlightablePlaylistItem)
            }
        }
    }

    class HighlightablePlaylistItemDiffUtil: DiffUtil.ItemCallback<HighlightablePlaylistItem>() {
        override fun areItemsTheSame(oldItem: HighlightablePlaylistItem, newItem: HighlightablePlaylistItem) =
            (oldItem.playlistItem.name == newItem.playlistItem.name)

        override fun areContentsTheSame(oldItem: HighlightablePlaylistItem, newItem: HighlightablePlaylistItem) =
            (oldItem == newItem)

    }
}