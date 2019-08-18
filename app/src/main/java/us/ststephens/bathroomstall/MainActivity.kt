package us.ststephens.bathroomstall

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.appcompat.app.AppCompatActivity;
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ViewSwitcher
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.*

import kotlinx.android.synthetic.main.activity_main.*
import us.ststephens.bathroomstall.net.NetworkState
import java.lang.Exception

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: NoteListViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var noteEntryBox: EditText? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var viewSwitcher: ViewSwitcher? = null
    private var locationUpdatesAllowed = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        viewModel = ViewModelProviders.of(this)[NoteListViewModel::class.java]
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        noteEntryBox = findViewById(R.id.note_entry_box)
        swipeRefreshLayout = findViewById(R.id.swiperefresh)
        viewSwitcher = findViewById(R.id.send_control_switcher)
        viewSwitcher?.displayedChild = 0


        findViewById<ImageView>(R.id.send_button).let { sendButton ->
            toggleSendButton(sendButton, noteEntryBox?.text?.isNotEmpty() == true)
            noteEntryBox?.addTextChangedListener(MessageChangeListener(sendButton))
            sendButton.setOnClickListener {
                noteEntryBox?.text?.takeIf { it.isNotBlank() }?.toString()?.let { message ->
                    viewModel.postNote(message).observe(this, postNoteObserver)
                } ?: Snackbar.make(findViewById(R.id.coordinator), R.string.error_empty_message, Snackbar.LENGTH_SHORT).show()
            }
        }

        findViewById<RecyclerView>(R.id.notes_list).let { recyclerView ->
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = viewModel.adapter
        }

        viewModel.getNotes().observe(this, noteListObserver)

        viewModel.isLocationServiceAvailable.observe(this, Observer { isAvailable ->
            Log.d("Stall", "Location Available? $isAvailable")
        })

        viewModel.lastKnownLocation.observe(this, Observer { location ->
            Snackbar.make(findViewById(R.id.coordinator), "Location recorded: $location", Snackbar.LENGTH_INDEFINITE).show()
            Log.d("Stall", "Location recorded: $location")
        })
    }

    override fun onResume() {
        super.onResume()
        if (locationUpdatesAllowed) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun messageAdded(messageId: String) {
        noteEntryBox?.text = null
        Snackbar.make(findViewById(R.id.coordinator), R.string.message_posted, Snackbar.LENGTH_SHORT).show()
        Log.d("BathroomStall", "messageId: $messageId")
    }

    private fun errorAddingMessage(e: Exception) {
        Snackbar.make(findViewById(R.id.coordinator), R.string.error_posting_message, Snackbar.LENGTH_SHORT).show()
        Log.w("BathroomStall", "Error adding document", e)
    }

    inner class MessageChangeListener(private val view: View):  TextWatcher {
        override fun afterTextChanged(s: Editable?) {}

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            toggleSendButton(view, !s.isNullOrBlank())
        }
    }

    private fun toggleSendButton(view: View, isEnabled: Boolean) {
        view.isEnabled = isEnabled
        view.alpha = if (isEnabled) 1f else .65f
    }

    private fun startLocationUpdates() {
        val permission =  Manifest.permission.ACCESS_FINE_LOCATION
        if (checkLocationPermission(permission)) {
            fetchLocationUpdates()
        } else {
            requestLocationPermission(permission)
        }
    }

    private fun checkLocationPermission(permission: String) =
        ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission(permission: String) {
        locationUpdatesAllowed = false
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            //Show our reason for requesting these permissions
            AlertDialog.Builder(this)
                .setTitle(R.string.permissions_required)
                .setMessage(R.string.location_permission_reason)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                    ActivityCompat.requestPermissions(this, arrayOf(permission), REQ_LOCATION_PERMISSION)
                }
                .show()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQ_LOCATION_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            REQ_LOCATION_PERMISSION -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    locationUpdatesAllowed = true
                    fetchLocationUpdates()
                } else if (grantResults.isNotEmpty()) {
                    //Permission Denied. Notify the user that this permission is required to continue
                   AlertDialog.Builder(this)
                        .setTitle(R.string.permissions_required)
                        .setMessage(R.string.location_permission_denied)
                        .setPositiveButton(R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                            showDeviceSettings()
                        }
                        .show()
                }
            }
            //otherwise they were interrupted
        }
    }

    @Throws(SecurityException::class)
    private fun fetchLocationUpdates() {
        viewModel.locationRequest?.let { request ->
            checkLocationServices()
                .addOnSuccessListener {
                    fusedLocationClient.requestLocationUpdates(request, viewModel.locationCallback, Looper.myLooper())
                }
                .addOnFailureListener { exception ->
                    if (exception is ResolvableApiException) try {
                        exception.startResolutionForResult(this, REQ_CHECK_SETTINGS) //todo handle the result
                    } catch (sendEx: IntentSender.SendIntentException) {
                        //todo: Let the user know they can't use the app because they don't support our location update
                    }
                }
        } //todo: Let the user know they can't use the app because they don't support our location update

    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(viewModel.locationCallback)
    }

    private fun checkLocationServices() : Task<LocationSettingsResponse> {
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        return client.checkLocationSettings(viewModel.locationSettingsRequest)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == REQ_CHECK_SETTINGS) {
            startLocationUpdates()
        } else if (requestCode == REQ_SETTINGS) {
            locationUpdatesAllowed = true
        }
    }

    private val postNoteObserver = Observer<NetworkState<DocumentReference>> { state ->
        when(state) {
            is NetworkState.Success -> {
                noteEntryBox?.isEnabled = true
                viewSwitcher?.displayedChild = 0
                state.data?.id?.let {
                    messageAdded(it)
                } ?: errorAddingMessage(Exception(getString(R.string.error_no_note_id)))
            }
            is Error -> {
                noteEntryBox?.isEnabled = true
                viewSwitcher?.displayedChild = 0
                errorAddingMessage(state.error ?: Exception())
            }
            is NetworkState.Loading -> {
                noteEntryBox?.isEnabled = false
                viewSwitcher?.displayedChild = 1
            }
        }
    }

    private val noteListObserver = Observer<NetworkState<QuerySnapshot>> { state ->
        when(state) {
            is NetworkState.Success -> {
                swipeRefreshLayout?.let {
                    it.isRefreshing = false
                    it.isEnabled = false
                }
            }
            is NetworkState.Loading -> {
                swipeRefreshLayout?.let {
                    it.isEnabled = true
                    it.isRefreshing = true
                }
            }
            is NetworkState.Error -> swipeRefreshLayout?.isRefreshing = false
        }
    }

    private fun showDeviceSettings() {
        // Build intent that displays the App settings screen.
        val uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
        val intent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivityForResult(intent, REQ_SETTINGS)
    }

    companion object {
        private const val REQ_CHECK_SETTINGS = 5
        private const val REQ_LOCATION_PERMISSION = 6
        private const val REQ_SETTINGS = 7
    }
}

data class Note(var message: String = "", var userId: Long = -1, var userName: String = "", var address: String = "")
