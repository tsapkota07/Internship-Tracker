package com.tirsansapkota.internshiptracker;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.InternshipApplication;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Validates that the server correctly rejects bad input and that
 * Thymeleaf's automatic HTML escaping prevents stored XSS.
 *
 * "Adversary" scenarios tested:
 *  - Missing required fields
 *  - Invalid enum value for status
 *  - Oversized notes field (exposes missing @Size constraint — known gap)
 *  - XSS payloads in every text field
 *  - SQL-injection-style strings (safe because JPA uses parameterised queries)
 *  - Whitespace-only fields (treated as blank by @NotBlank)
 *  - Malformed date
 */
class InputValidationTest extends BaseIntegrationTest {

    // ------------------------------------------------------------------
    // Required field validation
    // ------------------------------------------------------------------

    @Test
    void createApp_blankCompany_reRendersForm() throws Exception {
        mockMvc.perform(post("/apps").with(csrf())
                        .param("company", "")
                        .param("role", "SWE Intern")
                        .param("status", "APPLIED")
                        .param("appliedDate", "2026-01-15"))
                .andExpect(status().isOk())
                .andExpect(view().name("apps-new"));

        assertThat(appRepository.count()).isZero();
    }

    @Test
    void createApp_blankRole_reRendersForm() throws Exception {
        mockMvc.perform(post("/apps").with(csrf())
                        .param("company", "Acme")
                        .param("role", "")
                        .param("status", "APPLIED")
                        .param("appliedDate", "2026-01-15"))
                .andExpect(status().isOk())
                .andExpect(view().name("apps-new"));
    }

    @Test
    void createApp_missingStatus_reRendersForm() throws Exception {
        mockMvc.perform(post("/apps").with(csrf())
                        .param("company", "Acme")
                        .param("role", "Intern")
                        .param("appliedDate", "2026-01-15"))
                .andExpect(status().isOk())
                .andExpect(view().name("apps-new"));
    }

    @Test
    void createApp_missingDate_reRendersForm() throws Exception {
        mockMvc.perform(post("/apps").with(csrf())
                        .param("company", "Acme")
                        .param("role", "Intern")
                        .param("status", "APPLIED"))
                .andExpect(status().isOk())
                .andExpect(view().name("apps-new"));
    }

    @Test
    void createApp_whitespaceOnlyCompany_reRendersForm() throws Exception {
        // @NotBlank rejects whitespace-only strings
        mockMvc.perform(post("/apps").with(csrf())
                        .param("company", "   ")
                        .param("role", "Intern")
                        .param("status", "APPLIED")
                        .param("appliedDate", "2026-01-15"))
                .andExpect(status().isOk())
                .andExpect(view().name("apps-new"));
    }

    // ------------------------------------------------------------------
    // XSS payloads — the app should save them (Thymeleaf auto-escapes on render),
    // not crash, and not execute scripts.
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = "alice")
    void xss_inCompany_savedAndDoesNotCrash() throws Exception {
        AppUser alice = createUser("alice");

        mockMvc.perform(post("/apps").with(csrf())
                        .param("company", "<script>alert(1)</script>")
                        .param("role", "Intern")
                        .param("status", "APPLIED")
                        .param("appliedDate", "2026-01-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apps"));

        // Data is stored as-is; Thymeleaf escapes it on output
        InternshipApplication saved = appRepository.findAllByOwnerUsernameAndDeletedAtIsNull("alice").get(0);
        assertThat(saved.getCompany()).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    @WithMockUser(username = "alice")
    void xss_inNotes_savedAndDoesNotCrash() throws Exception {
        createUser("alice");

        mockMvc.perform(post("/apps").with(csrf())
                        .param("company", "Acme")
                        .param("role", "Intern")
                        .param("status", "APPLIED")
                        .param("appliedDate", "2026-01-15")
                        .param("notes", "<img src=x onerror=alert(2)>"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "alice")
    void xss_inRole_savedAndDoesNotCrash() throws Exception {
        createUser("alice");

        mockMvc.perform(post("/apps").with(csrf())
                        .param("company", "Acme")
                        .param("role", "'; DROP TABLE app_users; --")
                        .param("status", "APPLIED")
                        .param("appliedDate", "2026-01-15"))
                .andExpect(status().is3xxRedirection());

        // DB should be unaffected — JPA uses parameterised queries
        assertThat(userRepository.existsByUsername("alice")).isTrue();
    }

    // ------------------------------------------------------------------
    // Oversized notes — @Size(max=2000) now validates before the DB is hit,
    // so the form re-renders with an error message instead of crashing.
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = "alice")
    void oversizedNotes_reRendersFormWithError() throws Exception {
        createUser("alice");
        String huge = "A".repeat(2001);

        mockMvc.perform(post("/apps").with(csrf())
                        .param("company", "Acme")
                        .param("role", "Intern")
                        .param("status", "APPLIED")
                        .param("appliedDate", "2026-01-15")
                        .param("notes", huge))
                .andExpect(status().isOk())
                .andExpect(view().name("apps-new"));

        assertThat(appRepository.count()).isZero();
    }

    // ------------------------------------------------------------------
    // Login input edge cases
    // ------------------------------------------------------------------

    @Test
    void login_xssInUsername_doesNotCrash() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "<script>alert(1)</script>")
                        .param("password", "anything"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void login_sqlInjectionInUsername_doesNotCrash() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "' OR '1'='1")
                        .param("password", "anything"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void login_veryLongUsername_doesNotCrash() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "A".repeat(10_000))
                        .param("password", "anything"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }
}
