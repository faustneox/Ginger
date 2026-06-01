package com.ginger.android.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.ginger.android.ui.BaseActivity
import com.ginger.android.ui.hideErrorBanner
import com.ginger.android.ui.showErrorBanner
import com.ginger.android.ui.showConfirmationDialog
import com.ginger.android.ui.showLastAdminWarningDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ginger.android.R
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.ginger.android.data.session.SessionManager
import com.ginger.android.data.local.UserEntity
import com.ginger.android.databinding.ActivityAdminRolesBinding
import com.ginger.android.ui.common.DeleteItemAnimator
import com.ginger.android.ui.common.VerticalSpaceItemDecoration
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminRolesActivity : BaseActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    private val viewModel: AdminRolesViewModel by viewModels()
    private lateinit var binding: ActivityAdminRolesBinding
    private lateinit var adapter: UserRoleAdapter

    private var hasLoadedOnce = false

    private var currentUserId: Long = -1L
    private var adminCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdminRolesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()

        // Collect from StateFlow (modern approach)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    handleUiState(state)
                }
            }
        }

        viewModel.loadUsers()
    }

    private fun setupRecyclerView() {
        adapter = UserRoleAdapter()
        binding.recyclerViewUsers.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewUsers.adapter = adapter

        binding.recyclerViewUsers.itemAnimator = DeleteItemAnimator()

        val spacing = (resources.displayMetrics.density * 10).toInt()
        binding.recyclerViewUsers.addItemDecoration(
            VerticalSpaceItemDecoration(spacing, spacing)
        )
    }

    private fun setupListeners() {
        binding.buttonAdminRolesBack.setOnClickListener {
            finish()
        }

        adapter.setOnUserClickListener { user ->
            // We now rely on the ViewModel's processing state for disabling clicks
            // The Activity still does client-side last-admin checks for UX

            val isSelf = user.id == currentUserId
            val isLastAdmin = user.isAdmin && adminCount <= 1

            if (isLastAdmin) {
                showLastAdminWarningDialog(user)
                return@setOnUserClickListener
            }

            // Disable refresh and back during the operation (UI feedback)
            binding.swipeRefreshUsers.isEnabled = false
            binding.buttonAdminRolesBack.isEnabled = false

            viewModel.toggleAdminRole(user.id)
        }

        binding.swipeRefreshUsers.setOnRefreshListener {
            viewModel.clearError()
            viewModel.loadUsers()
        }

        binding.textAdminRolesSubtitle.text = getString(R.string.admin_roles_subtitle)
    }

    private fun showLastAdminWarningDialog(user: UserEntity) {
        val isSelf = user.id == currentUserId
        val message = if (isSelf) {
            getString(R.string.dialog_last_admin_warning_self)
        } else {
            getString(R.string.dialog_last_admin_warning_other)
        }

        showLastAdminWarningDialog(message, binding.recyclerViewUsers,
            onContinue = {
                binding.swipeRefreshUsers.isEnabled = false
                binding.buttonAdminRolesBack.isEnabled = false
                viewModel.toggleAdminRole(user.id)
            },
            onCancel = {
                resetAfterOperation()
            }
        )
    }

    private fun handleUiState(state: AdminRolesViewModel.AdminRolesUiState) {
        when (state) {
            is AdminRolesViewModel.AdminRolesUiState.Loading -> {
                hideErrorBanner(binding.textAdminError)
                binding.swipeRefreshUsers.isRefreshing = true
                binding.recyclerViewUsers.visibility = View.GONE
                binding.emptyUsersContainer.visibility = View.GONE
            }

            is AdminRolesViewModel.AdminRolesUiState.Success -> {
                binding.swipeRefreshUsers.isRefreshing = false

                this.currentUserId = state.currentUserId
                this.adminCount = state.adminCount

                adapter.setCurrentUserId(state.currentUserId)
                adapter.setProcessingUserId(state.processingUserId)
                adapter.submitList(state.users)

                hasLoadedOnce = true
                updateEmptyState(state.users.isEmpty())

                if (binding.recyclerViewUsers.layoutAnimation != null) {
                    binding.recyclerViewUsers.scheduleLayoutAnimation()
                    binding.recyclerViewUsers.layoutAnimation = null
                }

                if (state.error != null) {
                    showErrorBanner(binding.textAdminError, state.error, 4000L, true) { viewModel.clearError() }
                } else {
                    hideErrorBanner(binding.textAdminError)
                }

                resetAfterOperation()
            }

            is AdminRolesViewModel.AdminRolesUiState.AccessDenied -> {
                Toast.makeText(this, R.string.message_access_denied, Toast.LENGTH_SHORT).show()
                finish()
                resetAfterOperation()
            }

            is AdminRolesViewModel.AdminRolesUiState.Error -> {
                hideErrorBanner(binding.textAdminError)
                Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                updateEmptyState(true)
                resetAfterOperation()
            }
        }
    }

    private fun resetAfterOperation() {
        binding.swipeRefreshUsers.isEnabled = true
        binding.buttonAdminRolesBack.isEnabled = true
    }

    

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.recyclerViewUsers.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.emptyUsersContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }
}
