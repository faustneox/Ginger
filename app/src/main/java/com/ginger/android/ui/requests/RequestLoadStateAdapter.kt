package com.ginger.android.ui.requests

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ginger.android.databinding.ItemLoadStateBinding

class RequestLoadStateAdapter(private val retry: () -> Unit) : LoadStateAdapter<RequestLoadStateAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): ViewHolder {
        val binding = ItemLoadStateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, retry)
    }

    override fun onBindViewHolder(holder: ViewHolder, loadState: LoadState) {
        holder.bind(loadState)
    }

    class ViewHolder(private val binding: ItemLoadStateBinding, retry: () -> Unit) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.btnRetry.setOnClickListener { retry() }
        }

        fun bind(loadState: LoadState) {
            when (loadState) {
                is LoadState.Loading -> {
                    binding.progress.visibility = View.VISIBLE
                    binding.txtError.visibility = View.GONE
                    binding.btnRetry.visibility = View.GONE
                }
                is LoadState.Error -> {
                    binding.progress.visibility = View.GONE
                    binding.txtError.visibility = View.VISIBLE
                    binding.txtError.text = loadState.error.localizedMessage ?: loadState.error.toString()
                    binding.btnRetry.visibility = View.VISIBLE
                }
                else -> {
                    binding.progress.visibility = View.GONE
                    binding.txtError.visibility = View.GONE
                    binding.btnRetry.visibility = View.GONE
                }
            }
        }
    }
}
