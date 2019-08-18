package us.ststephens.bathroomstall

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.*
import com.google.firebase.firestore.*
import us.ststephens.bathroomstall.net.NetworkState

const val INTERVAL = 1000L * 60
const val MIN_INTERVAL = 1000L * 15
const val MIN_DISTANCE_FOR_UPDATE = 0f
const val LOCATION_PRIORITY = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
class NoteListViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    val adapter = NoteListAdapter()
    private var queryRegistration: ListenerRegistration? = null

    //location variables
    private val updatableLocation = MutableLiveData<Location?>()
    val lastKnownLocation: LiveData<Location?> = updatableLocation

    private val updatableLocationAvailability = MutableLiveData<Boolean>()
    val isLocationServiceAvailable: LiveData<Boolean> = updatableLocationAvailability

    val locationRequest: LocationRequest? = LocationRequest.create()?.apply {
        interval = INTERVAL
        fastestInterval = MIN_INTERVAL
        smallestDisplacement = MIN_DISTANCE_FOR_UPDATE
        priority = LOCATION_PRIORITY
    }

    val locationSettingsRequest: LocationSettingsRequest? = locationRequest?.let { locationRequest ->
        LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build()
    }

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(results: LocationResult?) {
            updatableLocation.value = results?.lastLocation
        }

        override fun onLocationAvailability(locationAvailability: LocationAvailability?) {
            updatableLocationAvailability.value = locationAvailability?.isLocationAvailable == true
        }
    }

    fun postNote(message: String) : LiveData<NetworkState<DocumentReference>> {
        val liveData = MutableLiveData<NetworkState<DocumentReference>>()
        liveData.value = NetworkState.Loading()
        val note = Note(message, 1, "stevo4932", "5350 Burnet Rd.")
        db.collection("messages").add(note)
            .addOnSuccessListener { documentReference ->
                liveData.postValue(NetworkState.Success(documentReference))
            }
            .addOnFailureListener { e ->
                liveData.postValue(NetworkState.Error(e))
            }
        return liveData
    }

    fun getNotes() : LiveData<NetworkState<QuerySnapshot>> {
        val liveData = MutableLiveData<NetworkState<QuerySnapshot>>()
        liveData.value = NetworkState.Loading()
        queryRegistration?.remove()
        queryRegistration = db.collection("messages").addSnapshotListener {querySnapshot, firestoreException ->
            when {
                firestoreException != null -> {
                    liveData.postValue(NetworkState.Error(firestoreException))
                }
                else -> {
                    if (querySnapshot != null) handleQueryResults(querySnapshot)
                    liveData.postValue(NetworkState.Success(querySnapshot))
                }
            }
        }
        return liveData
    }

    private fun handleQueryResults(querySnapshot: QuerySnapshot) {
        val notes = ArrayList<Note>()
        querySnapshot.forEach { doc -> doc.toObject(Note::class.java).let { note ->
            notes.add(note)
        } }
        adapter.notesList = notes
    }

    override fun onCleared() {
        super.onCleared()
        queryRegistration?.remove()
    }
}