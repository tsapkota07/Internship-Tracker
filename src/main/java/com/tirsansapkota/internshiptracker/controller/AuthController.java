package com.tirsansapkota.internshiptracker.controller;

import com.tirsansapkota.internshiptracker.dto.RegisterForm;
import com.tirsansapkota.internshiptracker.model.TokenType;
import com.tirsansapkota.internshiptracker.service.EmailService;
import com.tirsansapkota.internshiptracker.service.UserService;
import com.tirsansapkota.internshiptracker.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Authentication pages:
 * - GET /login (Spring Security handles POST /login)
 * - GET + POST /register
 *
 * GlobalUiAdvice injects shared UI model attributes (guestMode, username, uiTier, themeClass, etc.)
 * so we only add page-specific data here.
 */
@Controller
public class AuthController {

    private final UserService userService;
    private final VerificationService verificationService;
    private final EmailService emailService;

    /**
     * Optional base URL for absolute links in emails (recommended in production).
     * If not set, we fall back to the current request origin (good for local dev).
     */
    @Value("${app.base-url:}")
    private String baseUrl;

    public AuthController(
            UserService userService,
            VerificationService verificationService,
            EmailService emailService
    ) {
        this.userService = userService;
        this.verificationService = verificationService;
        this.emailService = emailService;
    }

    // =========================================================
    // Helpers
    // =========================================================

    /**
     * Normalize email:
     * - trims + lowercases
     * - returns null if blank (means "user did not provide email")
     */
    private String normalizeEmailOrNull(String raw) {
        if (raw == null) return null;
        String email = raw.trim().toLowerCase();
        return email.isBlank() ? null : email;
    }

    /**
     * Base URL used for links inside emails.
     * Priority:
     * 1) app.base-url (if configured)
     * 2) current request host (local dev safe)
     */
    private String resolveBaseUrl() {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.trim();
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private String buildVerifyEmailLink(String token) {
        return resolveBaseUrl() + "/account/verify-email/confirm?token=" + token;
    }

    // =========================================================
    // PAGES
    // =========================================================

    /**
     * GET /login
     * Renders the login page. Spring Security handles the authentication POST.
     */
    @GetMapping("/login")
    public String loginPage(Model model) {
        // Optional breadcrumbs (helps consistency if you're adding them everywhere)
        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Login", "url", "")
        ));
        return "login";
    }

    /**
     * GET /register
     * Shows the registration form.
     */
    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegisterForm());

        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Register", "url", "")
        ));

        return "register";
    }

    // =========================================================
    // ACTIONS
    // =========================================================

    /**
     * POST /register
     *
     * Creates a user. Email is optional:
     * - If email is provided -> send EMAIL_VERIFY token
     * - If email is blank -> register with no verification email sent
     */
    @PostMapping("/register")
    public String registerSubmit(
            @Valid @ModelAttribute("form") RegisterForm form,
            BindingResult bindingResult,
            Model model
    ) {
        // Validation errors: re-render with field messages
        if (bindingResult.hasErrors()) {
            model.addAttribute("breadcrumbs", List.of(
                    Map.of("label", "Register", "url", "")
            ));
            return "register";
        }

        try {
            String email = normalizeEmailOrNull(form.getEmail());

            // Register user (service should enforce uniqueness, hash password, etc.)
            var user = userService.register(form.getUsername(), form.getPassword(), email);

            // If they provided email, send a verification email
            if (email != null) {
                var token = verificationService.createToken(user, TokenType.EMAIL_VERIFY, 60, email);                String link = buildVerifyEmailLink(token.getToken());

                emailService.send(
                        email,
                        "Verify your email",
                        "Click to verify:\n" + link + "\n\nThis link expires in 60 minutes."
                );

                return "redirect:/login?verifySent";
            }

            // No email provided -> registration complete
            return "redirect:/login?registered";

        } catch (IllegalArgumentException e) {
            // e.g. username already exists, email already used, etc.
            model.addAttribute("error", e.getMessage());

            // Keep breadcrumb stable on error rerender
            model.addAttribute("breadcrumbs", List.of(
                    Map.of("label", "Register", "url", "")
            ));

            return "register";
        }
    }
}