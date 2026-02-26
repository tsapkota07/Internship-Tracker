package com.tirsansapkota.internshiptracker.config;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.InternshipApplication;
import com.tirsansapkota.internshiptracker.repository.UserRepository;
import com.tirsansapkota.internshiptracker.service.GuestApplicationStore;
import com.tirsansapkota.internshiptracker.service.InternshipApplicationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final GuestApplicationStore guestStore;
    private final InternshipApplicationService appService;
    private final UserRepository users;

    public LoginSuccessHandler(
            GuestApplicationStore guestStore,
            InternshipApplicationService appService,
            UserRepository users
    ) {
        this.guestStore = guestStore;
        this.appService = appService;
        this.users = users;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        HttpSession session = request.getSession(false);
        if (session != null) {
            String username = authentication.getName();
            AppUser owner = users.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("User not found after login: " + username));

            var guestApps = guestStore.getAll(session);
            for (InternshipApplication g : guestApps) {
                InternshipApplication copy = new InternshipApplication();
                copy.setCompany(g.getCompany());
                copy.setRole(g.getRole());
                copy.setLocation(g.getLocation());
                copy.setStatus(g.getStatus());
                copy.setAppliedDate(g.getAppliedDate() != null ? g.getAppliedDate() : LocalDate.now());
                copy.setLink(g.getLink());
                copy.setNotes(g.getNotes());
                copy.setOwner(owner);

                appService.save(copy);
            }

            // important: avoid migrating again on next login
            guestStore.clear(session);
        }

        // send them to /apps after login
        response.sendRedirect("/apps");
    }
}