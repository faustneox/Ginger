package com.ginger.android.ui.requests

import android.os.Bundle
import android.content.Intent
import com.ginger.android.ui.BaseActivity
import com.ginger.android.R
import com.ginger.android.data.session.SessionManager
import com.ginger.android.ui.auth.LoginActivity

class ChatActivity : BaseActivity() {

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        // Redirect to login if user is not authenticated
        val session = SessionManager.getInstance(this)
        if (!session.hasActiveSession()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1L)
        if (requestId <= 0) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.chatContainer, ChatFragment.newInstance(requestId))
                .commit()
        }
    }
}
