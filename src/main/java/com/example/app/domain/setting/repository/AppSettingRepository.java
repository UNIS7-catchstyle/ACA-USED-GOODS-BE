package com.example.app.domain.setting.repository;

import com.example.app.domain.setting.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
