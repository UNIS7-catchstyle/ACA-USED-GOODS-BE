package com.example.app.domain.setting.service;

import com.example.app.domain.setting.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppSettingService {

	public static final String MARKET_REGISTRATION_OPEN_KEY = "MARKET_REGISTRATION_OPEN";

	private final AppSettingRepository appSettingRepository;

	@Transactional(readOnly = true)
	public boolean isMarketRegistrationOpen() {
		return appSettingRepository.findById(MARKET_REGISTRATION_OPEN_KEY)
				.map(setting -> Boolean.parseBoolean(setting.getSettingValue()))
				.orElseGet(() -> {
					log.warn("AppSetting '{}' is missing; defaulting to open", MARKET_REGISTRATION_OPEN_KEY);
					return true;
				});
	}
}
