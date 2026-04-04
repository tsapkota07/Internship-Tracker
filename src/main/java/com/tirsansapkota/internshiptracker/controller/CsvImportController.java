package com.tirsansapkota.internshiptracker.controller;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.ApplicationStatus;
import com.tirsansapkota.internshiptracker.model.InternshipApplication;
import com.tirsansapkota.internshiptracker.service.InternshipApplicationService;
import com.tirsansapkota.internshiptracker.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class CsvImportController {

    private static final String SESSION_KEY = "CSV_IMPORT_APPS";

    private final InternshipApplicationService service;
    private final UserService userService;

    public CsvImportController(InternshipApplicationService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    // =========================================================
    // PAGE: /apps/import-csv (Upload form)
    // =========================================================

    @GetMapping("/apps/import-csv")
    public String uploadPage(Model model) {
        if (!userService.isLoggedIn()) return "redirect:/login";

        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Applications", "url", "/apps"),
                Map.of("label", "Import CSV", "url", "")
        ));

        return "apps-import-csv";
    }

    // =========================================================
    // ACTION: POST /apps/import-csv (Parse CSV, store in session)
    // =========================================================

    @PostMapping("/apps/import-csv")
    public String uploadCsv(@RequestParam("file") MultipartFile file,
                            HttpSession session,
                            RedirectAttributes ra) {
        if (!userService.isLoggedIn()) return "redirect:/login";

        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select a CSV file to upload.");
            return "redirect:/apps/import-csv";
        }

        List<InternshipApplication> parsed = new ArrayList<>();
        int skipped = 0;

        try {
            String raw = new String(file.getBytes(), StandardCharsets.UTF_8);

            // Strip BOM if present
            if (raw.startsWith("\uFEFF")) raw = raw.substring(1);

            List<List<String>> rows = parseCsvAll(raw);
            if (rows.isEmpty()) {
                ra.addFlashAttribute("error", "The file is empty.");
                return "redirect:/apps/import-csv";
            }

            // Skip header row (index 0)
            long idSeq = 1L;
            for (int i = 1; i < rows.size(); i++) {
                List<String> cols = rows.get(i);

                // Skip blank rows (single empty field)
                if (cols.size() == 1 && cols.get(0).isBlank()) continue;

                // company (col 0) and role (col 1) are required
                if (cols.size() < 2 || cols.get(0).isBlank() || cols.get(1).isBlank()) {
                    skipped++;
                    continue;
                }

                InternshipApplication app = new InternshipApplication();
                app.setId(idSeq++);
                app.setCompany(cols.get(0));
                app.setRole(cols.get(1));

                // status
                if (cols.size() > 2 && !cols.get(2).isBlank()) {
                    try {
                        app.setStatus(ApplicationStatus.valueOf(cols.get(2).toUpperCase().trim()));
                    } catch (IllegalArgumentException e) {
                        app.setStatus(ApplicationStatus.APPLIED);
                    }
                } else {
                    app.setStatus(ApplicationStatus.APPLIED);
                }

                // appliedDate
                if (cols.size() > 3 && !cols.get(3).isBlank()) {
                    try {
                        app.setAppliedDate(LocalDate.parse(cols.get(3).trim()));
                    } catch (DateTimeParseException e) {
                        app.setAppliedDate(LocalDate.now());
                    }
                } else {
                    app.setAppliedDate(LocalDate.now());
                }

                // location, url (link), description (notes)
                if (cols.size() > 4 && !cols.get(4).isBlank()) app.setLocation(cols.get(4));
                if (cols.size() > 5 && !cols.get(5).isBlank()) app.setLink(cols.get(5));
                if (cols.size() > 6 && !cols.get(6).isBlank()) {
                    String notes = cols.get(6);
                    if (notes.length() > 2000) notes = notes.substring(0, 2000);
                    app.setNotes(notes);
                }

                parsed.add(app);
            }

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to read the file. Make sure it's a valid CSV.");
            return "redirect:/apps/import-csv";
        }

        if (parsed.isEmpty()) {
            String msg = "No valid rows found in the file.";
            if (skipped > 0) msg += " " + skipped + " row(s) skipped (missing company or role).";
            ra.addFlashAttribute("error", msg);
            return "redirect:/apps/import-csv";
        }

        session.setAttribute(SESSION_KEY, parsed);

        if (skipped > 0) {
            ra.addFlashAttribute("warn", skipped + " row(s) were skipped due to missing company or role.");
        }

        return "redirect:/apps/import-csv/select";
    }

    // =========================================================
    // PAGE: /apps/import-csv/select (Review + select rows)
    // =========================================================

    @GetMapping("/apps/import-csv/select")
    public String selectPage(Model model, HttpSession session) {
        if (!userService.isLoggedIn()) return "redirect:/login";

        @SuppressWarnings("unchecked")
        List<InternshipApplication> apps =
                (List<InternshipApplication>) session.getAttribute(SESSION_KEY);
        if (apps == null) apps = List.of();

        model.addAttribute("csvApps", apps);
        model.addAttribute("csvAppsCount", apps.size());
        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Applications", "url", "/apps"),
                Map.of("label", "Import CSV", "url", "/apps/import-csv"),
                Map.of("label", "Select", "url", "")
        ));

        return "apps-import-csv-select";
    }

    // =========================================================
    // ACTION: POST /apps/import-csv/import-selected
    // =========================================================

    @PostMapping("/apps/import-csv/import-selected")
    public String importSelected(@RequestParam(name = "ids", required = false) List<Long> ids,
                                 HttpSession session,
                                 RedirectAttributes ra) {
        if (!userService.isLoggedIn()) return "redirect:/login";
        if (ids == null || ids.isEmpty()) return "redirect:/apps/import-csv/select";

        @SuppressWarnings("unchecked")
        List<InternshipApplication> all =
                (List<InternshipApplication>) session.getAttribute(SESSION_KEY);
        if (all == null) return "redirect:/apps";

        AppUser user = userService.getCurrentUser();

        List<InternshipApplication> selected = all.stream()
                .filter(a -> a.getId() != null && ids.contains(a.getId()))
                .toList();

        for (InternshipApplication a : selected) {
            InternshipApplication copy = new InternshipApplication();
            copy.setCompany(a.getCompany());
            copy.setRole(a.getRole());
            copy.setLocation(a.getLocation());
            copy.setStatus(a.getStatus());
            copy.setAppliedDate(a.getAppliedDate());
            copy.setLink(a.getLink());
            copy.setNotes(a.getNotes());
            copy.setOwner(user);
            service.save(copy);
        }

        session.removeAttribute(SESSION_KEY);
        ra.addFlashAttribute("success", selected.size() + " application(s) imported.");
        return "redirect:/apps";
    }

    // =========================================================
    // ACTION: POST /apps/import-csv/discard (Cancel, clear session)
    // =========================================================

    @PostMapping("/apps/import-csv/discard")
    public String discard(HttpSession session) {
        if (!userService.isLoggedIn()) return "redirect:/login";
        session.removeAttribute(SESSION_KEY);
        return "redirect:/apps";
    }

    // =========================================================
    // Full CSV parser — handles quoted fields that span multiple lines
    // =========================================================

    private List<List<String>> parseCsvAll(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    currentRow.add(field.toString().trim());
                    field.setLength(0);
                } else if (c == '\n') {
                    currentRow.add(field.toString().trim());
                    field.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                } else if (c == '\r') {
                    // skip — \r\n handled by \n above
                } else {
                    field.append(c);
                }
            }
        }

        // last field/row
        currentRow.add(field.toString().trim());
        if (!currentRow.isEmpty()) rows.add(currentRow);

        return rows;
    }
}
