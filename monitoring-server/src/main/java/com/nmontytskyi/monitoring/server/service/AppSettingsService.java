package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.server.entity.AppSettingsEntity;
import com.nmontytskyi.monitoring.server.repository.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppSettingsService {

    private final AppSettingsRepository repository;

    public String get(String key) {
        return repository.findById(key).map(AppSettingsEntity::getValue).orElse(null);
    }

    public String get(String key, String defaultValue) {
        return repository.findById(key).map(AppSettingsEntity::getValue).orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value) {
        AppSettingsEntity entity = repository.findById(key)
                .orElse(new AppSettingsEntity(key, null));
        entity.setValue(value);
        repository.save(entity);
    }

    public Map<String, String> getAll() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(AppSettingsEntity::getKey, AppSettingsEntity::getValue));
    }
}
