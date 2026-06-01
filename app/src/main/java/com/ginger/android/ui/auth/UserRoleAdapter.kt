package com.ginger.android.ui.auth

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ginger.android.R
import com.ginger.android.data.local.UserEntity
import com.ginger.android.databinding.ItemUserRoleBinding

class UserRoleAdapter : ListAdapter<UserEntity, UserRoleAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var listener: ((UserEntity) -> Unit)? = null
    private var currentUserId: Long = -1
    private var processingUserId: Long = -1

    fun setOnUserClickListener(listener: (UserEntity) -> Unit) {
        this.listener = listener
    }

    fun setCurrentUserId(userId: Long) {
        this.currentUserId = userId
        notifyDataSetChanged()
    }

    fun setProcessingUserId(userId: Long) {
        this.processingUserId = userId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserRoleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user, currentUserId == user.id, listener, processingUserId)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads[0] == "ROLE_CHANGED") {
            val user = getItem(position)
            val isCurrent = user.id == currentUserId
            holder.animateRoleChange(user.isAdmin, isCurrent)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    inner class ViewHolder(private val binding: ItemUserRoleBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            user: UserEntity,
            isCurrentUser: Boolean,
            listener: ((UserEntity) -> Unit)?,
            processingUserId: Long
        ) {
            val suffix = if (isCurrentUser) " (${binding.root.context.getString(R.string.label_you)})" else ""
            binding.textRoleUserName.text = user.fullName + suffix

            val phoneText = if (user.phone.isNullOrEmpty()) {
                binding.root.context.getString(R.string.label_no_phone)
            } else {
                user.phone
            }
            binding.textRoleUserPhone.text = phoneText

            binding.checkRoleAdmin.isChecked = user.isAdmin
            binding.checkRoleAdmin.isClickable = false

            val isProcessing = user.id == processingUserId

            if (isProcessing) {
                binding.root.alpha = 0.5f
                binding.root.setOnClickListener(null)
            } else {
                binding.root.alpha = 1f
                binding.root.setOnClickListener {
                    listener?.invoke(user)
                }
            }
        }

        fun animateRoleChange(isAdmin: Boolean, isCurrentUser: Boolean) {
            binding.checkRoleAdmin.isChecked = isAdmin

            binding.checkRoleAdmin.animate()
                .scaleX(1.4f)
                .scaleY(1.4f)
                .setDuration(100)
                .withEndAction {
                    binding.checkRoleAdmin.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                }
                .start()

            if (isCurrentUser) {
                binding.textRoleUserName.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(120)
                    .withEndAction {
                        binding.textRoleUserName.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(180)
                            .start()
                    }
                    .start()
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<UserEntity>() {
            override fun areItemsTheSame(oldItem: UserEntity, newItem: UserEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: UserEntity, newItem: UserEntity): Boolean {
                return oldItem == newItem
            }

            override fun getChangePayload(oldItem: UserEntity, newItem: UserEntity): Any? {
                if (oldItem.isAdmin != newItem.isAdmin) {
                    return "ROLE_CHANGED"
                }
                return null
            }
        }
    }
}
