package com.ginger.android.ui.requests

import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.ginger.android.ui.BaseActivity
import com.ginger.android.ui.hideErrorBanner
import com.ginger.android.ui.hideKeyboard
import com.ginger.android.ui.showErrorBanner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ginger.android.R
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.ginger.android.data.session.SessionManager
import com.ginger.android.databinding.ActivityCreateRequestBinding
import com.ginger.android.util.PhoneUtils
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreateRequestActivity : BaseActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    private val viewModel: CreateRequestViewModel by viewModels()
    private lateinit var binding: ActivityCreateRequestBinding

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (!uris.isNullOrEmpty()) {
            val strings = uris.map { it.toString() }
            viewModel.addAttachments(strings)
        }
    }

    private lateinit var attachmentsAdapter: AttachmentsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCreateRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentUser = sessionManager.currentUser.value
        if (currentUser == null || !currentUser.isAdmin) {
            finish()
            return
        }

        setupListeners()

        // Attachments Recycler
        attachmentsAdapter = AttachmentsAdapter(onRemove = { uri -> viewModel.removeAttachment(uri) }, onClick = { uriStr ->
            try {
                val intent = android.content.Intent(this, ImagePreviewActivity::class.java)
                intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URI, uriStr)
                startActivity(intent)
            } catch (_: Exception) {
            }
        })
        binding.recyclerAttachments.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerAttachments.adapter = attachmentsAdapter

        // Collect from StateFlow (modern approach)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }

                launch {
                    viewModel.attachments.collect { attachments ->
                        if (attachments.isEmpty()) {
                            binding.textAttachmentsCount.visibility = View.GONE
                        } else {
                            binding.textAttachmentsCount.visibility = View.VISIBLE
                            binding.textAttachmentsCount.text = "${attachments.size} вложение(s)"
                            attachmentsAdapter.setItems(attachments)
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.buttonCreateBack.setOnClickListener {
            finish()
        }

        binding.buttonAddAttachment.setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }

        binding.buttonCreateSubmit.setOnClickListener {
            hideKeyboard()
            attemptSubmit()
        }
    }

    private fun handleUiState(state: CreateRequestViewModel.CreateRequestUiState) {
        val isLoading = state is CreateRequestViewModel.CreateRequestUiState.Loading

        binding.buttonCreateSubmit.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.progressCreate.visibility = if (isLoading) View.VISIBLE else View.GONE

        setFormEnabled(!isLoading)

        when (state) {
            is CreateRequestViewModel.CreateRequestUiState.Success -> {
                Toast.makeText(this, R.string.message_request_sent, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }

            is CreateRequestViewModel.CreateRequestUiState.Error -> {
                showErrorBanner(binding.textCreateError, state.message)
            }

            is CreateRequestViewModel.CreateRequestUiState.Idle -> {
                hideErrorBanner(binding.textCreateError)
            }

            else -> {
                // Loading state is already handled by the common logic above (isLoading)
            }
        }
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.editCreateTitle.isEnabled = enabled
        binding.editCreateDescription.isEnabled = enabled
        binding.editCreateCategory.isEnabled = enabled
        binding.editOwnerPhone.isEnabled = enabled
        binding.buttonCreateBack.isEnabled = enabled
    }

    private fun attemptSubmit() {
        val title = binding.editCreateTitle.text.toString().trim()
        val description = binding.editCreateDescription.text.toString().trim()
        val category = binding.editCreateCategory.text.toString().trim()
        val ownerPhone = binding.editOwnerPhone.text.toString().trim()

        val validation = viewModel.validateFields(title, description, ownerPhone)
        if (validation != null) {
            val msg = getString(validation.toMessageRes())
            showErrorBanner(binding.textCreateError, msg)
            if (validation == ValidationError.TitleEmpty || validation == ValidationError.DescriptionTooShort) {
                binding.editCreateTitle.requestFocus()
            } else {
                binding.editOwnerPhone.requestFocus()
            }
            return
        }

        setFormEnabled(false)
        hideErrorBanner(binding.textCreateError)

        viewModel.submitRequest(title, description, category, ownerPhone)
    }



    

    
}
