package com.relevecompteur.app.data.remote

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Manages Google Identity OAuth 2.0 authentication with the required scopes:
 * - https://www.googleapis.com/auth/spreadsheets
 * - https://www.googleapis.com/auth/drive.file
 */
class GoogleAuthManager(private val context: Context) {

    companion object {
        const val SCOPE_SPREADSHEETS = "https://www.googleapis.com/auth/spreadsheets"
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    }

    private val signInOptions: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(SCOPE_SPREADSHEETS),
                Scope(SCOPE_DRIVE_FILE)
            )
            .build()
    }

    val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, signInOptions)
    }

    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun hasRequiredScopes(account: GoogleSignInAccount?): Boolean {
        if (account == null) return false
        return GoogleSignIn.hasPermissions(
            account,
            Scope(SCOPE_SPREADSHEETS),
            Scope(SCOPE_DRIVE_FILE)
        )
    }

    suspend fun signOut() {
        runCatching { signInClient.signOut().await() }
    }
}

