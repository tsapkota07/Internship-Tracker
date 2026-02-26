package com.tirsansapkota.internshiptracker.controller;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import com.tirsansapkota.internshiptracker.dto.DeleteAccountForm;
import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.TokenType;
import com.tirsansapkota.internshiptracker.service.EmailService;
import com.tirsansapkota.internshiptracker.service.UserService;
import com.tirsansapkota.internshiptracker.service.VerificationService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/account")
public class AccountController {

    private final UserService userService;
    private final VerificationService verificationService;
    private final EmailService emailService;

    @Value("${app.base-url:}")
    private String baseUrl;

    public AccountController(UserService userService,
                             VerificationService verificationService,
                             EmailService emailService) {
        this.userService = userService;
        this.verificationService = verificationService;
        this.emailService = emailService;
    }

    // =========================================================
    // Form objects
    // =========================================================
    public static class EmailForm {
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email")
        private String email;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    // =========================================================
    // Helpers
    // =========================================================
    private static String safeTrim(String s) {
        return (s == null) ? "" : s.trim();
    }

    private String resolveBaseUrl() {
        if (baseUrl != null && !baseUrl.isBlank()) return baseUrl.trim();
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private String buildVerifyLink(String token) {
        return resolveBaseUrl() + "/account/verify-email/confirm?token=" + token;
    }

    private void addAccountPageModel(Model model) {
        model.addAttribute("form", new DeleteAccountForm());
        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Applications", "url", "/apps"),
                Map.of("label", "Account Settings", "url", "")
        ));
    }

    private void addAccountEmailPageModel(Model model) {
        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Applications", "url", "/apps"),
                Map.of("label", "Account Settings", "url", "/account"),
                Map.of("label", "Email Setup", "url", "")
        ));
    }

    // =========================================================
    // PAGES
    // =========================================================
    @GetMapping
    public String accountPage(Model model) {
        addAccountPageModel(model);
        return "account";
    }

    @GetMapping("/email")
    public String emailPage(Model model,
                            @RequestParam(value = "required", required = false) String required,
                            @RequestParam(value = "verifyRequired", required = false) String verifyRequired,
                            @RequestParam(value = "sent", required = false) String sent,
                            @RequestParam(value = "pending", required = false) String pending,
                            @RequestParam(value = "cancelled", required = false) String cancelled,
                            @RequestParam(value = "verified", required = false) String verified,
                            @RequestParam(value = "error", required = false) String error) {

        var user = userService.getCurrentUser();

        // If verified, collapse noisy states (optional but keeps UI clean)
        if (user.isEmailVerified()
                && (sent != null || verifyRequired != null || required != null)
                && verified == null && pending == null) {
            return "redirect:/account/email?verified=1";
        }

        var form = new EmailForm();

        // Prefill rule:
        // - If user is unverified: keep input empty (encourage "use a different email")
        // - If verified: allow typing a new email (blank by default)
        // - If there is pendingEmail: optionally prefill with it (your choice)
        if (!user.isEmailVerified()) {
            form.setEmail("");
        } else {
            // For verified users, don't prefill with current email (prevents showing twice)
            // If you DO want to show pending email in input, you can change this to:
            // form.setEmail(safeTrim(user.getPendingEmail()));
            form.setEmail("");
        }

        model.addAttribute("user", user);
        model.addAttribute("form", form);

        // Flags
        model.addAttribute("required", required != null);
        model.addAttribute("verifyRequired", verifyRequired != null);
        model.addAttribute("sent", sent != null);
        model.addAttribute("pending", pending != null);
        model.addAttribute("cancelled", cancelled != null);
        model.addAttribute("verified", verified != null);
        model.addAttribute("error", error);

        addAccountEmailPageModel(model);
        return "account-email";
    }

    // =========================================================
    // ACTIONS: Email setup/change
    // =========================================================
    @PostMapping("/email")
    public String submitEmail(@Valid @ModelAttribute("form") EmailForm form,
                              BindingResult bindingResult,
                              Model model) {

        var user = userService.getCurrentUser();

        // For demo account, don't allow submission of email.
        // =========================================================
        if (user.isDemoUser()) {
            return "redirect:/account/email?error=demoLocked";
        }

        // Actual Purpose:
        // =========================================================
        String newEmail = safeTrim(form.getEmail()).toLowerCase();

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            addAccountEmailPageModel(model);
            return "account-email";
        }

        if (newEmail.isBlank()) {
            model.addAttribute("user", user);
            model.addAttribute("error", "Email is required");
            addAccountEmailPageModel(model);
            return "account-email";
        }

        try {
            String currentEmail = safeTrim(user.getEmail());
            boolean hasEmail = !currentEmail.isBlank();
            boolean isVerified = user.isEmailVerified();
            boolean sameAsCurrent = hasEmail && currentEmail.equalsIgnoreCase(newEmail);

            // -----------------------------------------------------
            // FLOW 1: No email yet -> set email unverified + verify
            // -----------------------------------------------------
            if (!hasEmail) {
                // kill old verify links (just in case)
                verificationService.invalidateActiveTokensForUser(user, TokenType.EMAIL_VERIFY);

                userService.setEmailUnverified(user, newEmail); // should set emailVerified=false, pendingEmail=null

                var token = verificationService.createToken(user, TokenType.EMAIL_VERIFY, 60, newEmail);
                emailService.send(
                        newEmail,
                        "Verify your email",
                        "Click to verify:\n" + buildVerifyLink(token.getToken()) +
                                "\n\nThis link expires in 60 minutes."
                );
                return "redirect:/account/email?sent=1";
            }

            // -----------------------------------------------------
            // FLOW 2: User currently UNVERIFIED
            // - same email -> resend
            // - new email -> replace email immediately + send verify
            // -----------------------------------------------------
            if (!isVerified) {
                // Same email typed again -> resend
                if (sameAsCurrent) {
                    verificationService.invalidateActiveTokensForUser(user, TokenType.EMAIL_VERIFY);

                    var token = verificationService.createToken(user, TokenType.EMAIL_VERIFY, 60, newEmail);
                    emailService.send(
                            newEmail,
                            "Verify your email",
                            "Click to verify:\n" + buildVerifyLink(token.getToken()) +
                                    "\n\nThis link expires in 60 minutes."
                    );
                    return "redirect:/account/email?sent=1";
                }

                // Different email -> replace immediately, invalidate old tokens, send verify to new
                verificationService.invalidateActiveTokensForUser(user, TokenType.EMAIL_VERIFY);

                userService.setEmailUnverified(user, newEmail); // replaces email, clears pendingEmail, verified=false

                var token = verificationService.createToken(user, TokenType.EMAIL_VERIFY, 60, newEmail);
                emailService.send(
                        newEmail,
                        "Verify your email",
                        "Click to verify:\n" + buildVerifyLink(token.getToken()) +
                                "\n\nThis link expires in 60 minutes."
                );
                return "redirect:/account/email?sent=1";
            }

            // -----------------------------------------------------
            // FLOW 3: User currently VERIFIED
            // - same email -> nothing
            // - new email -> pendingEmail + EMAIL_CHANGE confirmation
            // -----------------------------------------------------
            if (sameAsCurrent) {
                return "redirect:/account/email?verified=1";
            }

            // Changing from verified email -> pendingEmail flow
            userService.setPendingEmail(user, newEmail);

            // kill old change links so old pending links don't work
            verificationService.invalidateActiveTokensForUser(user, TokenType.EMAIL_CHANGE);

            var token = verificationService.createToken(user, TokenType.EMAIL_CHANGE, 60, newEmail);
            emailService.send(
                    newEmail,
                    "Confirm your new email",
                    "Click to confirm:\n" + buildVerifyLink(token.getToken()) +
                            "\n\nThis link expires in 60 minutes."
            );

            return "redirect:/account/email?pending=1";

        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            model.addAttribute("user", user);
            model.addAttribute("form", form);
            model.addAttribute("error", "That email is already in use.");
            addAccountEmailPageModel(model);
            return "account-email";
        }

        catch (IllegalArgumentException ex) {
            model.addAttribute("user", user);
            model.addAttribute("form", form);
            model.addAttribute("error", ex.getMessage());
            addAccountEmailPageModel(model);
            return "account-email";
        }
    }

    /**
     * POST /account/email/cancel-pending
     * Cancels a pending email change (only relevant if user has verified email).
     */
    @PostMapping("/email/cancel-pending")
    public String cancelPendingEmail() {
        var user = userService.getCurrentUser();

        // =========================================================
        // For demo account, don't allow cancelling of email.
        // =========================================================
        if (user.isDemoUser()) return "redirect:/account/email?error=demoLocked";


        if (!safeTrim(user.getPendingEmail()).isBlank()) {
            userService.setPendingEmail(user, null);
            verificationService.invalidateActiveTokensForUser(user, TokenType.EMAIL_CHANGE);
        }

        return "redirect:/account/email?cancelled=1";
    }

    /**
     * POST /account/verify-email/resend
     * Resends verification to the CURRENT email (only if unverified).
     */
    @PostMapping("/verify-email/resend")
    public String resendVerification() {
        var user = userService.getCurrentUser();


        // =========================================================
        // For demo account, don't allow resending of verification.(Safety measure)
        // =========================================================
        if (user.isDemoUser()) return "redirect:/account/email?error=demoLocked";

        String email = safeTrim(user.getEmail());

        if (email.isBlank()) return "redirect:/account/email?error=noemail";
        if (user.isEmailVerified()) return "redirect:/account/email?verified=1";

        // invalidate older verify links so only latest works
        verificationService.invalidateActiveTokensForUser(user, TokenType.EMAIL_VERIFY);

        var token = verificationService.createToken(user, TokenType.EMAIL_VERIFY, 60, email);
        emailService.send(
                email,
                "Verify your email",
                "Click to verify:\n" + buildVerifyLink(token.getToken()) +
                        "\n\nThis link expires in 60 minutes."
        );

        return "redirect:/account/email?sent=1";
    }

    // =========================================================
    // API: email status (for polling)
    // =========================================================
    @GetMapping("/email/status")
    @ResponseBody
    public Map<String, Object> emailStatus() {
        var current = userService.getCurrentUser();
        var fresh = userService.findById(current.getId()).orElseThrow();

        return Map.of(
                "email", safeTrim(fresh.getEmail()),
                "pendingEmail", safeTrim(fresh.getPendingEmail()),
                "emailVerified", fresh.isEmailVerified()
        );
    }

    // =========================================================
    // ACTION: delete account
    // =========================================================
    @PostMapping("/delete")
    public String deleteAccount(@Valid @ModelAttribute("form") DeleteAccountForm form,
                                BindingResult bindingResult,
                                Model model,
                                HttpServletRequest request) {

        var user = userService.getCurrentUser();

        if (user.isDemoUser()) {
            model.addAttribute("deleteError", "Demo accounts cannot be deleted.");
            addAccountPageModel(model);
            return "account";
        }

        if (bindingResult.hasErrors()) {
            addAccountPageModel(model);
            return "account";
        }

        String loggedInUsername = user.getUsername();

        if (!safeTrim(form.getUsername()).equals(loggedInUsername)) {
            model.addAttribute("deleteError", "Username does not match the logged-in account.");
            addAccountPageModel(model);
            return "account";
        }

        if (!userService.matchesPassword(loggedInUsername, form.getPassword())) {
            model.addAttribute("deleteError", "Incorrect password.");
            addAccountPageModel(model);
            return "account";
        }

        // 1) Delete DB data
        userService.deleteAccount(loggedInUsername);

        // 2) Logout user from current session (IMPORTANT)
        SecurityContextHolder.clearContext();

        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();

        // Optional: also ask container to logout (safe extra cleanup)
        try { request.logout(); } catch (Exception ignored) {}

        // 3) Redirect to a real page (not /logout)
        return "redirect:/logout-success";
    }
}