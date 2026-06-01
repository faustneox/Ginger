package com.ginger.android.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.ginger.android.ui.BaseActivity
import com.ginger.android.ui.applyLoadingState
import com.ginger.android.ui.hideErrorBanner
import com.ginger.android.ui.hideKeyboard
import com.ginger.android.ui.openMainAndFinish
import com.ginger.android.ui.setViewsEnabled
import com.ginger.android.ui.showErrorBanner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ginger.android.R
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import com.ginger.android.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterActivity : BaseActivity() {

    private val viewModel: RegisterViewModel by viewModels()
    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()

        // Collect from StateFlow (modern approach)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    handleUiState(state)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationEvents.collect { event ->
                    when (event) {
                        is RegisterViewModel.RegisterNavigationEvent.NavigateToMain -> {
                            Toast.makeText(this@RegisterActivity, R.string.message_registration_success, Toast.LENGTH_SHORT).show()
                            openMainAndFinish()
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.buttonRegisterBack.setOnClickListener {
            finish()
        }

        binding.buttonRegisterSubmit.setOnClickListener {
            hideKeyboard()
            attemptRegistration()
        }
    }

    private fun attemptRegistration() {
        val fullName = binding.editRegisterName.text.toString().trim()
        val phone = binding.editRegisterPhone.text.toString().trim()
        val password = binding.editRegisterPassword.text.toString()

        if (fullName.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            showErrorBanner(binding.textRegisterError, getString(R.string.error_fill_registration_fields))
            binding.editRegisterName.requestFocus()
            return
        }

        if (password.length < 6) {
            showErrorBanner(binding.textRegisterError, getString(R.string.error_password_length))
            binding.editRegisterPassword.requestFocus()
            return
        }

        setViewsEnabled(false,
            binding.editRegisterName,
            binding.editRegisterPhone,
            binding.editRegisterPassword,
            binding.buttonRegisterBack
        )
        hideErrorBanner(binding.textRegisterError)

        viewModel.register(fullName, phone, password)
    }

    private fun handleUiState(state: RegisterViewModel.RegisterUiState) {
        val isLoading = state is RegisterViewModel.RegisterUiState.Loading

        applyLoadingState(
            isLoading = isLoading,
            progressView = binding.progressRegister,
            hideWhileLoading = listOf(binding.buttonRegisterSubmit),
            controlViews = listOf(
                binding.editRegisterName,
                binding.editRegisterPhone,
                binding.editRegisterPassword,
                binding.buttonRegisterBack
            )
        )

        when (state) {
            is RegisterViewModel.RegisterUiState.Success -> {
                // Success is handled by navigation events.
            }

            is RegisterViewModel.RegisterUiState.Error -> {
                showErrorBanner(binding.textRegisterError, state.message)
                binding.editRegisterName.requestFocus()
            }

            is RegisterViewModel.RegisterUiState.Idle -> {
                hideErrorBanner(binding.textRegisterError)
            }

            else -> {
                // Loading state is already handled by the common logic above (isLoading)
            }
        }
    }

}
