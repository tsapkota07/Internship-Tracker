package com.tirsansapkota.internshiptracker;

import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the in-memory rate limiter (RateLimitInterceptor, now a Servlet Filter).
 * Running as a Filter means it fires before Spring Security, so POST /login is
 * correctly rate-limited even though Spring Security handles that endpoint.
 */
class RateLimitTest extends BaseIntegrationTest {

    private static final int LOGIN_MAX  = 10;
    private static final int FORGOT_MAX = 5;

    @Test
    void loginRateLimit_blocksAfterMaxAttempts() throws Exception {
        String ip = "10.3.0.1";

        for (int i = 0; i < LOGIN_MAX; i++) {
            mockMvc.perform(post("/login").with(csrf())
                            .header("X-Forwarded-For", ip)
                            .param("username", "nobody")
                            .param("password", "wrong"))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(post("/login").with(csrf())
                        .header("X-Forwarded-For", ip)
                        .param("username", "nobody")
                        .param("password", "wrong"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void forgotPasswordRateLimit_blocksAfterMaxAttempts() throws Exception {
        String ip = "10.3.0.2";

        for (int i = 0; i < FORGOT_MAX; i++) {
            mockMvc.perform(post("/forgot-password").with(csrf())
                            .header("X-Forwarded-For", ip)
                            .param("email", "nobody@example.com"))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(post("/forgot-password").with(csrf())
                        .header("X-Forwarded-For", ip)
                        .param("email", "nobody@example.com"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void rateLimitIsPerIp_differentIpsAreIndependent() throws Exception {
        // Exhaust limit for IP A
        for (int i = 0; i <= LOGIN_MAX; i++) {
            mockMvc.perform(post("/login").with(csrf())
                    .header("X-Forwarded-For", "10.3.1.1")
                    .param("username", "nobody").param("password", "wrong"));
        }

        // IP B is unaffected
        mockMvc.perform(post("/login").with(csrf())
                        .header("X-Forwarded-For", "10.3.1.2")
                        .param("username", "nobody")
                        .param("password", "wrong"))
                .andExpect(status().is3xxRedirection());
    }
}
