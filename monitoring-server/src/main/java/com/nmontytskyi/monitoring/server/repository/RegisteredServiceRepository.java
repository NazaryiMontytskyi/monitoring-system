package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link RegisteredServiceEntity}.
 *
 * <p>Used by the registration endpoint (FR-2) and the pull scheduler (FR-1)
 * to look up services that need to be contacted for health checks.
 */
@Repository
public interface RegisteredServiceRepository extends JpaRepository<RegisteredServiceEntity, Long> {

    /**
     * Finds a service by its unique logical name.
     * Used during registration to detect duplicates.
     *
     * @param name the service name from {@code @MonitoredMicroservice(name = "...")}
     * @return the entity if it exists
     */
    Optional<RegisteredServiceEntity> findByName(String name);

    /**
     * Returns all services that currently have the given health status.
     * Used by the dashboard aggregate query (FR-3) and alert evaluation (FR-4).
     *
     * @param status the target health status
     * @return list of matching services
     */
    List<RegisteredServiceEntity> findAllByStatus(HealthStatus status);

    /**
     * Checks whether a service with this name is already registered.
     *
     * @param name the service name to check
     * @return {@code true} if a service with this name exists
     */
    boolean existsByName(String name);
}
