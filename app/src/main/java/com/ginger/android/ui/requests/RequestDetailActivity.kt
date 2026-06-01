package com.ginger.android.ui.requests

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.ginger.android.ui.BaseActivity
import com.ginger.android.ui.hideErrorBanner
import com.ginger.android.ui.showErrorBanner
import com.ginger.android.ui.showConfirmationDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ginger.android.R
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import com.ginger.android.data.local.RequestEntity
import com.ginger.android.databinding.ActivityRequestDetailBinding
import kotlinx.coroutines.launch
import android.content.Intent
import com.ginger.android.ui.requests.ChatActivity

@AndroidEntryPoint
class RequestDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
    }

    private val viewModel: RequestDetailViewModel by viewModels()
    private lateinit var binding: ActivityRequestDetailBinding
    private var requestId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRequestDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1L)
        if (requestId <= 0) {
            finish()
            return
        }

        setupListeners()
        setupStatusCheckboxes()

        // Collect from StateFlow (modern approach)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    handleUiState(state)
                }
            }
        }
        viewModel.loadRequest(requestId)
    }

    private fun setupListeners() {
        binding.buttonDetailBack.setOnClickListener {
            finish()
        }

        binding.buttonOpenChat.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra(ChatActivity.EXTRA_REQUEST_ID, requestId)
            startActivity(intent)
        }

        binding.buttonSave.setOnClickListener {
            attemptSave()
        }

        binding.buttonDelete.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun setupStatusCheckboxes() {
        val statusClickListener = View.OnClickListener { v ->
            val clicked = v as CheckBox
            if (clicked.isChecked) {
                when (clicked.id) {
                    R.id.checkStatusNew -> {
                        binding.checkStatusInProgress.isChecked = false
                        binding.checkStatusClosed.isChecked = false
                    }
                    R.id.checkStatusInProgress -> {
                        binding.checkStatusNew.isChecked = false
                        binding.checkStatusClosed.isChecked = false
                    }
                    R.id.checkStatusClosed -> {
                        binding.checkStatusNew.isChecked = false
                        binding.checkStatusInProgress.isChecked = false
                    }
                }
            } else {
                // Prevent unchecking the last one
                if (!binding.checkStatusNew.isChecked &&
                    !binding.checkStatusInProgress.isChecked &&
                    !binding.checkStatusClosed.isChecked
                ) {
                    clicked.isChecked = true
                }
            }
        }

        binding.checkStatusNew.setOnClickListener(statusClickListener)
        binding.checkStatusInProgress.setOnClickListener(statusClickListener)
        binding.checkStatusClosed.setOnClickListener(statusClickListener)
    }

    private fun handleUiState(state: RequestDetailViewModel.RequestDetailUiState) {
        if (state is RequestDetailViewModel.RequestDetailUiState.Loading) {
            setFormEnabled(false)
            binding.progressDetail.visibility = View.VISIBLE
            hideErrorBanner(binding.textDetailError)
            return
        }

        binding.progressDetail.visibility = View.GONE

        when (state) {
            is RequestDetailViewModel.RequestDetailUiState.UserMode -> {
                setFormEnabled(false)

                binding.layoutUserReadOnly.visibility = View.VISIBLE
                binding.layoutAdminEditor.visibility = View.GONE

                val request = state.request
                binding.textUserDetailTitle.text = request.title
                binding.textUserDetailStatus.text = request.status
                binding.textUserDetailDescription.text = request.description
                binding.textUserDetailCategory.text = request.category
                binding.textUserDetailContact.text = request.contact
            }

            is RequestDetailViewModel.RequestDetailUiState.AdminMode -> {
                setFormEnabled(true)

                binding.layoutUserReadOnly.visibility = View.GONE
                binding.layoutAdminEditor.visibility = View.VISIBLE

                val request = state.request
                binding.editDetailTitle.setText(request.title)
                binding.editDetailDescription.setText(request.description)
                binding.editDetailCategory.setText(request.category)
                binding.editDetailContact.setText(request.contact)

                val status = request.status
                binding.checkStatusNew.isChecked = status == RequestEntity.STATUS_NEW
                binding.checkStatusInProgress.isChecked = status == RequestEntity.STATUS_IN_PROGRESS
                binding.checkStatusClosed.isChecked = status == RequestEntity.STATUS_CLOSED

                if (!state.error.isNullOrEmpty()) {
                    showErrorBanner(binding.textDetailError, state.error)
                } else {
                    hideErrorBanner(binding.textDetailError)
                }
            }

            is RequestDetailViewModel.RequestDetailUiState.Deleted -> {
                Toast.makeText(this, R.string.message_deleted, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }

            is RequestDetailViewModel.RequestDetailUiState.AccessDenied -> {
                Toast.makeText(this, R.string.message_access_denied, Toast.LENGTH_SHORT).show()
                finish()
            }

            is RequestDetailViewModel.RequestDetailUiState.Error -> {
                hideErrorBanner(binding.textDetailError)
                Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                setFormEnabled(true)
            }

            else -> {
                // Loading state is already handled by the early return above
            }
        }
    }

    private fun attemptSave() {
        val title = binding.editDetailTitle.text.toString().trim()
        val description = binding.editDetailDescription.text.toString().trim()
        val category = binding.editDetailCategory.text.toString().trim()
        val contact = binding.editDetailContact.text.toString().trim()
        val status = getSelectedStatus()

        if (title.isEmpty() || description.isEmpty()) {
            showErrorBanner(binding.textDetailError, getString(R.string.error_fill_all_fields))
            if (title.isEmpty()) {
                binding.editDetailTitle.requestFocus()
            } else {
                binding.editDetailDescription.requestFocus()
            }
            return
        }

        setFormEnabled(false)
        hideErrorBanner(binding.textDetailError)

        viewModel.saveChanges(title, description, category, contact, status)
    }

    private fun getSelectedStatus(): String {
        return when {
            binding.checkStatusClosed.isChecked -> RequestEntity.STATUS_CLOSED
            binding.checkStatusInProgress.isChecked -> RequestEntity.STATUS_IN_PROGRESS
            else -> RequestEntity.STATUS_NEW
        }
    }

    private fun showDeleteConfirmation() {
        showConfirmationDialog(
            getString(R.string.dialog_delete_request_title),
            getString(R.string.dialog_delete_request_message),
            getString(R.string.button_delete),
            getString(R.string.button_cancel),
            null,
            onConfirm = {
                setFormEnabled(false)
                hideErrorBanner(binding.textDetailError)
                viewModel.deleteRequest()
            }
        )
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.editDetailTitle.isEnabled = enabled
        binding.editDetailDescription.isEnabled = enabled
        binding.editDetailCategory.isEnabled = enabled
        binding.editDetailContact.isEnabled = enabled

        binding.checkStatusNew.isEnabled = enabled
        binding.checkStatusInProgress.isEnabled = enabled
        binding.checkStatusClosed.isEnabled = enabled

        binding.buttonSave.isEnabled = enabled
        binding.buttonDelete.isEnabled = enabled
        binding.buttonDetailBack.isEnabled = enabled
    }

    
}
