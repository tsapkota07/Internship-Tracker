package com.tirsansapkota.internshiptracker.controller;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.ApplicationStatus;
import com.tirsansapkota.internshiptracker.model.InternshipApplication;
import com.tirsansapkota.internshiptracker.service.GuestApplicationStore;
import com.tirsansapkota.internshiptracker.service.InternshipApplicationService;
import com.tirsansapkota.internshiptracker.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Internship Applications feature:
 * - List + filters (home page)
 * - Create (guest stored in session, user stored in DB)
 * - Edit (guest session vs user DB)
 * - Soft delete + restore for logged-in users
 * - Guest import (all or selective) once user logs in
 *
 * NOTE:
 * GlobalUiAdvice injects UI globals like:
 * guestMode, username, uiTier, themeClass, showImportBanner, etc.
 * So this controller should only add page-specific model attributes.
 */
@Controller
public class InternshipApplicationController {

    private final InternshipApplicationService service;
    private final GuestApplicationStore guestStore;
    private final UserService userService;

    public InternshipApplicationController(
            InternshipApplicationService service,
            GuestApplicationStore guestStore,
            UserService userService
    ) {
        this.service = service;
        this.guestStore = guestStore;
        this.userService = userService;
    }

    // =========================================================
    // Auth helpers (logic-only; NOT for UI model attributes)
    // =========================================================

    /**
     * Returns true when Spring Security says the user is authenticated.
     */
    private boolean isLoggedIn() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    /**
     * Returns the current username if logged in; otherwise null.
     */
    private String currentUsernameOrNull() {
        if (!isLoggedIn()) return null;
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // =========================================================
    // PAGE: /apps (List + filter + dashboard stats)
    // =========================================================

    private static final int PAGE_SIZE = 20;

    @GetMapping("/apps")
    public String appsHome(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "date_desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            Model model,
            HttpSession session
    ) {
        boolean guestMode = !isLoggedIn();
        boolean filtersActive = (q != null && !q.isBlank()) || status != null;

        List<InternshipApplication> allApps;
        List<InternshipApplication> filteredApps;

        if (guestMode) {
            allApps = guestStore.getAll(session);
            filteredApps = filterGuestList(allApps, q, status);
        } else {
            String username = currentUsernameOrNull();
            allApps = service.getAllForUser(username);
            filteredApps = service.filterForUser(username, q, status);
        }

        // Sort
        filteredApps = sortApps(filteredApps, sort);

        // Pagination
        int totalItems = filteredApps.size();
        int totalPages = (totalItems == 0) ? 1 : (int) Math.ceil((double) totalItems / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, totalItems);
        List<InternshipApplication> pagedApps = filteredApps.subList(from, to);

        // Filter state
        model.addAttribute("filtersActive", filtersActive);
        model.addAttribute("apps", pagedApps);

        // Pagination state
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);

        // Dashboard stats always based on ALL apps (not filtered results)
        model.addAttribute("totalCount", allApps.size());
        model.addAttribute("appliedCount", allApps.stream().filter(a -> a.getStatus() == ApplicationStatus.APPLIED).count());
        model.addAttribute("interviewCount", allApps.stream().filter(a -> a.getStatus() == ApplicationStatus.INTERVIEW).count());
        model.addAttribute("offerCount", allApps.stream().filter(a -> a.getStatus() == ApplicationStatus.OFFER).count());
        model.addAttribute("rejectedCount", allApps.stream().filter(a -> a.getStatus() == ApplicationStatus.REJECTED).count());

        // Preserve filter inputs
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("sort", sort);

        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Applications", "url", "")
        ));

        return "apps-list";
    }

    // =========================================================
    // PAGE: /apps/new (Create form)
    // =========================================================

    @GetMapping("/apps/new")
    public String newAppForm(Model model) {
        model.addAttribute("app", new InternshipApplication());
        model.addAttribute("statuses", ApplicationStatus.values());

        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Applications", "url", "/apps"),
                Map.of("label", "New", "url", "")
        ));

        return "apps-new";
    }

    // =========================================================
    // ACTION: POST /apps (Create)
    // =========================================================

    @PostMapping("/apps")
    public String createApp(
            @Valid @ModelAttribute("app") InternshipApplication app,
            BindingResult bindingResult,
            Model model,
            HttpSession session
    ) {
        // Validation errors -> re-render the form
        if (bindingResult.hasErrors()) {
            model.addAttribute("statuses", ApplicationStatus.values());
            model.addAttribute("breadcrumbs", List.of(
                    Map.of("label", "Applications", "url", "/apps"),
                    Map.of("label", "New", "url", "")
            ));
            return "apps-new";
        }

        if (isLoggedIn()) {
            // Save to DB for logged-in user
            AppUser user = userService.getCurrentUser();
            app.setOwner(user);
            service.save(app);
        } else {
            // Save to session for guest
            guestStore.save(session, app);
        }

        return "redirect:/apps";
    }

    // =========================================================
    // ACTION: POST /apps/{id}/delete
    // - Logged in: soft delete in DB
    // - Guest: delete from session store
    // =========================================================

    @PostMapping("/apps/{id}/delete")
    public String deleteApp(@PathVariable Long id, HttpSession session) {
        if (isLoggedIn()) {
            service.softDeleteByIdForUser(id, currentUsernameOrNull());
        } else {
            guestStore.deleteById(session, id);
        }
        return "redirect:/apps";
    }

    /**
     * Safety GET route to prevent 405 or Whitelabel if someone pastes the delete URL.
     */
    @GetMapping("/apps/{id}/delete")
    public String deleteGet(@PathVariable Long id) {
        return "redirect:/apps";
    }

    // =========================================================
    // PAGE: /apps/deleted (Recently deleted)
    // =========================================================

    @GetMapping("/apps/deleted")
    public String deletedApps(Model model) {
        if (!isLoggedIn()) return "redirect:/apps";

        model.addAttribute("apps", service.getDeletedForUser(currentUsernameOrNull()));
        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Applications", "url", "/apps"),
                Map.of("label", "Recently Deleted", "url", "")
        ));

        return "apps-deleted";
    }

    // =========================================================
    // ACTIONS: restore / hard delete (logged-in only)
    // =========================================================

    @PostMapping("/apps/{id}/restore")
    public String restore(@PathVariable Long id) {
        if (isLoggedIn()) {
            service.restoreByIdForUser(id, currentUsernameOrNull());
        }
        return "redirect:/apps/deleted";
    }

    @PostMapping("/apps/{id}/delete-forever")
    public String deleteForever(@PathVariable Long id) {
        if (isLoggedIn()) {
            service.hardDeleteByIdForUser(id, currentUsernameOrNull());
        }
        return "redirect:/apps/deleted";
    }

    // =========================================================
    // PAGE: /apps/{id}/edit (Edit form)
    // =========================================================

    @GetMapping("/apps/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, HttpSession session) {
        InternshipApplication app;

        if (isLoggedIn()) {
            app = service.getByIdForUser(id, currentUsernameOrNull())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } else {
            app = guestStore.getById(session, id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        }

        model.addAttribute("app", app);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Applications", "url", "/apps"),
                Map.of("label", "Edit", "url", "")
        ));

        return "apps-edit";
    }

    // =========================================================
    // ACTION: POST /apps/{id}/edit (Update)
    // =========================================================

    @PostMapping("/apps/{id}/edit")
    public String updateApp(
            @PathVariable Long id,
            @Valid @ModelAttribute("app") InternshipApplication form,
            BindingResult bindingResult,
            Model model,
            HttpSession session
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("statuses", ApplicationStatus.values());
            model.addAttribute("breadcrumbs", List.of(
                    Map.of("label", "Applications", "url", "/apps"),
                    Map.of("label", "Edit", "url", "")
            ));
            return "apps-edit";
        }

        if (isLoggedIn()) {
            InternshipApplication existing = service.getByIdForUser(id, currentUsernameOrNull())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

            // Copy editable fields from form -> existing entity
            existing.setCompany(form.getCompany());
            existing.setRole(form.getRole());
            existing.setLocation(form.getLocation());
            existing.setStatus(form.getStatus());
            existing.setAppliedDate(form.getAppliedDate());
            existing.setLink(form.getLink());
            existing.setNotes(form.getNotes());

            service.save(existing);
        } else {
            // Guests: update the session-stored object
            form.setId(id);
            guestStore.save(session, form);
        }

        return "redirect:/apps";
    }

    // =========================================================
    // ACTION: Import guest apps (manual, POST only)
    // =========================================================

    @GetMapping("/apps/import-guest")
    public String importGuestAppsGet() {
        return "redirect:/apps";
    }

    @PostMapping("/apps/import-guest")
    public String importGuestApps(HttpSession session) {
        if (!isLoggedIn()) return "redirect:/apps";

        List<InternshipApplication> guestApps = guestStore.getAll(session);
        if (guestApps.isEmpty()) return "redirect:/apps";

        AppUser user = userService.getCurrentUser();

        // Copy guest apps into DB under this user
        for (InternshipApplication a : guestApps) {
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

        guestStore.clear(session); // prevent duplicate re-import
        return "redirect:/apps";
    }

    // =========================================================
    // PAGE: Selective Import / Discard (logged-in only)
    // =========================================================

    @GetMapping("/apps/import-select")
    public String importSelectPage(Model model, HttpSession session) {
        if (!isLoggedIn()) return "redirect:/login";

        List<InternshipApplication> guestApps = guestStore.getAll(session);

        model.addAttribute("guestApps", guestApps);
        model.addAttribute("guestAppsCount", guestApps.size());
        model.addAttribute("breadcrumbs", List.of(
                Map.of("label", "Applications", "url", "/apps"),
                Map.of("label", "Import Guest Apps", "url", "")
        ));

        return "apps-import-select";
    }

    @PostMapping("/apps/import-selected")
    public String importSelected(@RequestParam(name = "ids", required = false) List<Long> ids,
                                 HttpSession session) {

        if (!isLoggedIn()) return "redirect:/login";
        if (ids == null || ids.isEmpty()) return "redirect:/apps/import-select";

        AppUser user = userService.getCurrentUser();
        List<InternshipApplication> selected = guestStore.getByIds(session, ids);

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

        // Remove imported from guest store so it can't be imported twice
        guestStore.deleteByIds(session, ids);

        return "redirect:/apps";
    }

    @PostMapping("/apps/discard-selected")
    public String discardSelected(@RequestParam(name = "ids", required = false) List<Long> ids,
                                  HttpSession session) {

        if (!isLoggedIn()) return "redirect:/login";
        if (ids == null || ids.isEmpty()) return "redirect:/apps/import-select";

        guestStore.deleteByIds(session, ids);
        return "redirect:/apps";
    }

    // =========================================================
    // ACTION: Discard ALL guest apps (manual)
    // =========================================================

    @PostMapping("/apps/import-dismiss")
    public String discardAllGuestApps(HttpSession session) {
        guestStore.clear(session);
        return "redirect:/apps";
    }

    @GetMapping("/apps/import-dismiss")
    public String discardAllGuestAppsGet() {
        return "redirect:/apps";
    }

    // =========================================================
    // Guest-only filtering helper
    // =========================================================

    private static List<InternshipApplication> sortApps(
            List<InternshipApplication> apps, String sort) {
        Comparator<InternshipApplication> cmp = switch (sort) {
            case "date_asc"     -> Comparator.comparing(
                    InternshipApplication::getAppliedDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "company_asc"  -> Comparator.comparing(
                    a -> a.getCompany() == null ? "" : a.getCompany().toLowerCase());
            case "company_desc" -> Comparator.<InternshipApplication, String>comparing(
                    a -> a.getCompany() == null ? "" : a.getCompany().toLowerCase())
                    .reversed();
            case "status"       -> Comparator.comparing(
                    a -> a.getStatus() == null ? "" : a.getStatus().name());
            default             -> Comparator.comparing(
                    InternshipApplication::getAppliedDate,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        };
        return apps.stream().sorted(cmp).toList();
    }

    /**
     * Guests don't have DB filtering, so we filter session apps in-memory.
     */
    private List<InternshipApplication> filterGuestList(List<InternshipApplication> apps,
                                                        String q,
                                                        ApplicationStatus status) {

        String query = (q == null) ? "" : q.trim().toLowerCase();
        boolean hasQuery = !query.isEmpty();

        return apps.stream()
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> {
                    if (!hasQuery) return true;

                    String company = (a.getCompany() == null) ? "" : a.getCompany().toLowerCase();
                    String role = (a.getRole() == null) ? "" : a.getRole().toLowerCase();

                    return company.contains(query) || role.contains(query);
                })
                .toList();
    }
}