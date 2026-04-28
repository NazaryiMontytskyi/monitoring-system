package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.server.entity.AppSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingsRepository extends JpaRepository<AppSettingsEntity, String> {}
