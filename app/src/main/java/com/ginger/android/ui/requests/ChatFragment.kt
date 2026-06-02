package com.ginger.android.ui.requests

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ginger.android.data.local.ChatMessage
import com.ginger.android.databinding.FragmentChatBinding
import com.ginger.android.R
import kotlinx.coroutines.flow.collect
import coil.load
import android.widget.ImageView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.doOnLayout
import com.google.android.material.snackbar.Snackbar

class ChatFragment : Fragment() {

    companion object {
        private const val ARG_REQUEST_ID = "arg_request_id"

        fun newInstance(requestId: Long): ChatFragment {
            val f = ChatFragment()
            val args = Bundle()
            args.putLong(ARG_REQUEST_ID, requestId)
            f.arguments = args
            return f
        }
    }

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by viewModels()
    private var requestId: Long = -1L

    private val adapter = MessageAdapter()

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // Upload and send
            viewModel.uploadAttachmentAndSend(requestId, it, binding.editMessage.text.toString().takeIf { t -> t.isNotBlank() })
            binding.editMessage.setText("")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = arguments?.getLong(ARG_REQUEST_ID) ?: -1L
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMessages.adapter = adapter

        // Adjust paddings for system navigation bar so messages/input aren't overlapped
        binding.recyclerMessages.clipToPadding = false
        val rv = binding.recyclerMessages
        val inputBar = binding.inputBar
        val originalRvBottom = rv.paddingBottom
        val originalInputBottom = inputBar.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navBarBottom = systemBars.bottom

            inputBar.updatePadding(bottom = originalInputBottom + navBarBottom)
            inputBar.doOnLayout {
                val targetBottom = originalRvBottom + navBarBottom + inputBar.height
                rv.updatePadding(bottom = targetBottom)
            }

            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        binding.buttonSend.setOnClickListener {
            val text = binding.editMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendMessage(requestId, text, null)
                binding.editMessage.setText("")
            }
        }

        binding.buttonAttach.setOnClickListener {
            // allow any file type; UI will show link
            pickFileLauncher.launch("*/*")
        }

        lifecycleScope.launch {
            viewModel.observeMessages(requestId).collect { list ->
                adapter.setItems(list)
                binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
            }
        }

        // Show retry Snackbar when upload fails
        lifecycleScope.launch {
            viewModel.uploadErrors.collect { ev ->
                Snackbar.make(binding.root, "Ошибка загрузки: ${ev.message}", Snackbar.LENGTH_LONG)
                    .setAction("Повторить") {
                        viewModel.uploadAttachmentAndSend(ev.requestId, ev.fileUri, ev.text)
                    }
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class MessageAdapter : RecyclerView.Adapter<MessageViewHolder>() {
        private var items: List<ChatMessage> = emptyList()

        fun setItems(list: List<ChatMessage>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
            return MessageViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textMessage = view.findViewById<android.widget.TextView>(R.id.textMessage)
        private val textAttachment = view.findViewById<android.widget.TextView>(R.id.textAttachment)
        private val textMeta = view.findViewById<android.widget.TextView>(R.id.textMeta)

        fun bind(msg: ChatMessage) {
            textMessage.text = msg.text ?: ""

            val attachment = msg.attachmentUrl
            if (!attachment.isNullOrEmpty()) {
                if (isImageUrl(attachment)) {
                    val imageView = itemView.findViewById<ImageView>(R.id.imageAttachment)
                    imageView.visibility = View.VISIBLE
                    imageView.load(attachment) { crossfade(true) }
                    imageView.setOnClickListener {
                        val intent = Intent(itemView.context, ImagePreviewActivity::class.java)
                        intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URI, attachment)
                        itemView.context.startActivity(intent)
                    }
                    textAttachment.visibility = View.GONE
                } else {
                    val imageView = itemView.findViewById<ImageView>(R.id.imageAttachment)
                    imageView.visibility = View.GONE
                    textAttachment.visibility = View.VISIBLE
                    textAttachment.text = getString(R.string.label_attachment)
                    textAttachment.setOnClickListener {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(attachment))
                        startActivity(intent)
                    }
                }
            } else {
                val imageView = itemView.findViewById<ImageView>(R.id.imageAttachment)
                imageView.visibility = View.GONE
                textAttachment.visibility = View.GONE
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            textMeta.text = "${msg.userId} • ${sdf.format(Date(msg.createdAt))}"
        }

        private fun isImageUrl(url: String): Boolean {
            return Regex("\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$", RegexOption.IGNORE_CASE).containsMatchIn(url)
        }
    }

}
