package us.ststephens.bathroomstall

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity;
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.*

import kotlinx.android.synthetic.main.activity_main.*
import us.ststephens.bathroomstall.net.NetworkState
import java.lang.Exception

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: NoteListViewModel
    private var noteEntryBox: EditText? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        viewModel = ViewModelProviders.of(this)[NoteListViewModel::class.java]
        noteEntryBox = findViewById(R.id.note_entry_box)
        swipeRefreshLayout = findViewById(R.id.swiperefresh)

        findViewById<ImageView>(R.id.send_button).setOnClickListener {
           noteEntryBox?.text?.takeIf { it.isNotBlank() }?.toString()?.let { message ->
               viewModel.postNote(message).observe(this, postNoteObserver)
            } ?: Snackbar.make(findViewById(android.R.id.content), "No message to send", Snackbar.LENGTH_SHORT).show()
        }

        findViewById<RecyclerView>(R.id.notes_list).let { recyclerView ->
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = viewModel.adapter
        }

        viewModel.getNotes().observe(this, noteListObserver)
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

    private val postNoteObserver = Observer<NetworkState<DocumentReference>> { state ->
        when(state) {
            is NetworkState.Success -> state.data?.id?.let {
                messageAdded(it)
            } ?: errorAddingMessage(Exception("No note id returned"))
            is Error -> errorAddingMessage(state.error ?: Exception())
            is NetworkState.Loading -> {}
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
}

data class Note(var message: String = "", var userId: Long = -1, var userName: String = "", var address: String = "")
