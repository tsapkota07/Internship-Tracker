package com.tirsansapkota.internshiptracker.controller;

import com.tirsansapkota.internshiptracker.model.TokenType;
import com.tirsansapkota.internshiptracker.repository.UserRepository;
import com.tirsansapkota.internshiptracker.service.VerificationService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Email verification + email change confirmation.
 *
 * Links are emailed to users and look like:
 *   /account/verify-email/confirm?token=...
 *
 * Token types handled:
 *  - EMAIL_VERIFY: verifies the user's current email
 *  - EMAIL_CHANGE: confirms and applies pendingEmail (payload must match pendingEmail)
 *
 * NOTE:
 * GlobalUiAdvice injects navbar/theme/uiTier everywhere, so this controller
 * only focuses on the verification logic + redirects.
 */
@Controller
@RequestMapping("/account")
public class EmailVerificationController {

    private final VerificationService verificationService;
    private final UserRepository users;

    public EmailVerificationController(VerificationService verificationService, UserRepository users) {
        this.verificationService = verificationService;
        this.users = users;
    }

    /**
     * GET /account/verify-email/confirm?token=...
     *
     * Flow:
     * 1) Validate token (throws if invalid/expired/used)
     * 2) Apply changes based on token type
     * 3) Save user
     * 4) Mark token as used
     * 5) Redirect to account email page with a status flag
     */
    @GetMapping("/verify-email/confirm")
    public String verify(@RequestParam(name = "token", required = false) String token) {

        if (token == null || token.isBlank()) {
            return "redirect:/account/email?error=missingToken";
        }

        try {
            var t = verificationService.requireValid(token);
            var user = t.getUser();

            if (t.getType() == TokenType.EMAIL_VERIFY) {
                String intended = t.getPayload();
                String current = user.getEmail();

                if (intended == null || current == null || !intended.equalsIgnoreCase(current)) {
                    verificationService.markUsed(t);
                    return "redirect:/account/verify-email/success?status=mismatch";
                }

                user.setEmailVerified(true);
                users.save(user);
                verificationService.markUsed(t);

                return "redirect:/account/verify-email/success?status=verified";
            }

            if (t.getType() == TokenType.EMAIL_CHANGE) {
                String pendingFromToken = t.getPayload();

                if (pendingFromToken == null || pendingFromToken.isBlank()) {
                    return "redirect:/account/email?error=badtoken";
                }

                String pendingOnUser = user.getPendingEmail();
                if (pendingOnUser == null || !pendingFromToken.equalsIgnoreCase(pendingOnUser)) {
                    return "redirect:/account/email?error=mismatch";
                }

                user.setEmail(pendingOnUser);
                user.setEmailVerified(true);
                user.setPendingEmail(null);

                users.save(user);
                verificationService.markUsed(t);

                return "redirect:/account/verify-email/success?status=changed"; // (or your preferred target)
            }

            return "redirect:/account/email?error=unsupportedToken";

        } catch (IllegalArgumentException ex) {
            return "redirect:/account/email?error=invalidToken";
        } catch (Exception ex) {
            return "redirect:/account/email?error=serverError";
        }
    }

    /**
     * Optional "success" page if you still want it.
     * (You can remove this later if you fully switch to redirecting to /account/email.)
     */
    @GetMapping("/verify-email/success")
    public String success() {
        return "verify-success";
    }
}