package us.ststephens.bathroomstall

import android.os.Bundle
import android.provider.ContactsContract
import android.util.LayoutDirection
import android.util.Log
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObject

import kotlinx.android.synthetic.main.activity_main.*
import org.imperiumlabs.geofirestore.GeoFirestore
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    // Access a Cloud Firestore instance from your Activity
    val db = FirebaseFirestore.getInstance()
    val adapter = NoteListAdapter()

    var noteEntryBox: EditText? = null
    var queryRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        noteEntryBox = findViewById(R.id.note_entry_box)
        findViewById<ImageView>(R.id.send_button).setOnClickListener {
           noteEntryBox?.text?.takeIf { it.isNotBlank() }?.toString()?.let { message ->
               postNote(message)
            } ?: Snackbar.make(findViewById(android.R.id.content), "No message to send", Snackbar.LENGTH_SHORT).show()
        }

        findViewById<RecyclerView>(R.id.notes_list).let { recyclerView ->
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter
        }

        queryRegistration = db.collection("messages").addSnapshotListener { snapshot, firestoreException ->
            when {
                firestoreException != null || snapshot == null -> {} //handle the error state
                else -> {
                    val notes = ArrayList<Note>()
                    for (doc in snapshot) {
                        val note = doc.toObject(Note::class.java)
                        notes.add(note)
                    }
                    adapter.notesList = notes
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        queryRegistration?.remove()
    }

    private fun postNote(message: String) {
        val note = Note(message, 1, "stevo4932", "5350 Burnet Rd.")
        db.collection("messages").add(note)
            .addOnSuccessListener { documentReference -> messageAdded(documentReference.id) }
            .addOnFailureListener { e -> errorAddingMessage(e) }
    }

    private fun messageAdded(messageId: String) {
        noteEntryBox?.text = null
        Snackbar.make(findViewById<View>(android.R.id.content), "messageId: $messageId", Snackbar.LENGTH_SHORT).show()
        Log.d("BathroomStall", "messageId: $messageId")
    }

    private fun errorAddingMessage(e: Exception) {
        Snackbar.make(findViewById<View>(android.R.id.content), "Error adding document. See log for more info", Snackbar.LENGTH_SHORT).show()
        Log.w("BathroomStall", "Error adding document", e)
    }
}

data class Note(var message: String = "", var userId: Long = -1, var userName: String = "", var address: String = "")
