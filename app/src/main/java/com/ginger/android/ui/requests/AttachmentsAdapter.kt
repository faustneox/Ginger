package com.ginger.android.ui.requests

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.ginger.android.R

class AttachmentsAdapter(
    private val onRemove: (String) -> Unit,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<AttachmentsAdapter.VH>() {

    private var items: List<String> = emptyList()

    fun setItems(list: List<String>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_attachment_preview, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uriStr = items[position]
        val uri = Uri.parse(uriStr)
        holder.image.load(uri) {
            crossfade(true)
        }

        holder.btnRemove.setOnClickListener { onRemove(uriStr) }
        holder.image.setOnClickListener { onClick(uriStr) }
    }

    override fun getItemCount(): Int = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imageAttachmentPreview)
        val btnRemove: ImageButton = view.findViewById(R.id.buttonRemoveAttachment)
    }
}
