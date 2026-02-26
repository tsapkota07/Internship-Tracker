package com.tirsansapkota.internshiptracker.service;

import com.tirsansapkota.internshiptracker.repository.VerificationTokenRepository;
import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.ApplicationStatus;
import com.tirsansapkota.internshiptracker.model.Theme;
import com.tirsansapkota.internshiptracker.model.UserPreferences;
import com.tirsansapkota.internshiptracker.repository.InternshipApplicationRepository;
import com.tirsansapkota.internshiptracker.repository.UserPreferencesRepository;
import com.tirsansapkota.internshiptracker.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository users;
    private final InternshipApplicationRepository applications;
    private final PasswordEncoder encoder;
    private final UserPreferencesRepository prefs;
    private final VerificationTokenRepository tokens;

    public UserService(UserRepository users,
                       InternshipApplicationRepository applications,
                       PasswordEncoder encoder,
                       UserPreferencesRepository prefs,
                       VerificationTokenRepository tokens) {
        this.users = users;
        this.applications = applications;
        this.encoder = encoder;
        this.prefs = prefs;
        this.tokens = tokens;
    }

    // register using an email.
    public AppUser register(String username, String rawPassword, String emailOrNull) {
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        String email = (emailOrNull == null || emailOrNull.isBlank()) ? null : emailOrNull.trim();

        if (email != null && users.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use");
        }

        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setEmail(email);
        u.setEmailVerified(false); // stays false unless verified later
        u.setRole("USER");

        u = users.save(u);

        UserPreferences up = new UserPreferences();
        up.setUser(u);
        up.setTheme(Theme.LIGHT);
        prefs.save(up);

        return u;
    }


    // register without an email.
    public AppUser register(String username, String rawPassword) {
        return register(username, rawPassword, null);
    }

    // get/ create user preferences
    public UserPreferences getOrCreatePreferences(AppUser user) {
        return prefs.findById(user.getId()).orElseGet(() -> {
            UserPreferences up = new UserPreferences();
            up.setUser(user);
            up.setTheme(Theme.LIGHT);
            up.setDefaultStatus(null);
            return prefs.save(up);
        });
    }


    // updating a user's preferences.
    public void updatePreferences(Theme theme, ApplicationStatus defaultStatus) {
        AppUser user = getCurrentUser();
        UserPreferences up = getOrCreatePreferences(user);
        up.setTheme(theme == null ? Theme.LIGHT : theme);
        up.setDefaultStatus(defaultStatus);
        prefs.save(up);
    }

    // -------------------------
    // Update the current theme.
    // -------------------------
    public void updateTheme(Theme theme) {
        AppUser user = getCurrentUser();
        UserPreferences up = getOrCreatePreferences(user);
        up.setTheme(theme == null ? Theme.LIGHT : theme);
        prefs.save(up);
    }

    // -------------------------
    // Current logged-in user
    // -------------------------
    public AppUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user");
        }

        String username = auth.getName();
        return users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Logged-in user not found: " + username));
    }

    // -------------------------
    // Danger zone: delete account
    // -------------------------
    @Transactional
    public void deleteAccount(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));

        Long userId = user.getId();

        // delete app rows first (FK safety)
        applications.deleteAllByOwnerUsername(username);

        // delete tokens + prefs (common FK blockers)
        // (inject these repos into UserService)
        tokens.deleteAllByUserId(userId);
        prefs.deleteById(userId);

        // finally delete user
        users.deleteById(userId);
    }

    // -------------------------
    // Check if password matches.
    // -------------------------
    public boolean matchesPassword(String username, String rawPassword) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));
        return encoder.matches(rawPassword, user.getPasswordHash());
    }


    @Transactional
    public void setEmailUnverified(AppUser user, String email) {
        if (email == null || email.isBlank()) {
            user.setEmail(null);
            user.setPendingEmail(null);
            user.setEmailVerified(false);
            users.save(user);
            return;
        }

        String normalized = email.trim().toLowerCase();

        if (isEmailTakenForOtherUser(normalized, user.getId())) {
            throw new IllegalArgumentException("Email already in use");
        }

        user.setEmail(normalized);
        user.setPendingEmail(null);
        user.setEmailVerified(false);
        users.save(user);
    }

    @Transactional
    public void setPendingEmail(AppUser user, String newEmail) {
        if (newEmail == null || newEmail.isBlank()) {
            user.setPendingEmail(null);
            users.save(user);
            return;
        }

        String normalized = newEmail.trim().toLowerCase();

        if (isEmailTakenForOtherUser(normalized, user.getId())) {
            throw new IllegalArgumentException("Email already in use");
        }

        user.setPendingEmail(normalized);

        users.save(user);
    }

    @Transactional
    public void clearEmail(AppUser user) {
        user.setEmail(null);
        user.setPendingEmail(null);
        user.setEmailVerified(false);
        users.save(user);
    }

    public Optional<AppUser> findById(Long id) {
        return users.findById(id);
    }

    public boolean isLoggedIn() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    public boolean isEmailTakenForOtherUser(String email, Long myId) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank()) return false;

        return users.existsByEmailIgnoreCaseAndIdNot(normalized, myId)
                || users.existsByPendingEmailIgnoreCaseAndIdNot(normalized, myId);
    }


}