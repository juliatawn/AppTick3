package com.juliacai.apptick.lockModes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RecoveryEmailHelperIntegrationTest {

    private lateinit var context: Context
    private lateinit var auth: FirebaseAuth
    private lateinit var projectId: String
    private lateinit var emulatorHost: String

    @Before
    fun setUp() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .permitNetwork()
                .build()
        )
        context = ApplicationProvider.getApplicationContext()
        FirebaseApp.initializeApp(context)
        auth = FirebaseAuth.getInstance()
        emulatorHost = resolveReachableEmulatorHost()
        auth.useEmulator(emulatorHost, EMULATOR_PORT)
        projectId = FirebaseApp.getInstance().options.projectId.orEmpty()
        assumeTrue("Firebase project id missing", projectId.isNotBlank())

        clearAuthEmulator(projectId)
        context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        auth.signOut()
    }

    @Test
    fun sendAndVerifyRecoveryLink_roundTripSucceeds() {
        val email = "recovery_${System.currentTimeMillis()}@example.com"

        var sendError: String? = null
        val sendLatch = CountDownLatch(1)
        RecoveryEmailHelper.sendRecoveryLink(
            context = context,
            email = email,
            purpose = RecoveryEmailHelper.PURPOSE_RESET_PASSWORD,
            onSuccess = { sendLatch.countDown() },
            onError = {
                sendError = it
                sendLatch.countDown()
            }
        )
        assertThat(sendLatch.await(15, TimeUnit.SECONDS)).isTrue()
        assertThat(sendError).isNull()

        val oobLink = waitForOobLink(projectId, email, timeoutMs = 15_000)
        assertThat(oobLink).isNotNull()

        var verifiedEmail: String? = null
        var verifiedPurpose: String? = null
        var verifyError: String? = null
        val verifyLatch = CountDownLatch(1)
        RecoveryEmailHelper.verifyRecoveryLink(
            context = context,
            intent = Intent(Intent.ACTION_VIEW, Uri.parse(oobLink)),
            onSuccess = { emailFromLink, purpose ->
                verifiedEmail = emailFromLink
                verifiedPurpose = purpose
                verifyLatch.countDown()
            },
            onError = {
                verifyError = it
                verifyLatch.countDown()
            }
        )
        assertThat(verifyLatch.await(15, TimeUnit.SECONDS)).isTrue()
        assertThat(verifyError).isNull()
        assertThat(verifiedEmail).isEqualTo(email)
        assertThat(verifiedPurpose).isEqualTo(RecoveryEmailHelper.PURPOSE_RESET_PASSWORD)

        val prefs = context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
        assertThat(prefs.getString("recovery_pending_email", null)).isNull()
        assertThat(prefs.getString("recovery_pending_purpose", null)).isNull()
    }

    private fun waitForOobLink(projectId: String, email: String, timeoutMs: Long): String? {
        val endAt = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < endAt) {
            val body = httpGet("${emulatorApiBase()}/projects/$projectId/oobCodes") ?: run {
                Thread.sleep(250)
                continue
            }
            val entries = JSONObject(body).optJSONArray("oobCodes") ?: run {
                Thread.sleep(250)
                continue
            }
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val itemEmail = item.optString("email")
                val requestType = item.optString("requestType")
                val itemLink = item.optString("oobLink")
                if (itemEmail.equals(email, ignoreCase = true) &&
                    requestType == "EMAIL_SIGNIN" &&
                    itemLink.isNotBlank()
                ) {
                    return itemLink
                }
            }
            Thread.sleep(250)
        }
        return null
    }

    private fun clearAuthEmulator(projectId: String) {
        httpDelete("${emulatorApiBase()}/projects/$projectId/accounts")
    }

    private fun resolveReachableEmulatorHost(): String {
        val candidateHosts = listOf(DEFAULT_EMULATOR_HOST, LOOPBACK_EMULATOR_HOST)
        return candidateHosts.firstOrNull { host ->
            httpGet("http://$host:$EMULATOR_PORT/") != null
        } ?: DEFAULT_EMULATOR_HOST
    }

    private fun emulatorApiBase(): String = "http://$emulatorHost:$EMULATOR_PORT/emulator/v1"

    private fun httpGet(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 1_500
            readTimeout = 1_500
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun httpDelete(url: String): Boolean {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 1_500
            readTimeout = 1_500
        }
        return try {
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val DEFAULT_EMULATOR_HOST = "10.0.2.2"
        private const val LOOPBACK_EMULATOR_HOST = "127.0.0.1"
        private const val EMULATOR_PORT = 9099
    }
}
