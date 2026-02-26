package com.tirsansapkota.internshiptracker.controller;

import com.tirsansapkota.internshiptracker.dto.ForgotPasswordForm;
import com.tirsansapkota.internshiptracker.dto.ResetPasswordForm;
import com.tirsansapkota.internshiptracker.model.TokenType;
import com.tirsansapkota.internshiptracker.repository.UserRepository;
import com.tirsansapkota.internshiptracker.service.EmailService;
import com.tirsansapkota.internshiptracker.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * PasswordResetController
 *
 * Supports:
 * - GET/POST /forgot-password  (request reset email)
 * - GET/POST /reset-password   (set new password using token)
 *
 * Security behavior:
 * - We do NOT reveal whether an email exists in the system.
 * - Reset tokens expire (15 minutes) and are one-time-use.
 *
 * Global UI model attributes are injected by your ControllerAdvice
 * (guestMode, username, uiTier, themeClass, etc.).
 */
@Controller
public class PasswordResetController {

    private final UserRepository users;
    private final VerificationService verificationService;
    private final EmailService emailService;
    private final PasswordEncoder encoder;

    /**
     * Optional: stable base URL for links in email.
     * If blank, we fall back to current request host (local dev safe).
     */
    @Value("${app.base-url:}")
    private String baseUrl;

    public PasswordResetController(
            UserRepository users,
            VerificationService verificationService,
            EmailService emailService,
            PasswordEncoder encoder
    ) {
        this.users = users;
        this.verificationService = verificationService;
        this.emailService = emailService;
        this.encoder = encoder;
    }

    // =========================================================
    // Helpers
    // =========================================================

    private String resolveBaseUrl() {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.trim();
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private static String safeLowerTrim(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    // =========================================================
    // Pages
    // =========================================================

    /**
     * GET /forgot-password
     * Displays "enter your email" form.
     */
    @GetMapping("/forgot-password")
    public String forgot(Model model) {
        model.addAttribute("form", new ForgotPasswordForm());

        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Login", "url", "/login"),
                Map.of("label", "Forgot Password", "url", "")
        ));

        return "forgot-password";
    }

    /**
     * POST /forgot-password
     * Always redirects to /login?resetSent (even if email doesn't exist).
     */
    @PostMapping("/forgot-password")
    public String forgotSubmit(
            @Valid @ModelAttribute("form") ForgotPasswordForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("breadcrumbs", List.of(
                    Map.of("label", "Login", "url", "/login"),
                    Map.of("label", "Forgot Password", "url", "")
            ));
            return "forgot-password";
        }

        String requestedEmail = safeLowerTrim(form.getEmail());

        // Always redirect the same way (prevents account enumeration)
        users.findByEmail(requestedEmail).ifPresent(user -> {
            // ✅ Only allow reset if the email is verified
            if (!user.isEmailVerified()) return;

            // Optional but recommended: invalidate older reset tokens
            verificationService.invalidateActiveTokensForUser(user, TokenType.PASSWORD_RESET);

            // Recommended: bind token to the email via payload
            var token = verificationService.createToken(user, TokenType.PASSWORD_RESET, 15, requestedEmail);

            String link = resolveBaseUrl() + "/reset-password?token=" + token.getToken();

            emailService.send(
                    user.getEmail(),
                    "Reset your password",
                    "Click to reset:\n" + link + "\n\nThis link expires in 15 minutes."
            );
        });

        return "redirect:/login?resetSent";
    }

    /**
     * GET /reset-password?token=...
     * Shows the reset password form.
     *
     * If you want, you can also validate token here and redirect if invalid,
     * but validating on submit is usually enough.
     */
    @GetMapping("/reset-password")
    public String reset(
            @RequestParam("token") String token,
            @RequestParam(value = "error", required = false) String error,
            Model model
    ) {
        model.addAttribute("form", new ResetPasswordForm());
        model.addAttribute("token", token); // used by the template (hidden input)

        // Optional: display a friendly error message
        model.addAttribute("error", error);

        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Login", "url", "/login"),
                Map.of("label", "Reset Password", "url", "")
        ));

        return "reset-password";
    }

    /**
     * POST /reset-password
     * Validates token + sets new password.
     */
    @PostMapping("/reset-password")
    public String resetSubmit(
            @Valid @ModelAttribute("form") ResetPasswordForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("token", form.getToken());
            model.addAttribute("breadcrumbs", List.of(
                    Map.of("label", "Login", "url", "/login"),
                    Map.of("label", "Reset Password", "url", "")
            ));
            return "reset-password";
        }

        try {
            var t = verificationService.requireValid(form.getToken(), TokenType.PASSWORD_RESET);
            var user = t.getUser();

            // ✅ extra safety: only verified accounts can reset (in case a token was created earlier)
            if (!user.isEmailVerified()) {
                verificationService.markUsed(t);
                return "redirect:/reset-password?token=" + form.getToken() + "&error=invalid";
            }

            // ✅ payload binding: token must match the user’s current email
            String intendedEmail = safeLowerTrim(t.getPayload());
            String currentEmail = safeLowerTrim(user.getEmail());
            if (intendedEmail.isBlank() || !intendedEmail.equals(currentEmail)) {
                verificationService.markUsed(t);
                return "redirect:/reset-password?token=" + form.getToken() + "&error=invalid";
            }

            // Update password hash (this should immediately invalidate old password)
            user.setPasswordHash(encoder.encode(form.getNewPassword()));
            users.save(user);

            // One-time token
            verificationService.markUsed(t);

            // Recommended: invalidate any other reset tokens
            verificationService.invalidateActiveTokensForUser(user, TokenType.PASSWORD_RESET);

            return "redirect:/login?resetSuccess";

        } catch (RuntimeException ex) {
            return "redirect:/reset-password?token=" + form.getToken() + "&error=invalid";
        }
    }
}