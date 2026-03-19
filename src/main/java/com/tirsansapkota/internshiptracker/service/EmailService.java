package com.tirsansapkota.internshiptracker.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.base-url}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // Plain-text fallback (kept for internal use)
    public void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }

    // HTML email, optional replyTo
    public void sendHtml(String to, String subject, String htmlBody, String replyTo) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(mime, "UTF-8");
            h.setFrom(from);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(htmlBody, true);
            if (replyTo != null && !replyTo.isBlank()) {
                h.setReplyTo(replyTo);
            }
            mailSender.send(mime);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send HTML email", e);
        }
    }

    public void sendHtml(String to, String subject, String htmlBody) {
        sendHtml(to, subject, htmlBody, null);
    }

    // -------------------------------------------------------
    // Shared HTML shell — inline styles, email-safe
    // -------------------------------------------------------
    public String buildTemplate(String preheader, String bodyHtml) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1"/>
              <title>Internship Tracker</title>
            </head>
            <body style="margin:0;padding:0;background:#f6f5f2;font-family:'Inter',Arial,sans-serif;color:#111827;">

              <!-- preheader text (shown in inbox preview, hidden in body) -->
              <span style="display:none;max-height:0;overflow:hidden;font-size:1px;color:#f6f5f2;">
            """ + preheader + """
              </span>

              <table width="100%" cellpadding="0" cellspacing="0" role="presentation"
                     style="background:#f6f5f2;padding:40px 16px;">
                <tr>
                  <td align="center">
                    <table width="100%" cellpadding="0" cellspacing="0" role="presentation"
                           style="max-width:560px;background:#ffffff;border-radius:14px;
                                  border:1px solid #e5e7eb;overflow:hidden;">

                      <!-- Header -->
                      <tr>
                        <td style="padding:22px 32px;border-bottom:1px solid #e5e7eb;">
                          <span style="font-size:1rem;font-weight:800;color:#c85d2e;
                                       letter-spacing:-0.02em;">Internship Tracker</span>
                        </td>
                      </tr>

                      <!-- Body -->
                      <tr>
                        <td style="padding:32px;">
                          """ + bodyHtml + """
                        </td>
                      </tr>

                      <!-- Footer -->
                      <tr>
                        <td style="padding:18px 32px;border-top:1px solid #e5e7eb;background:#f9fafb;">
                          <span style="font-size:0.78rem;color:#9ca3af;">
                            """ + baseUrl.replaceFirst("https?://", "") + """
                          </span>
                        </td>
                      </tr>

                    </table>
                  </td>
                </tr>
              </table>

            </body>
            </html>
            """;
    }
}
