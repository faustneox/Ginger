package com.ginger.android.ui.requests

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.ginger.android.ui.BaseActivity
import com.ginger.android.ui.hideErrorBanner
import com.ginger.android.ui.showErrorBanner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import com.ginger.android.R
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.ginger.android.data.session.SessionManager
import com.ginger.android.data.local.RequestEntity
import com.ginger.android.databinding.ActivityMainBinding
import com.ginger.android.ui.auth.AdminRolesActivity
import com.ginger.android.ui.auth.LoginActivity
import com.ginger.android.ui.common.DeleteItemAnimator
import com.ginger.android.ui.common.VerticalSpaceItemDecoration
import com.ginger.android.util.AuthPrefs
import androidx.paging.LoadState

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var mainTabManager: MainTabManager
    private lateinit var requestsAdapter: RequestListAdapter
    private lateinit var pagingAdapter: RequestPagingAdapter
    private var pagingJob: Job? = null
    private var currentPagingTab: Int = -1
    private var usingPaging: Boolean = false

    private var previousItemCount = 0
    private var lastOpenedRequestId = -1L

    private lateinit var detailLauncher: ActivityResultLauncher<Intent>
    private lateinit var createRequestLauncher: ActivityResultLauncher<Intent>
    private lateinit var adminRolesLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!sessionManager.hasActiveSession()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detailLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                // Explicit refresh so the list updates immediately after save/delete in Detail
                viewModel.refresh()
                if (usingPaging) {
                    pagingAdapter.refresh()
                }
            }
        }

        createRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.refresh()
                if (usingPaging) {
                    pagingAdapter.refresh()
                }
            }
        }

        // AdminRoles may change the current user's role — always refresh on return
        adminRolesLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            viewModel.refresh()
            if (usingPaging) pagingAdapter.refresh()
        }

        binding.buttonMenu.setOnClickListener {
            Toast.makeText(this, R.string.message_menu_placeholder, Toast.LENGTH_SHORT).show()
        }
        binding.buttonAddRequest.setOnClickListener {
            createRequestLauncher.launch(Intent(this, CreateRequestActivity::class.java))
        }

        mainTabManager = MainTabManager(binding, viewModel::selectTab)

        setupRecyclerView()
        setupListeners()

        // Collect from StateFlow (modern approach)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.isLoggedOut) {
                        handleLoggedOut()
                        return@collect
                    }

                    updateRequestsList(state)
                    binding.swipeRefreshRequests.isRefreshing = false

                    handleRequestsError(state.error)
                    handleEditedRequestHighlight(state)

                    updateAdminUi(state.isAdmin)

                    updateUserInfo(state)
                    applyScreenState(state.showProfileScreen)

                    mainTabManager.updateSelection(state.selectedTab)
                    setInteractionEnabled(!state.isLoading)
                }
            }
        }

        binding.bottomNavigation.setSelectedItemId(R.id.nav_requests)
    }


    private fun showRequestsScreen() {
        binding.screenRequests.visibility = View.VISIBLE
        binding.screenProfile.visibility = View.GONE
    }

    private fun showProfileScreen() {
        binding.screenRequests.visibility = View.GONE
        binding.screenProfile.visibility = View.VISIBLE
    }

    private fun applyScreenState(showProfile: Boolean) {
        if (showProfile) {
            showProfileScreen()
        } else {
            showRequestsScreen()
        }
    }

    private fun setupRecyclerView() {
        requestsAdapter = RequestListAdapter()
        requestsAdapter.setShowExtraDetails(false)
        requestsAdapter.setOnItemClickListener(object : RequestListAdapter.OnItemClickListener {
            override fun onItemClick(request: RequestEntity) {
                lastOpenedRequestId = request.id
                val intent = Intent(this@MainActivity, RequestDetailActivity::class.java)
                intent.putExtra(RequestDetailActivity.EXTRA_REQUEST_ID, request.id)
                detailLauncher.launch(intent)
            }
        })

        // Paging adapter for admin users
        pagingAdapter = RequestPagingAdapter()
        pagingAdapter.setShowExtraDetails(false)
        pagingAdapter.setOnItemClickListener(object : RequestPagingAdapter.OnItemClickListener {
            override fun onItemClick(request: RequestEntity) {
                lastOpenedRequestId = request.id
                val intent = Intent(this@MainActivity, RequestDetailActivity::class.java)
                intent.putExtra(RequestDetailActivity.EXTRA_REQUEST_ID, request.id)
                detailLauncher.launch(intent)
            }
        })

        binding.recyclerViewRequests.layoutManager = LinearLayoutManager(this)
        // default to simple adapter; will switch to paging when admin
        binding.recyclerViewRequests.adapter = requestsAdapter

        binding.recyclerViewRequests.itemAnimator = DeleteItemAnimator()

        val spacing = (resources.displayMetrics.density * 12).toInt()
        binding.recyclerViewRequests.addItemDecoration(
            VerticalSpaceItemDecoration(spacing, spacing)
        )

        binding.swipeRefreshRequests.setOnRefreshListener {
            if (usingPaging) {
                pagingAdapter.refresh()
            } else {
                viewModel.refresh()
            }
        }

        // Handle paging load states
        pagingAdapter.addLoadStateListener { loadState ->
            val isLoading = loadState.refresh is LoadState.Loading
            binding.swipeRefreshRequests.isRefreshing = isLoading

            val isEmpty = loadState.refresh is LoadState.NotLoading && pagingAdapter.itemCount == 0
            binding.recyclerViewRequests.visibility = if (isEmpty) View.GONE else View.VISIBLE
            binding.emptyRequestsContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE

            val error = when {
                loadState.prepend is LoadState.Error -> (loadState.prepend as LoadState.Error).error
                loadState.append is LoadState.Error -> (loadState.append as LoadState.Error).error
                loadState.refresh is LoadState.Error -> (loadState.refresh as LoadState.Error).error
                else -> null
            }
            if (error != null) showErrorBanner(binding.textMainError, error.message ?: error.toString()) else hideErrorBanner(binding.textMainError)
        }
    }

    private fun setupListeners() {
        binding.rowMyRequests.setOnClickListener {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_requests)
            showRequestsScreen()
        }
        binding.rowSettings.setOnClickListener {
            Toast.makeText(this, R.string.message_settings_placeholder, Toast.LENGTH_SHORT).show()
        }
        binding.rowManageAdmins.setOnClickListener {
            adminRolesLauncher.launch(Intent(this, AdminRolesActivity::class.java))
        }
        binding.rowLogout.setOnClickListener {
            logout()
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_register -> {
                    showProfileScreen()
                    true
                }
                R.id.nav_create -> {
                    // nav_create item is only visible to admins (controlled in updateAdminUi)
                    createRequestLauncher.launch(Intent(this, CreateRequestActivity::class.java))
                    true
                }
                else -> {
                    showRequestsScreen()
                    true
                }
            }
        }
    }

    private fun logout() {
        viewModel.logout()
    }

    private fun handleLoggedOut() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun updateRequestsList(state: MainViewModel.MainUiState) {
        // If admin — use PagingData flow; Pager has status-aware paging in ViewModel
        if (state.isAdmin) {
            startPaging(state.selectedTab)
            return
        }

        // Non-admin: standard ListAdapter flow
        stopPagingIfActive()

        val requests = state.requests
        val newSize = requests.size

        requestsAdapter.submitList(requests)

        val isEmpty = requests.isEmpty()
        binding.recyclerViewRequests.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.emptyRequestsContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE

        handleListAnimationsAndCount(newSize)
    }

    private fun handleListAnimationsAndCount(newSize: Int) {
        if (binding.recyclerViewRequests.layoutAnimation != null) {
            binding.recyclerViewRequests.scheduleLayoutAnimation()
            binding.recyclerViewRequests.layoutAnimation = null
        }

        if (newSize > previousItemCount) {
            animateNewItemsAppeared()
        }

        previousItemCount = newSize
    }

    private fun handleEditedRequestHighlight(state: MainViewModel.MainUiState) {
        if (lastOpenedRequestId > 0 && state.requests.isNotEmpty()) {
            val requestIdToHighlight = lastOpenedRequestId
            lastOpenedRequestId = -1L

            binding.recyclerViewRequests.postDelayed({
                val position = state.requests.indexOfFirst { it.id == requestIdToHighlight }
                if (position >= 0) {
                    highlightItemAtPosition(position)
                }
            }, 120)
        }
    }

    private fun startPaging(tabIndex: Int) {
        if (usingPaging && currentPagingTab == tabIndex) return
        stopPagingIfActive()

        usingPaging = true
        currentPagingTab = tabIndex

        // Switch adapter to paging (with LoadState header/footer)
        binding.recyclerViewRequests.adapter = pagingAdapter.withLoadStateHeaderAndFooter(
            RequestLoadStateAdapter { pagingAdapter.retry() },
            RequestLoadStateAdapter { pagingAdapter.retry() }
        )

        pagingJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getPagedRequestsForTab(tabIndex).collectLatest { pagingData ->
                    pagingAdapter.submitData(pagingData)
                }
            }
        }
    }

    private fun stopPagingIfActive() {
        if (!usingPaging) return
        pagingJob?.cancel()
        pagingJob = null
        usingPaging = false
        currentPagingTab = -1

        // Switch back to simple adapter
        binding.recyclerViewRequests.adapter = requestsAdapter
        hideErrorBanner(binding.textMainError)
    }

    private fun updateAdminUi(isAdmin: Boolean) {
        requestsAdapter.setShowExtraDetails(isAdmin)
        pagingAdapter.setShowExtraDetails(isAdmin)
        binding.buttonAddRequest.visibility = if (isAdmin) View.VISIBLE else View.GONE
        binding.bottomNavigation.menu.findItem(R.id.nav_create).isVisible = isAdmin
        binding.rowManageAdmins.visibility = if (isAdmin) View.VISIBLE else View.GONE
        binding.dividerManageAdmins.visibility = if (isAdmin) View.VISIBLE else View.GONE
    }

    private fun updateUserInfo(state: MainViewModel.MainUiState) {
        state.currentUser?.let { user ->
            binding.textUserInfoName.text = user.fullName
            val phoneDisplay = if (user.phone.isNullOrEmpty()) {
                getString(R.string.label_no_phone)
            } else {
                user.phone
            }
            binding.textUserInfoPhone.text = phoneDisplay
        }
    }

    private fun highlightItemAtPosition(position: Int) {
        binding.recyclerViewRequests.postDelayed({
            val vh = binding.recyclerViewRequests.findViewHolderForAdapterPosition(position)
            if (vh != null) {
                pulseItem(vh.itemView, 1.06f, 140, 220)
            }
        }, 80)
    }

    private fun handleRequestsError(errorMessage: String?) {
        if (!errorMessage.isNullOrEmpty()) {
            showErrorBanner(binding.textMainError, errorMessage)
        } else {
            hideErrorBanner(binding.textMainError)
        }
    }

    private fun animateNewItemsAppeared() {
        binding.recyclerViewRequests.postDelayed({
            binding.recyclerViewRequests.smoothScrollToPosition(0)

            binding.recyclerViewRequests.postDelayed({
                val vh = binding.recyclerViewRequests.findViewHolderForAdapterPosition(0)
                if (vh != null) {
                    pulseItem(vh.itemView, 1.08f, 120, 180)
                }
            }, 350)
        }, 80)
    }

    private fun setInteractionEnabled(enabled: Boolean) {
        binding.bottomNavigation.isEnabled = enabled
        binding.buttonAddRequest.isEnabled = enabled
        binding.rowMyRequests.isEnabled = enabled
        binding.rowSettings.isEnabled = enabled
        binding.rowManageAdmins.isEnabled = enabled
        binding.rowLogout.isEnabled = enabled
        binding.swipeRefreshRequests.isEnabled = enabled
    }

    private fun pulseItem(itemView: View, peakScale: Float, upDuration: Long, downDuration: Long) {
        itemView.animate()
            .scaleX(peakScale)
            .scaleY(peakScale)
            .setDuration(upDuration)
            .withEndAction {
                itemView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(downDuration)
                    .start()
            }
            .start()
    }
}
