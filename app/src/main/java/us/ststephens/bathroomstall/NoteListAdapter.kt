package us.ststephens.bathroomstall

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView

class NoteListAdapter : RecyclerView.Adapter<NoteViewHolder>() {

    var notesList: ArrayList<Note>? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = NoteViewHolder.createViewHolder(parent)

    override fun getItemCount(): Int = notesList?.size ?: 0

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        notesList?.get(position)?.let { note -> holder.onBind(note) }
    }
}

class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val toolbar: Toolbar = itemView.findViewById(R.id.toolbar)
    private val message: TextView = itemView.findViewById(R.id.message)
    private val address: TextView = itemView.findViewById(R.id.address)

    fun onBind(note: Note) {
        toolbar.title = note.userName
        message.text = note.message
        address.text = note.address
    }

    companion object {
        fun createViewHolder(parent: ViewGroup, clickListener: View.OnClickListener? = null) : NoteViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val view = layoutInflater.inflate(R.layout.note_list_item, parent, false)
            view.setOnClickListener(clickListener)
            return NoteViewHolder(view)
        }
    }
}