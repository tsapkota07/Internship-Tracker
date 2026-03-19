package com.tirsansapkota.internshiptracker;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.ApplicationStatus;
import com.tirsansapkota.internshiptracker.model.InternshipApplication;
import com.tirsansapkota.internshiptracker.repository.InternshipApplicationRepository;
import com.tirsansapkota.internshiptracker.repository.UserPreferencesRepository;
import com.tirsansapkota.internshiptracker.repository.UserRepository;
import com.tirsansapkota.internshiptracker.repository.VerificationTokenRepository;
import com.tirsansapkota.internshiptracker.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

/**
 * Shared base for all MockMvc integration tests.
 *
 * Profile "test" activates:
 *  - H2 in-memory database  (application-test.properties)
 *  - TestMailConfig          (no-op JavaMailSender)
 *
 * Each test class gets a clean database via cleanDb() in @BeforeEach.
 *
 * WHY TransactionTemplate in helpers?
 *   Without an active HTTP request, Spring's OpenEntityManagerInView filter is not
 *   running. Each Spring Data save() call opens and closes its own session, leaving
 *   the returned entity "detached". Wrapping helpers in TransactionTemplate keeps
 *   a single session open for the entire helper call, so entities stay managed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired protected MockMvc mockMvc;

    @Autowired protected UserService userService;
    @Autowired protected UserRepository userRepository;
    @Autowired protected InternshipApplicationRepository appRepository;
    @Autowired protected UserPreferencesRepository prefsRepository;
    @Autowired protected VerificationTokenRepository tokenRepository;
    @Autowired private PlatformTransactionManager txManager;

    @BeforeEach
    void cleanDb() {
        appRepository.deleteAll();
        tokenRepository.deleteAll();
        prefsRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Creates a real user in H2 inside a single transaction so that the
     * UserPreferences entity is saved while the AppUser is still managed.
     */
    protected AppUser createUser(String username) {
        return new TransactionTemplate(txManager).execute(
                status -> userService.register(username, "Password1!"));
    }

    /**
     * Creates a real application owned by the given user.
     * getReferenceById gives Hibernate a managed proxy within the same transaction.
     */
    protected InternshipApplication createApp(AppUser owner) {
        return new TransactionTemplate(txManager).execute(status -> {
            AppUser ref = userRepository.getReferenceById(owner.getId());
            InternshipApplication app = new InternshipApplication();
            app.setCompany("Acme Corp");
            app.setRole("SWE Intern");
            app.setStatus(ApplicationStatus.APPLIED);
            app.setAppliedDate(LocalDate.of(2026, 1, 15));
            app.setOwner(ref);
            return appRepository.save(app);
        });
    }
}
