package us.ststephens.bathroomstall

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.appcompat.app.AppCompatActivity;
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ViewSwitcher
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
    private var viewSwitcher: ViewSwitcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        viewModel = ViewModelProviders.of(this)[NoteListViewModel::class.java]
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
    }

    private fun messageAdded(messageId: String) {
        noteEntryBox?.text = null
        Snackbar.make(findViewById(R.id.coordinator), R.string.message_posted, Snackbar.LENGTH_SHORT).show()
        Log.d("BathroomStall", "messageId: $messageId")
    }

    private fun errorAddingMessage(e: Exception) {
        Snackbar.make(findViewById<View>(R.id.coordinator), R.string.error_posting_message, Snackbar.LENGTH_SHORT).show()
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
}

data class Note(var message: String = "", var userId: Long = -1, var userName: String = "", var address: String = "")
