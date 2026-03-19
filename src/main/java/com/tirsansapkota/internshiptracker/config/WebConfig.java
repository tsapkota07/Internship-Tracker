package com.tirsansapkota.internshiptracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration.
 * RateLimitInterceptor is now a Servlet Filter (auto-registered by Spring Boot)
 * so it no longer needs to be added here as a HandlerInterceptor.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
