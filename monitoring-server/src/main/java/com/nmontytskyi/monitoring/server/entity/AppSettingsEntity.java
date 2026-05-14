package com.nmontytskyi.monitoring.server.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity storing a single application runtime setting as a key-value pair.
 *
 * <p>All configurable runtime parameters (email recipient, retention windows,
 * dashboard refresh interval, etc.) are persisted in the {@code app_settings} table
 * and read at runtime by {@link com.nmontytskyi.monitoring.server.service.AppSettingsService}.
 * This design allows settings to be changed through the web UI without restarting the server.
 *
 * @author Nazar Montytskyi
 * @see com.nmontytskyi.monitoring.server.service.AppSettingsService
 */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppSettingsEntity {

    @Id
    @Column(length = 100)
    private String key;

    @Column(columnDefinition = "TEXT")
    private String value;
}
