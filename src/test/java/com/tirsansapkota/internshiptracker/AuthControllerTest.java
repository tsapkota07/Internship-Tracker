package com.tirsansapkota.internshiptracker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the registration and login flows.
 */
class AuthControllerTest extends BaseIntegrationTest {

    // ------------------------------------------------------------------
    // Page rendering
    // ------------------------------------------------------------------

    @Test
    void loginPage_returns200() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void registerPage_returns200() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    @Test
    void forgotPasswordPage_returns200() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    @Test
    void register_validData_createsUserAndRedirects() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "newuser")
                        .param("password", "Password1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        assertThat(userRepository.existsByUsername("newuser")).isTrue();
    }

    @Test
    void register_withEmail_redirectsToVerifySent() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "emailuser")
                        .param("password", "Password1!")
                        .param("email", "test@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?verifySent"));

        assertThat(userRepository.existsByUsername("emailuser")).isTrue();
    }

    @Test
    void register_blankUsername_reRendersFormWithError() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "")
                        .param("password", "Password1!"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void register_blankPassword_reRendersFormWithError() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "someuser")
                        .param("password", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void register_duplicateUsername_reRendersFormWithError() throws Exception {
        createUser("alice");

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "alice")
                        .param("password", "Password1!"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));

        // Still only one alice
        assertThat(userRepository.findAll().stream()
                .filter(u -> u.getUsername().equals("alice"))
                .count()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Login via Spring Security's built-in form handler
    // ------------------------------------------------------------------

    @Test
    void login_wrongCredentials_redirectsToLoginError() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "nobody")
                        .param("password", "wrongpass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void login_correctCredentials_redirectsToApps() throws Exception {
        // Register the user directly so Spring Security can authenticate them.
        // Note: the account must be enabled (email verification is not enforced
        // unless DbUserDetailsService checks it — verified by this test passing).
        createUser("alice");

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "alice")
                        .param("password", "Password1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apps"));
    }
}
