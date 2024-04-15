package jatx.audiobookplayer

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.NumberPicker
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import jatx.audiobookplayer.databinding.ActivityMainBinding
import jatx.audiobookplayer.library.LibraryFragmentDirections
import jatx.audiobookplayer.models.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

const val CLICK_PLAY = "jatx.audiobookplayer.CLICK_PLAY"
const val CLICK_PAUSE = "jatx.audiobookplayer.CLICK_PAUSE"
const val CLICK_NEXT = "jatx.audiobookplayer.CLICK_NEXT"
const val CLICK_PREV = "jatx.audiobookplayer.CLICK_PREV"
const val CLICK_PLUS_15 = "jatx.audiobookplayer.CLICK_PLUS_15"
const val CLICK_MINUS_15 = "jatx.audiobookplayer.CLICK_MINUS_15"
const val CLICK_PROGRESS = "jatx.audiobookplayer.CLICK_PROGRESS"
const val CLICK_PLAYLIST_ITEM = "jatx.audiobookplayer.CLICK_PLAYLIST_ITEM"
const val CLICK_TEMPO = "jatx.audiobookplayer.CLICK_TEMPO"

const val NOTIFY_PLAYLIST_CHANGED = "jatx.audiobookplayer.NOTIFY_PLAYLIST_CHANGED"

const val KEY_PROGRESS = "progress"
const val KEY_NAME = "name"
const val KEY_URI = "uri"
const val KEY_TEMPO = "tempo"

class MainActivity : FragmentActivity() {

    private lateinit var navController: NavController

    private lateinit var binding: ActivityMainBinding

    private var progressDialog: Dialog? = null

    private val viewModel: MainViewModel by viewModels()

    private var tempo: Double = App.settings.tempo
        set(value) {
            val needApply = field != value
            field = value
            App.settings.tempo = value
            binding.btnSelectTempo.text = value.toString()

            if (needApply) {
                clickTempo(value.toString())
            }
        }

    private val openDirResultLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let {
                App.settings.audioBooksDirUri = uri

                val contentResolver = applicationContext.contentResolver

                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                loadAudioBookDir(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.fragment_container)
                as NavHostFragment
        navController = navHost.navController

        checkNotificationPermissions()
        startService()

        try {
            loadAudioBookDir(App.settings.audioBooksDirUri)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        binding.btnSelectTempo.text = tempo.toString()

        binding.btnOpenDir.setOnClickListener {
            openDirResultLauncher.launch(App.settings.audioBooksDirUri)
        }

        binding.btnSelectTempo.setOnClickListener {
            showSelectTempoDialog()
        }

        binding.btnPlay.setOnClickListener {
            clickPlay()
        }

        binding.btnPause.setOnClickListener {
            clickPause()
        }

        binding.btnNext.setOnClickListener {
            clickNext()
        }

        binding.btnPrev.setOnClickListener {
            clickPrev()
        }

        binding.btnPlus15.setOnClickListener {
            clickPlus15()
        }

        binding.btnMinus15.setOnClickListener {
            clickMinus15()
        }

        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    clickProgress(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        binding.seekBar.setOnSeekBarChangeListener(seekBarListener)

        val builder = AlertDialog.Builder(this)
        builder.setView(R.layout.dialog_progress)
        progressDialog = builder.create()

        viewModel.isProgressDialogVisible.observe(this) { show ->
            showProgressDialog(show)
        }
    }

    override fun onStart() {
        super.onStart()
        App.activityProvider.currentActivity = this
    }

    override fun onStop() {
        super.onStop()
        App.activityProvider.currentActivity = null
    }

    private fun startService() {
        val intent = Intent(this, PlayerService::class.java)
        startService(intent)
    }

    fun openPlaylist(playlistName: String) {
        if (playlistName != AppState.playlistName.value) {
            notifyPlaylistChanged()
            App.settings.playlistName = playlistName
            AppState.updatePlaylistName(playlistName)
        }
        val action = LibraryFragmentDirections.actionLibraryFragmentToPlaylistFragment()
        navController.navigate(action)
    }

    private fun notifyPlaylistChanged() {
        val intent = Intent(NOTIFY_PLAYLIST_CHANGED)
        sendBroadcast(intent)
    }

    fun clickPlaylistItem(playlistItem: PlaylistItem) {
        App.settings.lastPlaylistItem = playlistItem
        val intent = Intent(CLICK_PLAYLIST_ITEM)
        intent.putExtra(KEY_NAME, playlistItem.name)
        intent.putExtra(KEY_URI, playlistItem.uri.toString())
        sendBroadcast(intent)
    }

    private fun clickPlay() {
        val intent = Intent(CLICK_PLAY)
        sendBroadcast(intent)
    }

    private fun clickPause() {
        val intent = Intent(CLICK_PAUSE)
        sendBroadcast(intent)
    }

    private fun clickNext() {
        val intent = Intent(CLICK_NEXT)
        sendBroadcast(intent)
    }

    private fun clickPrev() {
        val intent = Intent(CLICK_PREV)
        sendBroadcast(intent)
    }

    private fun clickPlus15() {
        val intent = Intent(CLICK_PLUS_15)
        sendBroadcast(intent)
    }

    private fun clickMinus15() {
        val intent = Intent(CLICK_MINUS_15)
        sendBroadcast(intent)
    }

    private fun clickProgress(progress: Int) {
        val intent = Intent(CLICK_PROGRESS)
        intent.putExtra(KEY_PROGRESS, progress)
        sendBroadcast(intent)
    }

    private fun clickTempo(tempoStr: String) {
        val intent = Intent(CLICK_TEMPO)
        intent.putExtra(KEY_TEMPO, tempoStr)
        sendBroadcast(intent)
    }

    private fun checkNotificationPermissions() {
        val permissionListener = object : PermissionListener {
            override fun onPermissionGranted() {}

            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {}
        }

        if (Build.VERSION.SDK_INT >= 33) {
            TedPermission.with(this)
                .setPermissionListener(permissionListener)
                .setPermissions(
                    Manifest.permission.POST_NOTIFICATIONS
                )
                .check()
        }
    }

    private fun showProgressDialog(show: Boolean) {
        if (show) {
            progressDialog?.show()
        } else {
            progressDialog?.dismiss()
        }
    }

    private fun showSelectTempoDialog(): Dialog? {
        val tempoStrValues =  (0..30)
            .map {
                (it - 10) * 0.05 + 1.0
            }
            .map {
                ((it * 100.0).roundToInt() / 100.0).toString()
            }
            .toTypedArray()

        val numberPicker = NumberPicker(this)
        numberPicker.maxValue = 30
        numberPicker.minValue = 0
        numberPicker.value = tempoStrValues.indexOf(tempo.toString()).takeIf { it >=0 } ?: 10
        numberPicker.wrapSelectorWheel = false
        numberPicker.displayedValues = tempoStrValues

        val builder = AlertDialog.Builder(this)
        builder.setView(numberPicker)
        builder.setTitle("Changing the Tempo")
        builder.setMessage("Choose a value :")
        builder.setPositiveButton("OK") { dialog, _ ->
            tempo = numberPicker.value
                .let {
                    (it - 10) * 0.05 + 1.0
                }
                .let {
                    ((it * 100.0).roundToInt() / 100.0)
                }
            dialog.dismiss()
        }
        builder.setNegativeButton("CANCEL") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create()
        return builder.show()
    }

    private fun loadAudioBookDir(dirUri: Uri) {
        val pickedDir = DocumentFile.fromTreeUri(this, dirUri)
        pickedDir?.let {
            lifecycleScope.launch {
                withContext(Dispatchers.Main) {
                    AppState.updateMp3Files(pickedDir)
                }
            }
        }
    }
}