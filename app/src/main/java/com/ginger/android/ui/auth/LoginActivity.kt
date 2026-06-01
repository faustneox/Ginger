package com.ginger.android.ui.auth

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import timber.log.Timber
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.ginger.android.ui.BaseActivity
import com.ginger.android.ui.applyLoadingState
import com.ginger.android.ui.hideErrorBanner
import com.ginger.android.ui.hideKeyboard
import com.ginger.android.ui.openMainAndFinish
import com.ginger.android.ui.setViewsEnabled
import com.ginger.android.ui.showErrorBanner
import com.ginger.android.util.PhoneUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ginger.android.R
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.ginger.android.data.session.SessionManager
import com.ginger.android.databinding.ActivityLoginBinding
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

@AndroidEntryPoint
class LoginActivity : BaseActivity() {

    private companion object {
        private const val TAG = "LoginActivity"
    }

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    @Inject
    lateinit var sessionManager: SessionManager

    private val viewModel: LoginViewModel by viewModels()

    private var googlePhoneDialog: AlertDialog? = null
    private var currentPhoneRequiredState: LoginViewModel.LoginUiState.PhoneNumberRequired? = null

    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (sessionManager.hasActiveSession()) {
            openMainAndFinish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        restoreRememberedPhone()
        setupListeners()

        // Main UI state (lifecycle-aware)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    handleUiState(state)
                }
            }
        }

        // One-time navigation events (reliable even if emitted while paused)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationEvents.collect { event ->
                    when (event) {
                        is LoginViewModel.LoginNavigationEvent.NavigateToMain -> {
                            openMainAndFinish()
                        }
                    }
                }
            }
        }
    }

    private fun handleUiState(state: LoginViewModel.LoginUiState) {
        val isLoading = state is LoginViewModel.LoginUiState.Loading

        applyLoadingState(
            isLoading = isLoading,
            progressView = binding.progressLogin,
            hideWhileLoading = listOf(binding.buttonLogin, binding.googleSignInSection, binding.googleSignInButton),
            controlViews = listOf(
                binding.editLoginPhone,
                binding.editLoginPassword,
                binding.checkRememberMe,
                binding.textGoRegister,
                binding.googleSignInButton
            )
        )

        when (state) {
            is LoginViewModel.LoginUiState.Success -> {
                // Navigation is handled via navigationEvents
            }

            is LoginViewModel.LoginUiState.Error -> {
                showErrorBanner(binding.textLoginError, state.message)
            }

            is LoginViewModel.LoginUiState.PhoneNumberRequired -> {
                hideErrorBanner(binding.textLoginError)
                if (currentPhoneRequiredState != state || googlePhoneDialog?.isShowing != true) {
                    currentPhoneRequiredState = state
                    showPhoneNumberDialog(state)
                }
            }

            is LoginViewModel.LoginUiState.Idle -> {
                hideErrorBanner(binding.textLoginError)
            }

            else -> {
                // Loading state is already handled by the common logic above (isLoading)
            }
        }
    }

    private fun setupGoogleSignIn() {
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            // Важно: для Google Sign-In НЕ проверяем жёстко RESULT_OK.
            // Даже при успешном выборе аккаунта иногда приходит RESULT_CANCELED.
            // Реальный результат определяется внутри getSignedInAccountFromIntent().
            if (result.data != null) {
                handleGoogleSignInResult(result.data!!)
            } else {
                // Пользователь закрыл окно без выбора
                showGoogleAuthError()
            }
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestId()
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.googleSignInButton.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }
    }

    private fun restoreRememberedPhone() {
        lifecycleScope.launch {
            if (viewModel.isRememberMeEnabled()) {
                viewModel.getSavedLoginPhone()?.takeIf { it.isNotEmpty() }?.let {
                    binding.editLoginPhone.setText(it)
                }
                binding.checkRememberMe.isChecked = true
            }
        }
    }

    private fun setupListeners() {
        binding.buttonLogin.setOnClickListener {
            attemptLogin()
        }

        binding.textForgotPassword.setOnClickListener {
            Toast.makeText(this, R.string.message_forgot_password, Toast.LENGTH_SHORT).show()
        }

        binding.textGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun handleGoogleSignInResult(data: Intent) {
        try {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account: GoogleSignInAccount? = task.getResult(ApiException::class.java)

            if (account != null) {
                processGoogleSignIn(account)
            } else {
                showGoogleAuthError()
            }
        } catch (e: ApiException) {
            Timber.e(e, "Google sign-in failed, statusCode=%s", e.statusCode)

            // 12501 = SIGN_IN_CANCELLED — пользователь сам отменил вход
            if (e.statusCode == 12501) {
                hideErrorBanner(binding.textLoginError)
            } else {
                showGoogleAuthError()
            }
        } catch (e: Exception) {
            Timber.e(e, "Google sign-in processing error")
            showErrorBanner(binding.textLoginError, getString(R.string.error_google_sign_in))
        }
    }

    private fun showGoogleAuthError() {
        showErrorBanner(binding.textLoginError, getString(R.string.error_google_auth_failed))
    }

    private fun showPhoneNumberDialog(state: LoginViewModel.LoginUiState.PhoneNumberRequired) {
        googlePhoneDialog?.dismiss()

        val dialogView = layoutInflater.inflate(R.layout.dialog_phone_input, null)
        val phoneInput = dialogView.findViewById<TextInputEditText>(com.ginger.android.R.id.input_phone)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_google_phone_required_title)
            .setMessage(R.string.dialog_google_phone_required_message)
            .setView(dialogView)
            .setPositiveButton(R.string.button_continue, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val phoneRaw = phoneInput.text?.toString()?.trim() ?: ""
                val normalizedPhone = PhoneUtils.normalize(phoneRaw)

                if (normalizedPhone.isBlank() || !PhoneUtils.isValid(normalizedPhone)) {
                    phoneInput.error = getString(R.string.error_invalid_phone)
                    return@setOnClickListener
                }

                viewModel.completeGoogleSignInWithPhone(phoneRaw, state)
            }
        }

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        googlePhoneDialog = dialog
        dialog.show()
    }

    private fun processGoogleSignIn(account: GoogleSignInAccount) {
        viewModel.handleGoogleSignIn(account)
    }

    private fun attemptLogin() {
        hideKeyboard()

        val phoneRaw = binding.editLoginPhone.text.toString().trim()
        val password = binding.editLoginPassword.text.toString().trim()
        val remember = binding.checkRememberMe.isChecked

        if (phoneRaw.isEmpty() || password.isEmpty()) {
            showErrorBanner(binding.textLoginError, getString(R.string.error_login_fill))
            return
        }

        setViewsEnabled(false,
            binding.editLoginPhone,
            binding.editLoginPassword,
            binding.checkRememberMe,
            binding.textGoRegister,
            binding.googleSignInButton
        )
        hideErrorBanner(binding.textLoginError)

        viewModel.loginWithPhone(phoneRaw, password, remember)
    }
}
