package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.server.entity.AppSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link com.nmontytskyi.monitoring.server.entity.AppSettingsEntity}.
 *
 * <p>Provides key-based lookup for individual settings and bulk retrieval used by
 * {@link com.nmontytskyi.monitoring.server.service.AppSettingsService} to populate
 * the settings page and supply runtime configuration to other services.
 *
 * @author Nazar Montytskyi
 */
public interface AppSettingsRepository extends JpaRepository<AppSettingsEntity, String> {}
