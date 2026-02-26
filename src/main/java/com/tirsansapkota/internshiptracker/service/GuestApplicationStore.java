package com.tirsansapkota.internshiptracker.service;

import com.tirsansapkota.internshiptracker.model.InternshipApplication;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GuestApplicationStore {
    private static final String KEY = "GUEST_APPS";
    private static final String ID_SEQ = "GUEST_ID_SEQ";

    @SuppressWarnings("unchecked")
    private List<InternshipApplication> list(HttpSession session) {
        Object val = session.getAttribute(KEY);
        if (val == null) {
            List<InternshipApplication> apps = new ArrayList<>();
            session.setAttribute(KEY, apps);
            session.setAttribute(ID_SEQ, 1L);
            return apps;
        }
        return (List<InternshipApplication>) val;
    }

    private long nextId(HttpSession session) {
        Long seq = (Long) session.getAttribute(ID_SEQ);
        if (seq == null) seq = 1L;
        session.setAttribute(ID_SEQ, seq + 1);
        return seq;
    }

    public List<InternshipApplication> getAll(HttpSession session) {
        return new ArrayList<>(list(session));
    }

    public void save(HttpSession session, InternshipApplication app) {
        List<InternshipApplication> apps = list(session);


        if (app.getId() == null) {
            app.setId(nextId(session)); // temporary session-only id
            apps.add(app);
            return;
        }

        // update existing
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).getId().equals(app.getId())) {
                apps.set(i, app);
                return;
            }
        }
        apps.add(app);
    }


    public Optional<InternshipApplication> getById(HttpSession session, Long id) {
        if (id == null) return Optional.empty();
        return list(session).stream().filter(a -> id.equals(a.getId())).findFirst();
    }

    public void deleteById(HttpSession session, Long id) {
        if (id == null) return;
        list(session).removeIf(a -> id.equals(a.getId()));
    }

    public void clear(HttpSession session) {
        session.removeAttribute(KEY);
        session.removeAttribute(ID_SEQ);
    }

    // =========================================================
    // Batch helpers for Selective Import/Discard
    // =========================================================
    public List<InternshipApplication> getByIds(HttpSession session, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return list(session).stream()
                .filter(a -> a.getId() != null && ids.contains(a.getId()))
                .toList();
    }

    public void deleteByIds(HttpSession session, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        list(session).removeIf(a -> a.getId() != null && ids.contains(a.getId()));
    }

}
