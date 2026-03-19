package com.tirsansapkota.internshiptracker.controller;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.TokenType;
import com.tirsansapkota.internshiptracker.model.VerificationToken;
import com.tirsansapkota.internshiptracker.repository.UserRepository;
import com.tirsansapkota.internshiptracker.repository.VerificationTokenRepository;
import com.tirsansapkota.internshiptracker.service.EmailService;
import com.tirsansapkota.internshiptracker.service.UserService;
import com.tirsansapkota.internshiptracker.service.VerificationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/dev")
public class DevController {

    private final UserRepository users;
    private final UserService userService;
    private final VerificationService verificationService;
    private final VerificationTokenRepository tokenRepo;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom rng = new SecureRandom();

    public DevController(UserRepository users,
                         UserService userService,
                         VerificationService verificationService,
                         VerificationTokenRepository tokenRepo,
                         EmailService emailService,
                         PasswordEncoder passwordEncoder) {
        this.users = users;
        this.userService = userService;
        this.verificationService = verificationService;
        this.tokenRepo = tokenRepo;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    // -------------------------------------------------------
    // GET /dev/users — list all users
    // -------------------------------------------------------
    @GetMapping("/users")
    public String listUsers(Model model) {
        List<AppUser> allUsers = users.findAll();
        AppUser self = userService.getCurrentUser();

        model.addAttribute("allUsers", allUsers);
        model.addAttribute("selfId", self.getId());
        return "dev/users";
    }

    // -------------------------------------------------------
    // POST /dev/users/{id}/delete — delete a user account
    // -------------------------------------------------------
    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id,
                             RedirectAttributes ra) {
        AppUser self = userService.getCurrentUser();
        if (self.getId().equals(id)) {
            ra.addFlashAttribute("error", "You cannot delete your own account from Dev Settings.");
            return "redirect:/dev/users";
        }

        AppUser target = users.findById(id).orElse(null);
        if (target == null) {
            ra.addFlashAttribute("error", "User not found.");
            return "redirect:/dev/users";
        }

        userService.deleteAccount(target.getUsername());
        ra.addFlashAttribute("success", "Account \"" + target.getUsername() + "\" deleted.");
        return "redirect:/dev/users";
    }

    // -------------------------------------------------------
    // POST /dev/users/{id}/send-otp — send OTP to user's email
    // -------------------------------------------------------
    @PostMapping("/users/{id}/send-otp")
    public String sendOtp(@PathVariable Long id,
                          RedirectAttributes ra) {
        AppUser target = users.findById(id).orElse(null);
        if (target == null) {
            ra.addFlashAttribute("error", "User not found.");
            return "redirect:/dev/users";
        }

        if (target.getEmail() == null || target.getEmail().isBlank()) {
            ra.addFlashAttribute("error",
                    "Cannot send OTP: \"" + target.getUsername() + "\" has no email on file.");
            return "redirect:/dev/users";
        }

        // Invalidate any previous DEV_OTP tokens for this user
        verificationService.invalidateActiveTokensForUser(target, TokenType.DEV_OTP);

        // Generate a 6-digit OTP and store it in the token payload
        String otp = String.format("%06d", rng.nextInt(1_000_000));
        verificationService.createToken(target, TokenType.DEV_OTP, 15, otp);

        // Email it to the user
        String body = """
                <p style="margin:0 0 20px;font-size:0.95rem;color:#374151;">
                  Hi <strong>%s</strong>,
                </p>
                <p style="margin:0 0 24px;font-size:0.9rem;color:#6b7280;line-height:1.6;">
                  The site admin has requested a password change for your account.
                  Share the code below with them to confirm.
                </p>

                <div style="text-align:center;margin:0 0 28px;">
                  <div style="display:inline-block;background:#f6f5f2;border:1px solid #e5e7eb;
                              border-radius:12px;padding:20px 40px;">
                    <div style="font-size:0.72rem;font-weight:600;letter-spacing:0.1em;
                                color:#9ca3af;text-transform:uppercase;margin-bottom:8px;">
                      Your one-time code
                    </div>
                    <div style="font-size:2.4rem;font-weight:800;letter-spacing:0.18em;color:#111827;">
                      %s
                    </div>
                    <div style="font-size:0.78rem;color:#9ca3af;margin-top:8px;">
                      Expires in 15 minutes
                    </div>
                  </div>
                </div>

                <p style="margin:0;font-size:0.82rem;color:#9ca3af;line-height:1.5;
                           border-top:1px solid #e5e7eb;padding-top:16px;">
                  If you did not expect this request, you can safely ignore this email.
                  Your password will not change unless you share this code.
                </p>
                """.formatted(target.getUsername(), otp);

        emailService.sendHtml(
                target.getEmail(),
                "Your one-time code — Internship Tracker",
                emailService.buildTemplate("Your password change code is " + otp, body)
        );

        ra.addFlashAttribute("otpSentFor", id);
        ra.addFlashAttribute("otpSentUsername", target.getUsername());
        return "redirect:/dev/users";
    }

    // -------------------------------------------------------
    // GET /dev/users/{id}/change-password — show form
    // -------------------------------------------------------
    @GetMapping("/users/{id}/change-password")
    public String changePasswordForm(@PathVariable Long id, Model model) {
        AppUser target = users.findById(id).orElse(null);
        if (target == null) return "redirect:/dev/users";

        model.addAttribute("targetUser", target);
        return "dev/change-password";
    }

    // -------------------------------------------------------
    // POST /dev/users/{id}/change-password — verify OTP + update password
    // -------------------------------------------------------
    @PostMapping("/users/{id}/change-password")
    public String changePassword(@PathVariable Long id,
                                 @RequestParam String otp,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        AppUser target = users.findById(id).orElse(null);
        if (target == null) {
            ra.addFlashAttribute("error", "User not found.");
            return "redirect:/dev/users";
        }

        // Validate inputs
        if (newPassword == null || newPassword.length() < 8) {
            ra.addFlashAttribute("cpError", "Password must be at least 8 characters.");
            ra.addFlashAttribute("cpUserId", id);
            return "redirect:/dev/users/" + id + "/change-password";
        }
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("cpError", "Passwords do not match.");
            ra.addFlashAttribute("cpUserId", id);
            return "redirect:/dev/users/" + id + "/change-password";
        }

        // Find an active DEV_OTP token for this user
        List<VerificationToken> active = tokenRepo.findActiveByUserAndType(
                target, TokenType.DEV_OTP, LocalDateTime.now());

        boolean otpValid = active.stream().anyMatch(t -> otp.trim().equals(t.getPayload()));

        if (!otpValid) {
            ra.addFlashAttribute("cpError", "Invalid or expired OTP. Please send a new one.");
            ra.addFlashAttribute("cpUserId", id);
            return "redirect:/dev/users/" + id + "/change-password";
        }

        // Consume the matching token
        active.stream()
              .filter(t -> otp.trim().equals(t.getPayload()))
              .findFirst()
              .ifPresent(verificationService::markUsed);

        // Update password
        target.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(target);

        ra.addFlashAttribute("success",
                "Password for \"" + target.getUsername() + "\" updated successfully.");
        return "redirect:/dev/users";
    }
}
