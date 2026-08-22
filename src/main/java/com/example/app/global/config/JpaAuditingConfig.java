package com.example.app.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// Auditing needs a real EntityManagerFactory; skipped in "test" so the context
// can load without a database (see src/test/resources/application-test.yml).
@Configuration
@Profile("!test")
@EnableJpaAuditing
public class JpaAuditingConfig {
}
