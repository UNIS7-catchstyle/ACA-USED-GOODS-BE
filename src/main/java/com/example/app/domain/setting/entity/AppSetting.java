package com.example.app.domain.setting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "app_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppSetting {

	@Id
	@Column(name = "setting_key", length = 50)
	private String settingKey;

	@Column(name = "setting_value", nullable = false, length = 100)
	private String settingValue;

	@Builder
	private AppSetting(String settingKey, String settingValue) {
		this.settingKey = settingKey;
		this.settingValue = settingValue;
	}
}
