package com.ginger.android.ui.requests

import android.text.TextUtils
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ginger.android.R
import com.ginger.android.data.local.RequestEntity
import com.ginger.android.databinding.ItemRequestBinding

class RequestListAdapter : ListAdapter<RequestEntity, RequestListAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var showExtraDetails = true
    private var listener: OnItemClickListener? = null

    interface OnItemClickListener {
        fun onItemClick(request: RequestEntity)
    }

    fun setShowExtraDetails(showExtraDetails: Boolean) {
        this.showExtraDetails = showExtraDetails
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, showExtraDetails, listener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val payload = payloads[0]
            if ("STATUS_CHANGED" == payload) {
                val item = getItem(position)
                holder.animateStatusChange(item)
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    class ViewHolder(private val binding: ItemRequestBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RequestEntity, showExtraDetails: Boolean, listener: OnItemClickListener?) {
            binding.textTitle.text = item.title

            if (showExtraDetails) {
                binding.textCategory.visibility = android.view.View.VISIBLE
                binding.textDate.visibility = android.view.View.VISIBLE

                val category = if (TextUtils.isEmpty(item.category))
                    binding.root.context.getString(R.string.default_category)
                else
                    item.category
                binding.textCategory.text = category

                binding.textDate.text = formatDate(binding.root.context, item.createdAt)
            } else {
                binding.textCategory.visibility = android.view.View.GONE
                binding.textDate.visibility = android.view.View.GONE
            }

            binding.textStatus.text = item.status
            binding.textStatus.setBackgroundResource(RequestEntity.statusChipDrawable(item.status))

            binding.root.setOnClickListener {
                listener?.onItemClick(item)
            }
        }

        fun animateStatusChange(item: RequestEntity) {
            binding.textStatus.text = item.status
            binding.textStatus.setBackgroundResource(RequestEntity.statusChipDrawable(item.status))

            binding.textStatus.animate()
                .scaleX(1.25f)
                .scaleY(1.25f)
                .setDuration(120)
                .withEndAction {
                    binding.textStatus.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180)
                        .start()
                }
                .start()
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RequestEntity>() {
            override fun areItemsTheSame(oldItem: RequestEntity, newItem: RequestEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: RequestEntity, newItem: RequestEntity): Boolean {
                return oldItem == newItem
            }

            override fun getChangePayload(oldItem: RequestEntity, newItem: RequestEntity): Any? {
                return if (oldItem.status != newItem.status) "STATUS_CHANGED" else null
            }
        }

        private fun formatDate(context: android.content.Context, createdAt: Long): CharSequence {
            if (createdAt <= 0L) return ""
            return DateUtils.formatDateTime(
                context,
                createdAt,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
            )
        }
    }
}
