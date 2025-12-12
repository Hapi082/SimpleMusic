package com.example.simplemusicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.simplemusicplayer.domain.model.Track

class TrackListAdapter(
    private val items: MutableList<Track>,
    private val onItemClick: (Track) -> Unit,
    private val onDeleteClick: (Track) -> Unit
) : RecyclerView.Adapter<TrackListAdapter.TrackViewHolder>() {

    inner class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.tv_track_title)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btn_delete_track)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false)
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = items[position]
        holder.titleText.text = track.title

        holder.itemView.setOnClickListener {
            onItemClick(track)
        }

        holder.deleteButton.setOnClickListener {
            onDeleteClick(track)
        }
    }

    override fun getItemCount(): Int = items.size

    /** リスト全体を入れ替え */
    fun submitList(newItems: List<Track>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /** 1曲追加 */
    fun addTrack(track: Track) {
        items.add(track)
        notifyItemInserted(items.lastIndex)
    }

    /** 1曲削除 */
    fun removeTrack(track: Track) {
        val index = items.indexOfFirst { it.id == track.id }
        if (index != -1) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }
}
