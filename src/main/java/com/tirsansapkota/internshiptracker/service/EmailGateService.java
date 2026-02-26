package com.tirsansapkota.internshiptracker.service;

import com.tirsansapkota.internshiptracker.model.AppUser;
import org.springframework.stereotype.Service;

@Service
public class EmailGateService {
    public boolean needsEmail(AppUser user) {
        return user.getEmail() == null || user.getEmail().isBlank();
    }

    public boolean needsVerification(AppUser user) {
        return !needsEmail(user) && !user.isEmailVerified();
    }
}