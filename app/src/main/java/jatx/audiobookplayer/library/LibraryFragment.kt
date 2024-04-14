package jatx.audiobookplayer.library

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jatx.audiobookplayer.App
import jatx.audiobookplayer.databinding.FragmentLibraryBinding

class LibraryFragment : Fragment() {

    companion object {
        fun newInstance() = LibraryFragment()
    }

    private val viewModel: LibraryViewModel by lazy {
        ViewModelProvider(this)[LibraryViewModel::class.java]
    }

    private lateinit var libraryFragmentBinding: FragmentLibraryBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        libraryFragmentBinding = FragmentLibraryBinding.inflate(inflater, container, false)

        return libraryFragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        libraryFragmentBinding.lifecycleOwner = this
        libraryFragmentBinding.viewModel = viewModel

        viewModel.libraryItems.observe(viewLifecycleOwner) { list ->
            if (list != viewModel.lastLibraryItems) {
                viewModel.lastLibraryItems = list
                if (list.map { it.name }.contains(App.settings.playlistName)) {
                    App.activityProvider.currentActivity?.openPlaylist(App.settings.playlistName)
                }
            }
        }

        val adapter = LibraryAdapter()
        adapter.onItemClick = {
            App.activityProvider.currentActivity?.openPlaylist(it.name)
        }
        libraryFragmentBinding.rvLibrary.adapter = adapter
    }

}