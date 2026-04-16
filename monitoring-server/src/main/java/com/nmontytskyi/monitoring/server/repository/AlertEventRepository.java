package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.server.entity.AlertEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AlertEventEntity}.
 *
 * <p>Supports paginated queries for the event log UI (FR-3/FR-4) and the
 * cooldown check used by {@code AlertCooldownManager} to prevent alert storms.
 */
@Repository
public interface AlertEventRepository extends JpaRepository<AlertEventEntity, Long> {

    /**
     * Returns a paginated list of all alert events for a service, newest first.
     * Used by the event log page and {@code GET /api/alerts/events?serviceId=}.
     *
     * @param serviceId the service identifier
     * @param pageable  pagination and sorting parameters
     * @return page of alert events
     */
    Page<AlertEventEntity> findAllByServiceIdOrderByFiredAtDesc(Long serviceId, Pageable pageable);

    /**
     * Returns a paginated list of all alert events within a time range, newest first.
     * Used by the REST API to support time-filtered queries.
     *
     * @param from     start of the time window (inclusive)
     * @param to       end of the time window (inclusive)
     * @param pageable pagination and sorting parameters
     * @return page of alert events
     */
    Page<AlertEventEntity> findAllByFiredAtBetweenOrderByFiredAtDesc(
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );

    /**
     * Finds the most recent event fired for a specific alert rule.
     * Used by {@code AlertCooldownManager} to determine whether the cooldown
     * period has elapsed before firing another notification.
     *
     * @param ruleId the alert rule identifier
     * @return the most recent event for this rule, if any
     */
    Optional<AlertEventEntity> findTopByRuleIdOrderByFiredAtDesc(Long ruleId);
}
