package com.tirsansapkota.internshiptracker.controller;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.repository.UserRepository;
import com.tirsansapkota.internshiptracker.service.EmailService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class ContactController {

    private final UserRepository users;
    private final EmailService emailService;

    public ContactController(UserRepository users, EmailService emailService) {
        this.users = users;
        this.emailService = emailService;
    }

    @GetMapping("/contact")
    public String contactPage() {
        return "contact";
    }

    @PostMapping("/contact")
    public String submitContact(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String message,
            RedirectAttributes ra) {

        // Basic validation
        if (name.isBlank() || email.isBlank() || message.isBlank()) {
            ra.addFlashAttribute("contactError", "All fields are required.");
            ra.addFlashAttribute("prevName", name);
            ra.addFlashAttribute("prevEmail", email);
            ra.addFlashAttribute("prevMessage", message);
            return "redirect:/contact";
        }

        String timestamp = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a 'UTC'"));

        String htmlBody = """
                <h2 style="margin:0 0 20px;font-size:1.1rem;font-weight:700;color:#111827;">
                  New Message
                </h2>

                <!-- Ticket metadata -->
                <table cellpadding="0" cellspacing="0" width="100%%"
                       style="margin-bottom:24px;border:1px solid #e5e7eb;border-radius:10px;
                              overflow:hidden;font-size:0.875rem;">
                  <tr style="background:#f9fafb;">
                    <td style="padding:10px 16px;color:#6b7280;font-weight:600;
                               border-bottom:1px solid #e5e7eb;width:90px;">From</td>
                    <td style="padding:10px 16px;color:#111827;
                               border-bottom:1px solid #e5e7eb;">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:10px 16px;color:#6b7280;font-weight:600;
                               border-bottom:1px solid #e5e7eb;">Email</td>
                    <td style="padding:10px 16px;color:#111827;
                               border-bottom:1px solid #e5e7eb;">
                      <a href="mailto:%s" style="color:#c85d2e;text-decoration:none;">%s</a>
                    </td>
                  </tr>
                  <tr style="background:#f9fafb;">
                    <td style="padding:10px 16px;color:#6b7280;font-weight:600;">Sent</td>
                    <td style="padding:10px 16px;color:#111827;">%s</td>
                  </tr>
                </table>

                <!-- Message body -->
                <div style="background:#f6f5f2;border-radius:10px;padding:20px;
                            font-size:0.9rem;color:#374151;line-height:1.7;
                            white-space:pre-wrap;word-break:break-word;">%s</div>

                <p style="margin:20px 0 0;font-size:0.8rem;color:#9ca3af;">
                  Reply directly to this email to respond to %s.
                </p>
                """.formatted(name, email, email, timestamp, message, name);

        String subject = "Message from " + name + " — Internship Tracker";
        String html = emailService.buildTemplate("New message from " + name, htmlBody);

        // Send to all DEV users who have an email
        List<AppUser> devs = users.findAllByRole("DEV");
        for (AppUser dev : devs) {
            if (dev.getEmail() != null && !dev.getEmail().isBlank()) {
                emailService.sendHtml(dev.getEmail(), subject, html, email);
            }
        }

        ra.addFlashAttribute("contactSuccess", true);
        return "redirect:/contact";
    }
}
