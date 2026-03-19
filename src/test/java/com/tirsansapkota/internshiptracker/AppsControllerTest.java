package com.tirsansapkota.internshiptracker;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.InternshipApplication;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Functional tests for the internship application CRUD lifecycle:
 *  - Guest mode (session-based)
 *  - Authenticated mode (DB-based)
 *  - Ownership enforcement (IDOR protection)
 *  - Soft-delete / restore / hard-delete
 */
class AppsControllerTest extends BaseIntegrationTest {

    // ------------------------------------------------------------------
    // Guest flow
    // ------------------------------------------------------------------

    @Test
    void guest_listPage_returns200() throws Exception {
        mockMvc.perform(get("/apps"))
                .andExpect(status().isOk())
                .andExpect(view().name("apps-list"));
    }

    @Test
    void guest_newForm_returns200() throws Exception {
        mockMvc.perform(get("/apps/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("apps-new"));
    }

    @Test
    void guest_createApp_redirectsToList() throws Exception {
        mockMvc.perform(post("/apps").with(csrf())
                        .param("company", "Acme")
                        .param("role", "SWE Intern")
                        .param("status", "APPLIED")
                        .param("appliedDate", "2026-01-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apps"));
    }

    // ------------------------------------------------------------------
    // Authenticated CRUD
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = "alice")
    void auth_listPage_returns200() throws Exception {
        createUser("alice");
        mockMvc.perform(get("/apps"))
                .andExpect(status().isOk())
                .andExpect(view().name("apps-list"));
    }

    @Test
    @WithMockUser(username = "alice")
    void auth_createApp_savesToDb() throws Exception {
        createUser("alice");

        mockMvc.perform(post("/apps").with(csrf())
                        .param("company", "Acme")
                        .param("role", "SWE Intern")
                        .param("status", "APPLIED")
                        .param("appliedDate", "2026-01-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apps"));

        assertThat(appRepository.findAllByOwnerUsernameAndDeletedAtIsNull("alice"))
                .hasSize(1)
                .first()
                .satisfies(a -> {
                    assertThat(a.getCompany()).isEqualTo("Acme");
                    assertThat(a.getRole()).isEqualTo("SWE Intern");
                });
    }

    @Test
    @WithMockUser(username = "alice")
    void auth_editForm_returns200() throws Exception {
        AppUser alice = createUser("alice");
        InternshipApplication app = createApp(alice);

        mockMvc.perform(get("/apps/{id}/edit", app.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("apps-edit"));
    }

    @Test
    @WithMockUser(username = "alice")
    void auth_updateApp_updatesInDb() throws Exception {
        AppUser alice = createUser("alice");
        InternshipApplication app = createApp(alice);

        mockMvc.perform(post("/apps/{id}/edit", app.getId()).with(csrf())
                        .param("company", "NewCo")
                        .param("role", "Backend Intern")
                        .param("status", "INTERVIEW")
                        .param("appliedDate", "2026-02-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apps"));

        InternshipApplication updated = appRepository.findById(app.getId()).orElseThrow();
        assertThat(updated.getCompany()).isEqualTo("NewCo");
        assertThat(updated.getRole()).isEqualTo("Backend Intern");
    }

    @Test
    @WithMockUser(username = "alice")
    void auth_softDelete_setsDeletedAt() throws Exception {
        AppUser alice = createUser("alice");
        InternshipApplication app = createApp(alice);

        mockMvc.perform(post("/apps/{id}/delete", app.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apps"));

        InternshipApplication deleted = appRepository.findById(app.getId()).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    @WithMockUser(username = "alice")
    void auth_restore_clearsDeletedAt() throws Exception {
        AppUser alice = createUser("alice");
        InternshipApplication app = createApp(alice);

        // soft-delete first
        mockMvc.perform(post("/apps/{id}/delete", app.getId()).with(csrf()));

        // then restore
        mockMvc.perform(post("/apps/{id}/restore", app.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apps/deleted"));

        InternshipApplication restored = appRepository.findById(app.getId()).orElseThrow();
        assertThat(restored.getDeletedAt()).isNull();
    }

    @Test
    @WithMockUser(username = "alice")
    void auth_hardDelete_removesFromDb() throws Exception {
        AppUser alice = createUser("alice");
        InternshipApplication app = createApp(alice);
        Long id = app.getId();

        // soft-delete first (hard-delete requires deleted state isn't mandatory, but test it)
        mockMvc.perform(post("/apps/{id}/delete-forever", id).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(appRepository.findById(id)).isEmpty();
    }

    // ------------------------------------------------------------------
    // IDOR (Insecure Direct Object Reference) — alice must not touch bob's apps
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = "alice")
    void idor_editForm_otherUserApp_returns404() throws Exception {
        createUser("alice");
        AppUser bob = createUser("bob");
        InternshipApplication bobApp = createApp(bob);

        mockMvc.perform(get("/apps/{id}/edit", bobApp.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice")
    void idor_deletePost_otherUserApp_returns404() throws Exception {
        createUser("alice");
        AppUser bob = createUser("bob");
        InternshipApplication bobApp = createApp(bob);

        mockMvc.perform(post("/apps/{id}/delete", bobApp.getId()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice")
    void idor_deleteForever_otherUserApp_returns404() throws Exception {
        createUser("alice");
        AppUser bob = createUser("bob");
        InternshipApplication bobApp = createApp(bob);

        mockMvc.perform(post("/apps/{id}/delete-forever", bobApp.getId()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Pagination sanity — make sure page param doesn't crash
    // ------------------------------------------------------------------

    @Test
    void guest_listPage_withLargePageParam_doesNotCrash() throws Exception {
        mockMvc.perform(get("/apps").param("page", "99999"))
                .andExpect(status().isOk());
    }

    @Test
    void guest_listPage_withNegativePageParam_doesNotCrash() throws Exception {
        mockMvc.perform(get("/apps").param("page", "-5"))
                .andExpect(status().isOk());
    }
}
