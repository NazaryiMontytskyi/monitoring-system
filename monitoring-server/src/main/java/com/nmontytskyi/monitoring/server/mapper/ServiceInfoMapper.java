package com.nmontytskyi.monitoring.server.mapper;

import com.nmontytskyi.monitoring.model.ServiceInfo;
import com.nmontytskyi.monitoring.model.SlaDefinition;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.entity.SlaDefinitionEntity;
import lombok.experimental.UtilityClass;

/**
 * Stateless mapper between the {@link RegisteredServiceEntity} JPA entity
 * and the {@link ServiceInfo} core domain model.
 *
 * <p>Keeps the persistence layer decoupled from the domain layer.
 * The business logic in services and schedulers works exclusively with
 * core models; this mapper performs the translation at the boundary.
 */
@UtilityClass
public class ServiceInfoMapper {

    /**
     * Converts a {@link RegisteredServiceEntity} (loaded from the database)
     * into a {@link ServiceInfo} core domain object.
     *
     * @param entity the JPA entity to convert; must not be {@code null}
     * @return the corresponding domain model
     */
    public static ServiceInfo toModel(RegisteredServiceEntity entity) {
        SlaDefinition sla = entity.getSlaDefinition() != null
                ? slaToModel(entity.getSlaDefinition())
                : SlaDefinition.defaults();

        return ServiceInfo.builder()
                .id(String.valueOf(entity.getId()))
                .name(entity.getName())
                .host(entity.getHost())
                .port(entity.getPort())
                .actuatorUrl(entity.getActuatorUrl())
                .baseUrl(entity.getBaseUrl())
                .sla(sla)
                .registeredAt(entity.getRegisteredAt())
                .build();
    }

    /**
     * Converts a {@link ServiceInfo} core domain object into a new
     * {@link RegisteredServiceEntity} ready to be persisted.
     *
     * <p>The returned entity has no {@code id} set (it will be assigned by the DB),
     * and the {@link SlaDefinitionEntity} is pre-linked to the service.
     *
     * @param model the domain model to convert; must not be {@code null}
     * @return the JPA entity without a persisted ID
     */
    public static RegisteredServiceEntity toEntity(ServiceInfo model) {
        RegisteredServiceEntity entity = RegisteredServiceEntity.builder()
                .name(model.getName())
                .host(model.getHost())
                .port(model.getPort())
                .actuatorUrl(model.getActuatorUrl())
                .baseUrl(model.getBaseUrl())
                .build();

        SlaDefinition sla = model.getSla() != null ? model.getSla() : SlaDefinition.defaults();
        SlaDefinitionEntity slaEntity = slaToEntity(sla);
        slaEntity.setService(entity);
        entity.setSlaDefinition(slaEntity);

        return entity;
    }

    // ── SLA helpers ──────────────────────────────────────────────────────────

    private static SlaDefinition slaToModel(SlaDefinitionEntity entity) {
        return SlaDefinition.builder()
                .uptimePercent(entity.getUptimePercent())
                .maxResponseTimeMs(entity.getMaxResponseTimeMs())
                .maxErrorRatePercent(entity.getMaxErrorRatePercent())
                .description(entity.getDescription())
                .build();
    }

    private static SlaDefinitionEntity slaToEntity(SlaDefinition model) {
        return SlaDefinitionEntity.builder()
                .uptimePercent(model.getUptimePercent())
                .maxResponseTimeMs(model.getMaxResponseTimeMs())
                .maxErrorRatePercent(model.getMaxErrorRatePercent())
                .description(model.getDescription())
                .build();
    }
}
