package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.server.entity.AppSettingsEntity;
import com.nmontytskyi.monitoring.server.repository.AppSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppSettingsServiceTest {

    @Mock private AppSettingsRepository repository;

    @InjectMocks private AppSettingsService appSettingsService;

    // ── get(key) ─────────────────────────────────────────────────────────────

    @Test
    void get_existingKey_returnsValue() {
        when(repository.findById("my.key")).thenReturn(Optional.of(new AppSettingsEntity("my.key", "hello")));

        String result = appSettingsService.get("my.key");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void get_missingKey_returnsNull() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        String result = appSettingsService.get("missing");

        assertThat(result).isNull();
    }

    // ── get(key, defaultValue) ───────────────────────────────────────────────

    @Test
    void getWithDefault_existingKey_returnsStoredValue() {
        when(repository.findById("k")).thenReturn(Optional.of(new AppSettingsEntity("k", "stored")));

        String result = appSettingsService.get("k", "default");

        assertThat(result).isEqualTo("stored");
    }

    @Test
    void getWithDefault_missingKey_returnsDefault() {
        when(repository.findById("absent")).thenReturn(Optional.empty());

        String result = appSettingsService.get("absent", "fallback");

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void getWithDefault_nullDefault_missingKey_returnsNull() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        String result = appSettingsService.get("x", null);

        assertThat(result).isNull();
    }

    // ── set ──────────────────────────────────────────────────────────────────

    @Test
    void set_existingKey_updatesValue() {
        AppSettingsEntity existing = new AppSettingsEntity("retention.days", "30");
        when(repository.findById("retention.days")).thenReturn(Optional.of(existing));

        appSettingsService.set("retention.days", "60");

        ArgumentCaptor<AppSettingsEntity> captor = ArgumentCaptor.forClass(AppSettingsEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getValue()).isEqualTo("60");
        assertThat(captor.getValue().getKey()).isEqualTo("retention.days");
    }

    @Test
    void set_newKey_createsNewEntity() {
        when(repository.findById("new.key")).thenReturn(Optional.empty());

        appSettingsService.set("new.key", "value");

        ArgumentCaptor<AppSettingsEntity> captor = ArgumentCaptor.forClass(AppSettingsEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo("new.key");
        assertThat(captor.getValue().getValue()).isEqualTo("value");
    }

    @Test
    void set_nullValue_persistsNull() {
        when(repository.findById("k")).thenReturn(Optional.empty());

        appSettingsService.set("k", null);

        ArgumentCaptor<AppSettingsEntity> captor = ArgumentCaptor.forClass(AppSettingsEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getValue()).isNull();
    }

    // ── getAll ───────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsAllAsMap() {
        when(repository.findAll()).thenReturn(List.of(
                new AppSettingsEntity("a", "1"),
                new AppSettingsEntity("b", "2")
        ));

        Map<String, String> result = appSettingsService.getAll();

        assertThat(result).containsEntry("a", "1").containsEntry("b", "2").hasSize(2);
    }

    @Test
    void getAll_empty_returnsEmptyMap() {
        when(repository.findAll()).thenReturn(List.of());

        Map<String, String> result = appSettingsService.getAll();

        assertThat(result).isEmpty();
    }
}
