package com.tirsansapkota.internshiptracker.web;

import com.tirsansapkota.internshiptracker.service.GuestApplicationStore;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NavbarModelAdvice {

    private final GuestApplicationStore guestStore;

    public NavbarModelAdvice(GuestApplicationStore guestStore) {
        this.guestStore = guestStore;
    }

    @ModelAttribute("guestMode")
    public boolean guestMode() {
        return !isLoggedIn();
    }

    @ModelAttribute("username")
    public String username() {
        if (!isLoggedIn()) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth == null) ? null : auth.getName();
    }

    @ModelAttribute("hasGuestApps")
    public boolean hasGuestApps(HttpSession session) {
        // Safe for both logged-in + guest; your store initializes session list if missing
        return !guestStore.getAll(session).isEmpty();
    }

    private boolean isLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }
}