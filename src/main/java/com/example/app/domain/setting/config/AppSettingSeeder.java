package com.example.app.domain.setting.config;

import com.example.app.domain.setting.entity.AppSetting;
import com.example.app.domain.setting.repository.AppSettingRepository;
import com.example.app.domain.setting.service.AppSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Runs on every context startup, including tests (no profile restriction) — keeps
// AppSettingService.isMarketRegistrationOpen() from ever hitting its "row missing"
// fallback in normal operation.
@Slf4j
@Component
@RequiredArgsConstructor
public class AppSettingSeeder implements ApplicationRunner {

	private final AppSettingRepository appSettingRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (appSettingRepository.existsById(AppSettingService.MARKET_REGISTRATION_OPEN_KEY)) {
			return;
		}
		appSettingRepository.save(AppSetting.builder()
				.settingKey(AppSettingService.MARKET_REGISTRATION_OPEN_KEY)
				.settingValue("true")
				.build());
		log.info("Seeded AppSetting '{}' = true", AppSettingService.MARKET_REGISTRATION_OPEN_KEY);
	}
}
