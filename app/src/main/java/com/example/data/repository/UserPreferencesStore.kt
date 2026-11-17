package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID

private val Context.brokerSecurityDataStore by preferencesDataStore(name = "apextrade_security_prefs")

data class LocalSecurityState(
    val pinEnabled: Boolean = false,
    val hasPinSet: Boolean = false,
    val failedPinAttempts: Int = 0,
    val lockedUntilMillis: Long = 0L,
    val deviceSessionId: String = ""
)

class UserPreferencesStore(private val context: Context) {

    private object Keys {
        val PIN_ENABLED = booleanPreferencesKey("pin_lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_lock_hash")
        val FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        val LOCKED_UNTIL = longPreferencesKey("pin_locked_until_ms")
        val DEVICE_SESSION_ID = stringPreferencesKey("device_session_id")
    }

    val securityState: Flow<LocalSecurityState> = context.brokerSecurityDataStore.data.map { prefs ->
        LocalSecurityState(
            pinEnabled = prefs[Keys.PIN_ENABLED] ?: false,
            hasPinSet = !prefs[Keys.PIN_HASH].isNullOrBlank(),
            failedPinAttempts = prefs[Keys.FAILED_ATTEMPTS] ?: 0,
            lockedUntilMillis = prefs[Keys.LOCKED_UNTIL] ?: 0L,
            deviceSessionId = prefs[Keys.DEVICE_SESSION_ID] ?: ""
        )
    }

    suspend fun getOrCreateDeviceSessionId(userId: String): String {
        val current = context.brokerSecurityDataStore.data.first()[Keys.DEVICE_SESSION_ID]
        if (!current.isNullOrBlank() && current.startsWith("${userId.take(12)}_")) {
            return current
        }
        val generated = "${userId.take(12)}_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        context.brokerSecurityDataStore.edit { prefs ->
            prefs[Keys.DEVICE_SESSION_ID] = generated
        }
        return generated
    }

    suspend fun setSecurityPin(userId: String, pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() }) {
            "Security PIN must be 4 to 8 digits."
        }
        val hashed = hashPin(userId, pin)
        context.brokerSecurityDataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hashed
            prefs[Keys.PIN_ENABLED] = true
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKED_UNTIL] = 0L
        }
    }

    suspend fun disableSecurityPin() {
        context.brokerSecurityDataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = false
            prefs.remove(Keys.PIN_HASH)
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKED_UNTIL] = 0L
        }
    }

    suspend fun verifySecurityPin(userId: String, pin: String): Result<Boolean> {
        val prefs = context.brokerSecurityDataStore.data.first()
        val now = System.currentTimeMillis()
        val lockedUntil = prefs[Keys.LOCKED_UNTIL] ?: 0L
        if (lockedUntil > now) {
            val secs = ((lockedUntil - now) / 1000L).coerceAtLeast(1L)
            return Result.failure(IllegalStateException("Too many failed attempts. Retry in ${secs}s."))
        }
        val storedHash = prefs[Keys.PIN_HASH].orEmpty()
        if (storedHash.isBlank()) return Result.success(true)
        val matches = storedHash == hashPin(userId, pin)
        if (matches) {
            context.brokerSecurityDataStore.edit { p ->
                p[Keys.FAILED_ATTEMPTS] = 0
                p[Keys.LOCKED_UNTIL] = 0L
            }
            return Result.success(true)
        } else {
            val nextAttempts = (prefs[Keys.FAILED_ATTEMPTS] ?: 0) + 1
            val lockMs = if (nextAttempts >= 5) now + 60_000L else 0L
            context.brokerSecurityDataStore.edit { p ->
                p[Keys.FAILED_ATTEMPTS] = nextAttempts
                p[Keys.LOCKED_UNTIL] = lockMs
            }
            return Result.success(false)
        }
    }

    private fun hashPin(userId: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("apextrade_v1:$userId:$pin".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
