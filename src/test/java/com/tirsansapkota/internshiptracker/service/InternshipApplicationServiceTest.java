package com.tirsansapkota.internshiptracker.service;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.ApplicationStatus;
import com.tirsansapkota.internshiptracker.model.InternshipApplication;
import com.tirsansapkota.internshiptracker.repository.InternshipApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for InternshipApplicationService.
 * No Spring context — just Mockito stubs. Runs fast on any platform.
 */
@ExtendWith(MockitoExtension.class)
class InternshipApplicationServiceTest {

    @Mock
    InternshipApplicationRepository repo;

    @InjectMocks
    InternshipApplicationService service;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private AppUser user(String username) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash("hash");
        return u;
    }

    private InternshipApplication app(AppUser owner) {
        InternshipApplication a = new InternshipApplication();
        a.setId(1L);
        a.setCompany("Acme");
        a.setRole("Intern");
        a.setStatus(ApplicationStatus.APPLIED);
        a.setAppliedDate(LocalDate.of(2026, 1, 1));
        a.setOwner(owner);
        return a;
    }

    // ------------------------------------------------------------------
    // softDeleteByIdForUser
    // ------------------------------------------------------------------

    @Test
    void softDelete_setsDeletedAt() {
        AppUser alice = user("alice");
        InternshipApplication a = app(alice);

        when(repo.findByIdAndOwnerUsernameAndDeletedAtIsNull(1L, "alice"))
                .thenReturn(Optional.of(a));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.softDeleteByIdForUser(1L, "alice");

        assertThat(a.getDeletedAt()).isNotNull();
        verify(repo).save(a);
    }

    @Test
    void softDelete_wrongUser_throws404() {
        when(repo.findByIdAndOwnerUsernameAndDeletedAtIsNull(1L, "attacker"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDeleteByIdForUser(1L, "attacker"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void softDelete_alreadyDeleted_throws404() {
        // The query filters out already-deleted records (deletedAtIsNull),
        // so re-deleting returns empty → 404.
        when(repo.findByIdAndOwnerUsernameAndDeletedAtIsNull(1L, "alice"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDeleteByIdForUser(1L, "alice"))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ------------------------------------------------------------------
    // restoreByIdForUser
    // ------------------------------------------------------------------

    @Test
    void restore_clearsDeletedAt() {
        AppUser alice = user("alice");
        InternshipApplication a = app(alice);
        a.setDeletedAt(java.time.LocalDateTime.now());

        when(repo.findByIdAndOwnerUsername(1L, "alice")).thenReturn(Optional.of(a));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.restoreByIdForUser(1L, "alice");

        assertThat(a.getDeletedAt()).isNull();
        verify(repo).save(a);
    }

    @Test
    void restore_wrongUser_throws404() {
        when(repo.findByIdAndOwnerUsername(1L, "attacker"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restoreByIdForUser(1L, "attacker"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ------------------------------------------------------------------
    // hardDeleteByIdForUser
    // ------------------------------------------------------------------

    @Test
    void hardDelete_callsRepoDelete() {
        AppUser alice = user("alice");
        InternshipApplication a = app(alice);

        when(repo.findByIdAndOwnerUsername(1L, "alice")).thenReturn(Optional.of(a));

        service.hardDeleteByIdForUser(1L, "alice");

        verify(repo).delete(a);
    }

    @Test
    void hardDelete_wrongUser_throws404() {
        when(repo.findByIdAndOwnerUsername(1L, "attacker"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.hardDeleteByIdForUser(1L, "attacker"))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ------------------------------------------------------------------
    // getByIdForUser (ownership check)
    // ------------------------------------------------------------------

    @Test
    void getByIdForUser_ownApp_returnsPresent() {
        AppUser alice = user("alice");
        InternshipApplication a = app(alice);

        when(repo.findByIdAndOwnerUsernameAndDeletedAtIsNull(1L, "alice"))
                .thenReturn(Optional.of(a));

        assertThat(service.getByIdForUser(1L, "alice")).isPresent();
    }

    @Test
    void getByIdForUser_otherUserApp_returnsEmpty() {
        when(repo.findByIdAndOwnerUsernameAndDeletedAtIsNull(1L, "attacker"))
                .thenReturn(Optional.empty());

        assertThat(service.getByIdForUser(1L, "attacker")).isEmpty();
    }

    // ------------------------------------------------------------------
    // filterForUser — in-memory filtering logic
    // ------------------------------------------------------------------

    @Test
    void filterForUser_byStatus_returnsOnlyMatching() {
        AppUser alice = user("alice");

        InternshipApplication a1 = app(alice);
        a1.setStatus(ApplicationStatus.APPLIED);
        a1.setCompany("Google");

        InternshipApplication a2 = app(alice);
        a2.setId(2L);
        a2.setStatus(ApplicationStatus.INTERVIEW);
        a2.setCompany("Meta");

        when(repo.findAllByOwnerUsernameAndDeletedAtIsNull("alice"))
                .thenReturn(List.of(a1, a2));

        List<InternshipApplication> result =
                service.filterForUser("alice", null, ApplicationStatus.APPLIED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompany()).isEqualTo("Google");
    }

    @Test
    void filterForUser_byQuery_matchesCompanyAndRole() {
        AppUser alice = user("alice");

        InternshipApplication a1 = app(alice);
        a1.setCompany("Google");
        a1.setRole("SWE");

        InternshipApplication a2 = app(alice);
        a2.setId(2L);
        a2.setCompany("Meta");
        a2.setRole("PM");

        when(repo.findAllByOwnerUsernameAndDeletedAtIsNull("alice"))
                .thenReturn(List.of(a1, a2));

        List<InternshipApplication> result =
                service.filterForUser("alice", "google", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompany()).isEqualTo("Google");
    }

    @Test
    void filterForUser_queryIsCaseInsensitive() {
        AppUser alice = user("alice");
        InternshipApplication a = app(alice);
        a.setCompany("Stripe");

        when(repo.findAllByOwnerUsernameAndDeletedAtIsNull("alice"))
                .thenReturn(List.of(a));

        assertThat(service.filterForUser("alice", "STRIPE", null)).hasSize(1);
        assertThat(service.filterForUser("alice", "stripe", null)).hasSize(1);
        assertThat(service.filterForUser("alice", "nomatch", null)).isEmpty();
    }

    // ------------------------------------------------------------------
    // getAllForUser — basic delegation
    // ------------------------------------------------------------------

    @Test
    void getAllForUser_delegatesToRepo() {
        AppUser alice = user("alice");
        when(repo.findAllByOwnerUsernameAndDeletedAtIsNull("alice"))
                .thenReturn(List.of(app(alice)));

        assertThat(service.getAllForUser("alice")).hasSize(1);
        verify(repo).findAllByOwnerUsernameAndDeletedAtIsNull("alice");
    }
}
