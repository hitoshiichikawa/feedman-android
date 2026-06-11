package com.feedman.android.core.auth

/**
 * Authentication boundary for Google OAuth + token refresh flows.
 *
 * Declared as an interface only in this skeleton (Req 2.2). The skeleton does NOT
 * provide a Fake or Hilt binding because the login placeholder screen must avoid
 * invoking any auth flow (Req 4.2). Concrete implementations and the PKCE / token
 * refresh logic are deferred to subsequent Issues per `docs/GRAND-DESIGN.md` §5.3 and
 * `design/SERVER.md` §1.
 */
interface AuthRepository {
    // Intentionally empty: subsequent Issues will declare exchangeAuthCode / refresh /
    // revoke / observeSession etc. with proper return types tied to design/SPEC.md §3.
}
