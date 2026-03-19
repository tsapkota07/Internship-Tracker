package com.tirsansapkota.internshiptracker.controller;

import com.tirsansapkota.internshiptracker.model.*;
import com.tirsansapkota.internshiptracker.repository.UserPreferencesRepository;
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
import java.util.*;
import java.util.stream.*;

@Controller
@RequestMapping("/dev")
public class DevController {

    private final UserRepository users;
    private final UserService userService;
    private final VerificationService verificationService;
    private final VerificationTokenRepository tokenRepo;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final UserPreferencesRepository prefsRepo;
    private final SecureRandom rng = new SecureRandom();

    public DevController(UserRepository users,
                         UserService userService,
                         VerificationService verificationService,
                         VerificationTokenRepository tokenRepo,
                         EmailService emailService,
                         PasswordEncoder passwordEncoder,
                         UserPreferencesRepository prefsRepo) {
        this.users = users;
        this.userService = userService;
        this.verificationService = verificationService;
        this.tokenRepo = tokenRepo;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.prefsRepo = prefsRepo;
    }

    // -------------------------------------------------------
    // GET /dev/users — list all users (with sort + filter)
    // -------------------------------------------------------
    @GetMapping("/users")
    public String listUsers(
            @RequestParam(defaultValue = "id")  String sort,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(defaultValue = "all") String roleFilter,
            @RequestParam(defaultValue = "all") String verifiedFilter,
            Model model) {

        AppUser self = userService.getCurrentUser();
        Stream<AppUser> stream = users.findAll().stream();

        stream = switch (roleFilter) {
            case "users" -> stream.filter(u -> !u.isDemoUser() && !"DEV".equals(u.getRole()));
            case "devs"  -> stream.filter(u -> "DEV".equals(u.getRole()));
            case "demo"  -> stream.filter(AppUser::isDemoUser);
            default      -> stream;
        };

        stream = switch (verifiedFilter) {
            case "yes" -> stream.filter(AppUser::isEmailVerified);
            case "no"  -> stream.filter(u -> !u.isEmailVerified());
            default    -> stream;
        };

        Comparator<AppUser> comp = switch (sort) {
            case "username" -> Comparator.comparing(AppUser::getUsername, String.CASE_INSENSITIVE_ORDER);
            case "verified" -> Comparator.comparing(AppUser::isEmailVerified);
            case "email"    -> Comparator.comparing(u -> u.getEmail() == null ? "" : u.getEmail(),
                                                    String.CASE_INSENSITIVE_ORDER);
            default         -> Comparator.comparing(AppUser::getId);
        };
        if ("desc".equals(dir)) comp = comp.reversed();

        model.addAttribute("allUsers", stream.sorted(comp).collect(Collectors.toList()));
        model.addAttribute("selfId", self.getId());
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("roleFilter", roleFilter);
        model.addAttribute("verifiedFilter", verifiedFilter);
        return "dev/users";
    }

    // -------------------------------------------------------
    // POST /dev/users/{id}/delete
    // -------------------------------------------------------
    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes ra) {
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
    // POST /dev/users/{id}/send-otp  (password-change OTP)
    //   — not allowed for demo users
    // -------------------------------------------------------
    @PostMapping("/users/{id}/send-otp")
    public String sendOtp(@PathVariable Long id, RedirectAttributes ra) {
        AppUser target = users.findById(id).orElse(null);
        if (target == null) {
            ra.addFlashAttribute("error", "User not found.");
            return "redirect:/dev/users";
        }
        if (target.isDemoUser()) {
            ra.addFlashAttribute("error", "Cannot send OTP to demo users.");
            return "redirect:/dev/users";
        }
        if (target.getEmail() == null || target.getEmail().isBlank()) {
            ra.addFlashAttribute("error",
                    "Cannot send OTP: \"" + target.getUsername() + "\" has no email on file.");
            return "redirect:/dev/users";
        }

        verificationService.invalidateActiveTokensForUser(target, TokenType.DEV_OTP);
        String otp = String.format("%06d", rng.nextInt(1_000_000));
        verificationService.createToken(target, TokenType.DEV_OTP, 15, otp);

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

        try {
            emailService.sendHtml(
                    target.getEmail(),
                    "Your one-time code — Internship Tracker",
                    emailService.buildTemplate("Your password change code is " + otp, body)
            );
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to send OTP email: " + e.getMessage());
            return "redirect:/dev/users";
        }

        ra.addFlashAttribute("otpSentFor", id);
        ra.addFlashAttribute("otpSentUsername", target.getUsername());
        return "redirect:/dev/users";
    }

    // -------------------------------------------------------
    // GET /dev/users/{id}/change-password
    // -------------------------------------------------------
    @GetMapping("/users/{id}/change-password")
    public String changePasswordForm(@PathVariable Long id, Model model) {
        AppUser target = users.findById(id).orElse(null);
        if (target == null) return "redirect:/dev/users";
        model.addAttribute("targetUser", target);
        return "dev/change-password";
    }

    // -------------------------------------------------------
    // POST /dev/users/{id}/change-password
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
        if (newPassword == null || newPassword.length() < 8) {
            ra.addFlashAttribute("cpError", "Password must be at least 8 characters.");
            return "redirect:/dev/users/" + id + "/change-password";
        }
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("cpError", "Passwords do not match.");
            return "redirect:/dev/users/" + id + "/change-password";
        }

        List<VerificationToken> active = tokenRepo.findActiveByUserAndType(
                target, TokenType.DEV_OTP, LocalDateTime.now());
        boolean otpValid = active.stream().anyMatch(t -> otp.trim().equals(t.getPayload()));
        if (!otpValid) {
            ra.addFlashAttribute("cpError", "Invalid or expired OTP. Please send a new one.");
            return "redirect:/dev/users/" + id + "/change-password";
        }

        active.stream()
              .filter(t -> otp.trim().equals(t.getPayload()))
              .findFirst()
              .ifPresent(verificationService::markUsed);

        target.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(target);

        ra.addFlashAttribute("success",
                "Password for \"" + target.getUsername() + "\" updated successfully.");
        return "redirect:/dev/users";
    }

    // -------------------------------------------------------
    // POST /dev/users/{id}/unverify
    //   — not allowed for DEV users
    //   — resets theme to Light
    // -------------------------------------------------------
    @PostMapping("/users/{id}/unverify")
    public String unverifyUser(@PathVariable Long id, RedirectAttributes ra) {
        AppUser self = userService.getCurrentUser();
        if (self.getId().equals(id)) {
            ra.addFlashAttribute("error", "Cannot change your own verification status.");
            return "redirect:/dev/users";
        }
        AppUser target = users.findById(id).orElse(null);
        if (target == null) {
            ra.addFlashAttribute("error", "User not found.");
            return "redirect:/dev/users";
        }
        if ("DEV".equals(target.getRole())) {
            ra.addFlashAttribute("error", "Cannot change verification status of a dev user.");
            return "redirect:/dev/users";
        }
        if (!target.isEmailVerified()) {
            ra.addFlashAttribute("error",
                    "\"" + target.getUsername() + "\"'s email is already unverified.");
            return "redirect:/dev/users";
        }

        target.setEmailVerified(false);
        users.save(target);

        UserPreferences prefs = prefsRepo.findById(target.getId()).orElseGet(() -> {
            UserPreferences up = new UserPreferences();
            up.setUser(target);
            return up;
        });
        prefs.setTheme(Theme.LIGHT);
        prefsRepo.save(prefs);

        ra.addFlashAttribute("success",
                "\"" + target.getUsername() + "\" unverified and theme reset to Light.");
        return "redirect:/dev/users";
    }

    // -------------------------------------------------------
    // POST /dev/users/{id}/verify
    //   — demo users only
    // -------------------------------------------------------
    @PostMapping("/users/{id}/verify")
    public String verifyUser(@PathVariable Long id, RedirectAttributes ra) {
        AppUser target = users.findById(id).orElse(null);
        if (target == null) {
            ra.addFlashAttribute("error", "User not found.");
            return "redirect:/dev/users";
        }
        if (!target.isDemoUser()) {
            ra.addFlashAttribute("error", "Manual verification is only allowed for demo users.");
            return "redirect:/dev/users";
        }
        if (target.getEmail() == null || target.getEmail().isBlank()) {
            ra.addFlashAttribute("error", "User has no email to verify.");
            return "redirect:/dev/users";
        }
        if (target.isEmailVerified()) {
            ra.addFlashAttribute("error",
                    "\"" + target.getUsername() + "\"'s email is already verified.");
            return "redirect:/dev/users";
        }

        target.setEmailVerified(true);
        users.save(target);

        ra.addFlashAttribute("success",
                "\"" + target.getUsername() + "\" marked as verified.");
        return "redirect:/dev/users";
    }

    // -------------------------------------------------------
    // GET /dev/users/{id}/change-email — show form
    //   — USER role only, must be email-verified
    // -------------------------------------------------------
    @GetMapping("/users/{id}/change-email")
    public String changeEmailForm(@PathVariable Long id, Model model) {
        AppUser target = users.findById(id).orElse(null);
        if (target == null
                || "DEV".equals(target.getRole())
                || target.isDemoUser()
                || !target.isEmailVerified()) {
            return "redirect:/dev/users";
        }
        model.addAttribute("targetUser", target);
        return "dev/change-email";
    }

    // -------------------------------------------------------
    // POST /dev/users/{id}/change-email/send-otp
    // -------------------------------------------------------
    @PostMapping("/users/{id}/change-email/send-otp")
    public String sendEmailChangeOtp(@PathVariable Long id, RedirectAttributes ra) {
        AppUser target = users.findById(id).orElse(null);
        if (target == null
                || "DEV".equals(target.getRole())
                || target.isDemoUser()
                || !target.isEmailVerified()
                || target.getEmail() == null
                || target.getEmail().isBlank()) {
            ra.addFlashAttribute("ceError", "Cannot send OTP: user is not eligible.");
            return "redirect:/dev/users";
        }

        verificationService.invalidateActiveTokensForUser(target, TokenType.DEV_EMAIL_OTP);
        String otp = String.format("%06d", rng.nextInt(1_000_000));
        verificationService.createToken(target, TokenType.DEV_EMAIL_OTP, 15, otp);

        String body = """
                <p style="margin:0 0 20px;font-size:0.95rem;color:#374151;">
                  Hi <strong>%s</strong>,
                </p>
                <p style="margin:0 0 24px;font-size:0.9rem;color:#6b7280;line-height:1.6;">
                  The site admin has requested an email address change for your account.
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
                  Your email will not change unless you share this code.
                </p>
                """.formatted(target.getUsername(), otp);

        try {
            emailService.sendHtml(
                    target.getEmail(),
                    "Your email change code — Internship Tracker",
                    emailService.buildTemplate("Your email change code is " + otp, body)
            );
            ra.addFlashAttribute("ceSuccess", "OTP sent to " + target.getEmail() + ".");
        } catch (Exception e) {
            ra.addFlashAttribute("ceError", "Failed to send OTP email: " + e.getMessage());
        }

        return "redirect:/dev/users/" + id + "/change-email";
    }

    // -------------------------------------------------------
    // POST /dev/users/{id}/change-email — verify OTP + apply
    // -------------------------------------------------------
    @PostMapping("/users/{id}/change-email")
    public String changeEmail(@PathVariable Long id,
                              @RequestParam String otp,
                              @RequestParam String newEmail,
                              RedirectAttributes ra) {
        AppUser target = users.findById(id).orElse(null);
        if (target == null
                || "DEV".equals(target.getRole())
                || target.isDemoUser()
                || !target.isEmailVerified()) {
            ra.addFlashAttribute("error", "Email change not allowed for this user.");
            return "redirect:/dev/users";
        }

        if (newEmail == null || newEmail.isBlank()) {
            ra.addFlashAttribute("ceError", "New email is required.");
            return "redirect:/dev/users/" + id + "/change-email";
        }
        String normalized = newEmail.trim().toLowerCase();

        if (userService.isEmailTakenForOtherUser(normalized, target.getId())) {
            ra.addFlashAttribute("ceError", "That email is already in use by another account.");
            return "redirect:/dev/users/" + id + "/change-email";
        }

        List<VerificationToken> active = tokenRepo.findActiveByUserAndType(
                target, TokenType.DEV_EMAIL_OTP, LocalDateTime.now());
        boolean otpValid = active.stream().anyMatch(t -> otp.trim().equals(t.getPayload()));
        if (!otpValid) {
            ra.addFlashAttribute("ceError", "Invalid or expired OTP. Please send a new one.");
            return "redirect:/dev/users/" + id + "/change-email";
        }

        active.stream()
              .filter(t -> otp.trim().equals(t.getPayload()))
              .findFirst()
              .ifPresent(verificationService::markUsed);

        target.setEmail(normalized);
        target.setPendingEmail(null);
        users.save(target);

        ra.addFlashAttribute("success",
                "Email for \"" + target.getUsername() + "\" changed to " + normalized + ".");
        return "redirect:/dev/users";
    }
}
