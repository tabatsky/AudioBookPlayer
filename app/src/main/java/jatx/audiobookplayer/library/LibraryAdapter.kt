package jatx.audiobookplayer.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jatx.audiobookplayer.models.LibraryItem
import jatx.audiobookplayer.R
import jatx.audiobookplayer.databinding.ItemLibraryBinding

class LibraryAdapter: ListAdapter<LibraryItem, LibraryAdapter.VH>(LibraryItemDiffUtil()) {

    var onItemClick: ((LibraryItem) -> Unit)? = null

    fun updateItems(items: List<LibraryItem>) {
        submitList(items)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val word = getItem(position)
        holder.bindTo(word)
    }

    inner class VH(private val v: View): RecyclerView.ViewHolder(v) {
        private val binding = ItemLibraryBinding.bind(v)

        fun bindTo(libraryItem: LibraryItem) {
            binding.libraryItem = libraryItem
            v.setOnClickListener {
                onItemClick?.invoke(libraryItem)
            }
        }
    }

    class LibraryItemDiffUtil: DiffUtil.ItemCallback<LibraryItem>() {
        override fun areItemsTheSame(oldItem: LibraryItem, newItem: LibraryItem) =
            (oldItem.name == newItem.name)

        override fun areContentsTheSame(oldItem: LibraryItem, newItem: LibraryItem) =
            (oldItem == newItem)

    }
}