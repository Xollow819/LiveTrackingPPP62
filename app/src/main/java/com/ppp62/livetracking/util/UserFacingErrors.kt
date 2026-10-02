package com.ppp62.livetracking.util

import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Never pass exception text (which can contain request headers/tokens) into product UI. */
object UserFacingErrors {
    fun message(error: Throwable, fallback: String): String {
        if (error is CancellationException) throw error
        val detail = if (error is RestException) error.error else error.message
        return knownMessage(detail) ?: when {
            error is IOException -> "Unable to connect. Check your internet connection and try again."
            error is RestException && error.statusCode == 429 -> "Too many attempts. Please wait a moment and try again."
            error is RestException && error.statusCode in 500..599 -> "The service is temporarily unavailable. Please try again."
            else -> fallback
        }
    }

    // Exact allowlist: unknown server text and technical details are never shown.
    internal fun knownMessage(code: String?): String? = when (code) {
        "email_address_invalid" -> "This email address wasn't accepted. Check it or use another email address."
        "invalid_credentials" -> "The email or password is incorrect. Please try again."
        "email_not_confirmed" -> "Confirm your email using the link in your inbox, then sign in."
        "email_exists", "user_already_exists" -> "An account already uses this email. Sign in or reset your password."
        "weak_password" -> "Choose a stronger password with at least eight characters."
        "same_password" -> "Choose a password different from your current one."
        "over_request_rate_limit", "over_email_send_rate_limit" -> "Too many attempts. Please wait a moment and try again."
        "email_address_not_authorized" -> "Email delivery isn't available for this address. Contact your session administrator."
        "signup_disabled", "email_provider_disabled" -> "Account creation is currently unavailable. Contact your session administrator."
        "otp_expired", "flow_state_expired", "flow_state_not_found", "bad_code_verifier" -> "This link has expired. Request a new email and try again."
        "session_expired", "refresh_token_not_found", "bad_jwt", "Sign in required", "Unable to sign in", "Sign in before recording evidence" -> "Please sign in again to continue."
        "Lecturer account required", "Sign in with your lecturer account", "Only the owner can monitor this session", "Owner required" -> "Sign in with the lecturer account that owns this session."
        "Session unavailable", "Session code unavailable or session closed" -> "This session isn't available. Check the code with your lecturer."
        "Session closed" -> "This session has ended. Your saved records are still available."
        "Open your session from the lecturer workspace" -> "Open your session from the lecturer workspace."
        "Active session owner required" -> "Only the lecturer can edit an active session."
        "Name and team required" -> "Enter your name and team to join."
        "Membership required" -> "Join this session before submitting evidence."
        "Checkpoint unavailable" -> "This checkpoint is no longer available. Refresh the route."
        "Invalid checkpoint", "Title, six-character code and checkpoints required" -> "Check the session details and checkpoint locations before saving."
        "Photo required" -> "Add a photo for this checkpoint."
        "Enter valid measurements", "Invalid measurements", "Required measurement missing" -> "Enter valid measurements for the required fields."
        else -> null
    }
}
