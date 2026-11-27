package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

object GuestSession {
    private const val PREFS_NAME = "watchtogether_guest"
    private const val KEY_GUEST_UID = "guest_uid"
    private val _guestUid = MutableStateFlow<String?>(null)
    val guestUid: StateFlow<String?> = _guestUid.asStateFlow()

    fun enterGuestMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getString(KEY_GUEST_UID, null)
            ?: "guest_${UUID.randomUUID().toString().take(8)}".also {
                prefs.edit().putString(KEY_GUEST_UID, it).apply()
            }
        _guestUid.value = uid
        return uid
    }

    fun clearGuestMode() {
        _guestUid.value = null
    }
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

/**
 * Silent Auto-Sign-In on startup using GetGoogleIdOption (one credential option per request).
 */
fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val response = kotlinx.coroutines.withTimeoutOrNull(2500L) {
                credentialManager.getCredential(context, request)
            }
            val cred = response?.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

/**
 * Interactive Google Sign-In using GetSignInWithGoogleOption (one credential option per request).
 */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }
    val activity = context as? Activity
    if (activity == null) {
        onAuthError("Unable to launch sign-in from non-Activity context")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned by Google Sign-In.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
        }
    }
}

/**
 * Guest Sign-In: tries Firebase Anonymous Auth first, and falls back to a local GuestSession
 * if Anonymous Auth is disabled in Firebase Console (e.g. ADMIN_ONLY_OPERATION).
 */
fun onGuestSignInClicked(
    context: Context,
    onAuthSuccess: () -> Unit,
    scope: CoroutineScope
) {
    scope.launch {
        try {
            if (Firebase.auth.currentUser == null) {
                Firebase.auth.signInAnonymously().await()
            }
        } catch (e: Exception) {
            Log.w("Auth", "Firebase anonymous auth unavailable, using local guest session: ${e.message}")
            GuestSession.enterGuestMode(context)
        }
        onAuthSuccess()
    }
}

/**
 * Email & Password Sign-In or Sign-Up using Firebase Auth.
 */
fun onEmailSignInClicked(
    email: String,
    password: String,
    isSignUp: Boolean,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope
) {
    val trimmedEmail = email.trim()
    if (trimmedEmail.isBlank() || password.length < 6) {
        onAuthError("Enter a valid email and a password of at least 6 characters.")
        return
    }
    scope.launch {
        try {
            if (isSignUp) {
                Firebase.auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            } else {
                Firebase.auth.signInWithEmailAndPassword(trimmedEmail, password).await()
            }
            GuestSession.clearGuestMode()
            onAuthSuccess()
        } catch (e: Exception) {
            Log.e("Auth", "Email auth failed", e)
            onAuthError(e.localizedMessage ?: "Email sign-in failed. Please try again.")
        }
    }
}

/**
 * Safe sign-out flow wrapped in NonCancellable to cleanly clear Credential Manager state.
 */
fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                GuestSession.clearGuestMode()
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
