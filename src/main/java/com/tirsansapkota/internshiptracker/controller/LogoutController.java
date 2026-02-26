package com.tirsansapkota.internshiptracker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * LogoutController
 *
 * Logout confirmation is handled via a modal.
 * Spring Security performs the logout at POST /logout.
 *
 * This controller only serves the post-logout landing page.
 */
@Controller
public class LogoutController {

    /**
     * GET /logout-success
     *
     * Landing page after a successful logout.
     * No navbar. No breadcrumbs. Auth-style page only.
     */
    @GetMapping("/logout-success")
    public String logoutSuccess() {
        return "logout-success";
    }
}