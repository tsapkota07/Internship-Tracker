package com.tirsansapkota.internshiptracker.config;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.Theme;
import com.tirsansapkota.internshiptracker.model.UserPreferences;
import com.tirsansapkota.internshiptracker.repository.UserPreferencesRepository;
import com.tirsansapkota.internshiptracker.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration
@Profile({"dev", "demo", "local", "prod"})
public class DevDataSeeder {

    @Bean
    public CommandLineRunner seedUsers(SeederRunner runner) {
        return args -> runner.run();
    }

    @org.springframework.stereotype.Component
    static class SeederRunner {
        private final UserRepository users;
        private final UserPreferencesRepository prefsRepo;
        private final PasswordEncoder encoder;

        SeederRunner(UserRepository users, UserPreferencesRepository prefsRepo, PasswordEncoder encoder) {
            this.users = users;
            this.prefsRepo = prefsRepo;
            this.encoder = encoder;
        }

        @org.springframework.transaction.annotation.Transactional
        public void run() {
            String pw = System.getenv().getOrDefault("DEMO_PASSWORD", "CHANGE_ME");

            if (!"true".equalsIgnoreCase(System.getenv().getOrDefault("DEMO_SEED_ENABLED", "false"))) return;

            seedIfMissing("user1", null, pw, false, true);
            seedIfMissing("user2", null, pw, false, true);
            seedIfMissing("verifiedUser1", "verified1@testmail.com", pw, true, true);
            seedIfMissing("verifiedUser2", "verified2@testmail.com", pw, true, true);
        }

        private void seedIfMissing(String username, String email, String rawPassword, boolean verified, boolean demoUser) {
            if (users.existsByUsername(username)) return;

            AppUser u = new AppUser();
            u.setUsername(username);
            u.setEmail(email == null ? null : email.trim().toLowerCase());
            u.setPasswordHash(encoder.encode(rawPassword));
            u.setEmailVerified(verified);
            u.setPendingEmail(null);
            u.setRole("USER");
            u.setDemoUser(demoUser);

            u = users.save(u); // managed in same Tx now ✅

            UserPreferences up = new UserPreferences();
            up.setUser(u);           // managed user ✅
            up.setTheme(Theme.LIGHT);
            prefsRepo.save(up);
        }
    }
}