package com.example.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QueueAdapter(
    private val songList: MutableList<SongData>,
    private val onItemClick: (Int) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    class QueueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvSongName)
        val tvArtist: TextView = view.findViewById(R.id.tvArtist)
        val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_queue_song, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val song = songList[position]
        holder.tvName.text = song.name
        holder.tvArtist.text = "- ${song.artist}"

        holder.itemView.setOnClickListener { onItemClick(position) }
        holder.btnDelete.setOnClickListener { onDeleteClick(position) }
    }

    override fun getItemCount() = songList.size

    // 提供给外部调用的删除方法
    fun removeItem(position: Int) {
        if (position in 0 until songList.size) {
            songList.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, songList.size - position)
        }
    }
}