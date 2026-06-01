package com.ginger.android.ui.auth

import android.view.View
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ginger.android.R
import com.ginger.android.data.session.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityTest {

    @Test
    fun loginScreenShowsLoginButton() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        SessionManager.getInstance(ctx).logout()

        ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.buttonLogin)
                assertEquals(View.VISIBLE, button.visibility)
            }
        }
    }
}
