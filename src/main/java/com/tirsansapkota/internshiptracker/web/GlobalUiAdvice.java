package com.tirsansapkota.internshiptracker.web;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.Theme;
import com.tirsansapkota.internshiptracker.service.GuestApplicationStore;
import com.tirsansapkota.internshiptracker.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * GlobalUiAdvice
 *
 * Adds shared UI attributes to the model for EVERY view automatically.
 * This keeps controllers focused on page-specific logic.
 *
 * What it provides (always safe / non-null):
 *  - guestMode, username
 *  - guestAppsCount, hasGuestApps, showImportBanner
 *  - theme, themeClass
 *  - email, hasEmail, emailVerified
 *  - uiTier: GUEST / BASIC_USER / EMAIL_USER  (based on emailVerified)
 */
@ControllerAdvice
public class GlobalUiAdvice {

    private final UserService userService;
    private final GuestApplicationStore guestStore;

    public GlobalUiAdvice(UserService userService, GuestApplicationStore guestStore) {
        this.userService = userService;
        this.guestStore = guestStore;
    }

    @ModelAttribute("demoUser")
    public boolean demoUser() {
        try {
            return userService.getCurrentUser().isDemoUser();
        } catch (Exception e) {
            return false; // guest / not logged in
        }
    }

    @ModelAttribute("currentUser")
    public AppUser currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }

    @ModelAttribute
    public void injectGlobalUi(Model model, HttpSession session) {

        // =========================================================
        // 1) Auth state
        // =========================================================
        boolean loggedIn = userService.isLoggedIn();
        boolean guestMode = !loggedIn;

        model.addAttribute("guestMode", guestMode);

        // Always provide a non-null username to prevent Thymeleaf null issues.
        // (For guests this will just be an empty string.)
        String username = "";
        if (loggedIn) {
            username = userService.getCurrentUser().getUsername();
        }
        model.addAttribute("username", username);

        // =========================================================
        // 2) Guest applications stored in session
        // =========================================================
        int guestAppsCount = guestStore.getAll(session).size();
        boolean hasGuestApps = guestAppsCount > 0;

        model.addAttribute("guestAppsCount", guestAppsCount);
        model.addAttribute("hasGuestApps", hasGuestApps);

        // Import banner shows only when logged in AND guest apps exist
        model.addAttribute("showImportBanner", loggedIn && hasGuestApps);

        // =========================================================
        // 3) Logged-in user details (email + tier flags)
        // =========================================================
        // Always provide safe defaults for templates.
        String email = "";
        boolean hasEmail = false;
        boolean emailVerified = false;

        if (loggedIn) {
            var user = userService.getCurrentUser(); // fetch ONCE

            email = (user.getEmail() == null) ? "" : user.getEmail().trim();
            hasEmail = !email.isBlank();
            emailVerified = user.isEmailVerified();
        }

        model.addAttribute("email", email);
        model.addAttribute("hasEmail", hasEmail);
        model.addAttribute("emailVerified", emailVerified);

        // Your 3-tier UX rule:
        // - GUEST: not logged in
        // - BASIC_USER: logged in, email not verified (even if email exists)
        // - EMAIL_USER: logged in + verified email (best UI)
        String uiTier;
        if (!loggedIn) {
            uiTier = "GUEST";
        } else {
            uiTier = emailVerified ? "EMAIL_USER" : "BASIC_USER";
        }
        model.addAttribute("uiTier", uiTier);

        // =========================================================
        // 4) Theme + themeClass
        // =========================================================
        // Guests default to LIGHT.
        Theme theme = Theme.LIGHT;

        if (loggedIn) {
            var user = userService.getCurrentUser();
            var prefs = userService.getOrCreatePreferences(user);

            if (prefs.getTheme() != null) {
                theme = prefs.getTheme();
            }
        }

        model.addAttribute("theme", theme);

        String themeClass = switch (theme) {
            case DARK       -> "theme-dark";
            case SERIKA     -> "theme-serika";
            case NORD       -> "theme-nord";
            case DRACULA    -> "theme-dracula";
            case ROSE_PINE  -> "theme-rose-pine";
            case CATPPUCCIN -> "theme-catppuccin";
            default         -> "theme-light";
        };
        model.addAttribute("themeClass", themeClass);
    }
}