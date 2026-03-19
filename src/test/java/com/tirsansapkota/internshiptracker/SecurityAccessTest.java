package com.tirsansapkota.internshiptracker;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that:
 *  1. Unauthenticated users are blocked from protected pages.
 *  2. CSRF enforcement is active on all state-changing POSTs.
 *  3. Public routes are reachable without auth.
 *  4. Authenticated users can reach their own protected pages.
 */
class SecurityAccessTest extends BaseIntegrationTest {

    // ------------------------------------------------------------------
    // Public routes — must be accessible without auth (no redirect)
    // ------------------------------------------------------------------

    @Test
    void publicRoute_home_guestSeesHomePage() throws Exception {
        // Guests get the home page (200); logged-in users get redirect to /apps
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    void publicRoute_login_returns200() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void publicRoute_register_returns200() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk());
    }

    @Test
    void publicRoute_forgotPassword_returns200() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk());
    }

    @Test
    void publicRoute_logoutSuccess_returns200() throws Exception {
        mockMvc.perform(get("/logout-success"))
                .andExpect(status().isOk());
    }

    @Test
    void publicRoute_apps_guestAllowed() throws Exception {
        mockMvc.perform(get("/apps"))
                .andExpect(status().isOk());
    }

    @Test
    void publicRoute_appsNew_guestAllowed() throws Exception {
        mockMvc.perform(get("/apps/new"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Protected routes — unauthenticated user must be redirected to /login
    // ------------------------------------------------------------------

    @Test
    void protectedRoute_appsDeleted_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/apps/deleted"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void protectedRoute_account_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/account"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void protectedRoute_preferences_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/preferences"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void protectedRoute_export_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/export"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void protectedRoute_importGuest_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/apps/import-guest").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    // ------------------------------------------------------------------
    // CSRF enforcement — POST without a CSRF token must be rejected (403)
    // ------------------------------------------------------------------

    @Test
    void csrf_postApps_withoutToken_returns403() throws Exception {
        mockMvc.perform(post("/apps")
                        .param("company", "Evil Corp")
                        .param("role", "Hacker")
                        .param("status", "APPLIED")
                        .param("appliedDate", "2026-01-01"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrf_postLogin_withoutToken_returns403() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "alice")
                        .param("password", "wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrf_postRegister_withoutToken_returns403() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "alice")
                        .param("password", "Password1!"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Authenticated access — real user in DB is required because
    // GlobalUiAdvice calls userService.getCurrentUser() on every request.
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = "alice")
    void authenticated_appsDeleted_returns200() throws Exception {
        createUser("alice"); // must exist in DB for GlobalUiAdvice
        mockMvc.perform(get("/apps/deleted"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice")
    void authenticated_preferences_redirectsDueToEmailGate() throws Exception {
        // alice has no email → EmailGateService redirects to /account/email?required.
        // This confirms the email gate is active for /preferences.
        createUser("alice");
        mockMvc.perform(get("/preferences"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/email?required"));
    }
}
