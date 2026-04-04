package com.tirsansapkota.internshiptracker.controller;

import com.tirsansapkota.internshiptracker.model.InternshipApplication;
import com.tirsansapkota.internshiptracker.service.InternshipApplicationService;
import com.tirsansapkota.internshiptracker.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
public class ExportController {

    private final InternshipApplicationService service;
    private final UserService userService;

    public ExportController(InternshipApplicationService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping("/export")
    public String exportPage(Model model) {
        if (!userService.isLoggedIn()) return "redirect:/login";

        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Applications", "url", "/apps"),
                Map.of("label", "Export", "url", "")
        ));

        return "export";
    }

    @GetMapping("/export/apps.csv")
    public void exportCsv(HttpServletResponse response) throws IOException {
        if (!userService.isLoggedIn()) {
            response.sendRedirect("/login");
            return;
        }

        String username = userService.getCurrentUser().getUsername();
        List<InternshipApplication> apps = service.getAllForUser(username);

        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"internship-apps.csv\"");

        try (PrintWriter out = response.getWriter()) {
            // BOM helps Excel interpret UTF-8 properly
            out.write('\uFEFF');

            out.println("company,role,status,appliedDate,location,url,description");

            DateTimeFormatter dateFmt = DateTimeFormatter.ISO_LOCAL_DATE;

            for (InternshipApplication a : apps) {
                String applied = (a.getAppliedDate() == null) ? "" : a.getAppliedDate().format(dateFmt);

                out.println(String.join(",",
                        csv(a.getCompany()),
                        csv(a.getRole()),
                        csv(a.getStatus() == null ? "" : a.getStatus().name()),
                        csv(applied),
                        csv(a.getLocation()),
                        csv(a.getLink()),
                        csv(a.getNotes())
                ));
            }
        }
    }

    private String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\r\n", "\n");
        boolean mustQuote = v.contains(",") || v.contains("\"") || v.contains("\n");
        if (mustQuote) {
            v = v.replace("\"", "\"\"");
            return "\"" + v + "\"";
        }
        return v;
    }
}