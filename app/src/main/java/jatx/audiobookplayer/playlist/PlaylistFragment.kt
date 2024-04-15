package jatx.audiobookplayer.playlist

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jatx.audiobookplayer.App
import jatx.audiobookplayer.AppState
import jatx.audiobookplayer.databinding.FragmentPlaylistBinding

class PlaylistFragment : Fragment() {

    companion object {
        fun newInstance() = PlaylistFragment()
    }

    private val viewModel: PlaylistViewModel by lazy {
        ViewModelProvider(this)[PlaylistViewModel::class.java]
    }

    private lateinit var playlistFragmentBinding: FragmentPlaylistBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        playlistFragmentBinding = FragmentPlaylistBinding.inflate(inflater, container, false)

        return playlistFragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistFragmentBinding.lifecycleOwner = this
        playlistFragmentBinding.viewModel = viewModel

        viewModel.highlightablePlaylistItems.observe(viewLifecycleOwner) { list ->
            val playlistItems = list?.map { it.playlistItem }
            if (playlistItems != viewModel.lastPlaylistItems) {
                viewModel.lastPlaylistItems = playlistItems
                val lastPlaylistItem = App.settings.lastPlaylistItem
                if (playlistItems?.contains(lastPlaylistItem) == true) {
                    App.activityProvider.currentActivity?.let {
                        it.clickPlaylistItem(lastPlaylistItem)
                        AppState.needPauseFlag = true
                    }
                }
            }
        }

        val adapter = PlaylistAdapter()
        adapter.onItemClick = {
            App.activityProvider.currentActivity?.clickPlaylistItem(it.playlistItem)
        }
        playlistFragmentBinding.rvPlaylist.adapter = adapter
    }

}