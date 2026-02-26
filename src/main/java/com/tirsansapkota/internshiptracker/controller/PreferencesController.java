package com.tirsansapkota.internshiptracker.controller;

import com.tirsansapkota.internshiptracker.model.Theme;
import com.tirsansapkota.internshiptracker.repository.UserPreferencesRepository;
import com.tirsansapkota.internshiptracker.service.EmailGateService;
import com.tirsansapkota.internshiptracker.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PreferencesController
 *
 * Purpose:
 * - Lets a logged-in user update app preferences (currently: Theme).
 *
 * Access rules (EmailGateService):
 * - If user has no email -> redirect to /account/email?required
 * - If user has email but not verified -> redirect to /account/email?verifyRequired
 *
 * Global UI (navbar/theme/uiTier):
 * - Injected automatically via your @ControllerAdvice (GlobalUiAdvice / GlobalViewAdvice),
 *   so we only set page-specific attributes here (form, breadcrumbs, etc.).
 */
@Controller
public class PreferencesController {

    private final UserService userService;
    private final EmailGateService emailGate;
    private final UserPreferencesRepository prefsRepo;

    public PreferencesController(
            UserService userService,
            EmailGateService emailGate,
            UserPreferencesRepository prefsRepo
    ) {
        this.userService = userService;
        this.emailGate = emailGate;
        this.prefsRepo = prefsRepo;
    }

    // =========================================================
    // Form backing object
    // =========================================================

    /**
     * PreferencesForm
     * - Used as th:object="${form}" in preferences.html
     * - Default theme is LIGHT
     */
    public static class PreferencesForm {
        private Theme theme = Theme.LIGHT;

        public Theme getTheme() { return theme; }
        public void setTheme(Theme theme) { this.theme = theme; }
    }

    // =========================================================
    // Helpers
    // =========================================================

    /**
     * Enforces the "email required + verification required" rules.
     * Returns a redirect string if blocked, otherwise returns null.
     */
    private String enforceEmailGateOrRedirect() {
        var user = userService.getCurrentUser();

        if (emailGate.needsEmail(user)) {
            return "redirect:/account/email?required";
        }
        if (emailGate.needsVerification(user)) {
            return "redirect:/account/email?verifyRequired";
        }
        return null;
    }

    /**
     * Small safety helper so we never persist null theme.
     */
    private static Theme safeTheme(Theme t) {
        return (t == null) ? Theme.LIGHT : t;
    }

    // =========================================================
    // Pages
    // =========================================================

    /**
     * GET /preferences
     * Displays preferences form (Theme).
     */
    @GetMapping("/preferences")
    public String preferences(Model model) {
        // Enforce your "best UX only for verified email users" rule
        String gateRedirect = enforceEmailGateOrRedirect();
        if (gateRedirect != null) return gateRedirect;

        var user = userService.getCurrentUser();
        var prefs = userService.getOrCreatePreferences(user);

        // Build form from current preferences
        var form = new PreferencesForm();
        if (prefs.getTheme() != null) {
            form.setTheme(prefs.getTheme());
        }

        model.addAttribute("form", form);

        // Breadcrumbs (edit if you want different structure)
        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Applications", "url", "/apps"),
                Map.of("label", "Preferences", "url", "")
        ));
        return "preferences";
    }

    // =========================================================
    // Actions
    // =========================================================

    /**
     * POST /preferences
     * Saves preferences and redirects back with a "saved" query param.
     */
    @PostMapping("/preferences")
    public String save(@Valid @ModelAttribute("form") PreferencesForm form) {
        String gateRedirect = enforceEmailGateOrRedirect();
        if (gateRedirect != null) return gateRedirect;

        var user = userService.getCurrentUser();
        var prefs = userService.getOrCreatePreferences(user);

        // Save theme safely (avoid null)
        prefs.setTheme(safeTheme(form.getTheme()));
        prefsRepo.save(prefs);

        return "redirect:/preferences?saved";
    }
}