package com.tirsansapkota.internshiptracker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * HomeController
 *
 * app's "home" is the Applications page.
 * So "/" simply redirects to "/apps".
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/apps";
    }
}