package com.nmontytskyi.monitoring.server.mapper;

import com.nmontytskyi.monitoring.model.MetricSnapshot;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity.MetricSource;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import lombok.experimental.UtilityClass;

/**
 * Stateless mapper between the {@link MetricRecordEntity} JPA entity
 * and the {@link MetricSnapshot} core domain model.
 *
 * <p>The {@link MetricSource} is preserved through conversion so that
 * business logic can distinguish between Actuator-pulled data and
 * starter-pushed data when computing aggregates.
 */
@UtilityClass
public class MetricSnapshotMapper {

    /**
     * Converts a {@link MetricRecordEntity} (loaded from the database)
     * into a {@link MetricSnapshot} core domain object.
     *
     * @param entity the JPA entity to convert; must not be {@code null}
     * @return the corresponding domain model
     */
    public static MetricSnapshot toModel(MetricRecordEntity entity) {
        return MetricSnapshot.builder()
                .serviceId(String.valueOf(entity.getService().getId()))
                .endpoint(entity.getEndpoint())
                .responseTimeMs(entity.getResponseTimeMs())
                .status(entity.getStatus())
                .cpuUsage(entity.getCpuUsage() != null ? entity.getCpuUsage() : 0.0)
                .heapUsedMb(entity.getHeapUsedMb() != null ? entity.getHeapUsedMb() : 0L)
                .heapMaxMb(entity.getHeapMaxMb() != null ? entity.getHeapMaxMb() : 0L)
                .errorMessage(entity.getErrorMessage())
                .recordedAt(entity.getRecordedAt())
                .anomaly(entity.isAnomaly())
                .zScore(entity.getZScore())
                .build();
    }

    /**
     * Converts a {@link MetricSnapshot} core domain object into a
     * {@link MetricRecordEntity} ready to be persisted.
     *
     * <p>The caller must set the {@link RegisteredServiceEntity} association
     * on the returned entity before saving, as the snapshot only carries
     * a string service ID (not the full entity reference).
     *
     * @param snapshot the domain model to convert; must not be {@code null}
     * @param source   the origin of this snapshot (PULL or PUSH)
     * @param service  the owning service entity
     * @return the JPA entity without a persisted ID
     */
    public static MetricRecordEntity toEntity(
            MetricSnapshot snapshot,
            MetricSource source,
            RegisteredServiceEntity service
    ) {
        return MetricRecordEntity.builder()
                .service(service)
                .endpoint(snapshot.getEndpoint())
                .responseTimeMs(snapshot.getResponseTimeMs())
                .status(snapshot.getStatus())
                .cpuUsage(snapshot.getCpuUsage() != 0.0 ? snapshot.getCpuUsage() : null)
                .heapUsedMb(snapshot.getHeapUsedMb() != 0L ? snapshot.getHeapUsedMb() : null)
                .heapMaxMb(snapshot.getHeapMaxMb() != 0L ? snapshot.getHeapMaxMb() : null)
                .errorMessage(snapshot.getErrorMessage())
                .recordedAt(snapshot.getRecordedAt())
                .anomaly(snapshot.isAnomaly())
                .zScore(snapshot.getZScore())
                .source(source)
                .build();
    }
}
