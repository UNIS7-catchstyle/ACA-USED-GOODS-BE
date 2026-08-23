package com.example.app.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "millisTruncatedDateTimeProvider")
public class JpaAuditingConfig {

	// Truncated to milliseconds so every @CreatedDate matches the precision of the
	// market list's "{epoch millis}_{id}" cursor — without this, two rows audited
	// within the same millisecond but at different sub-millisecond instants could
	// fall on neither side of the cursor's createdAt comparison and get silently
	// skipped by cursor-paginated queries.
	@Bean
	public DateTimeProvider millisTruncatedDateTimeProvider() {
		return () -> Optional.of(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
	}
}
