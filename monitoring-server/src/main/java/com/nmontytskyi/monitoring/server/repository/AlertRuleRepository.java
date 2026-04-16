package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link AlertRuleEntity}.
 *
 * <p>Used by the alert evaluation engine (FR-4) to load the active rules
 * for a service and by the REST API to expose the rule configuration.
 */
@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, Long> {

    /**
     * Returns all alert rules for a specific service.
     * Used by the alert rules management page.
     *
     * @param serviceId the service identifier
     * @return all rules for the service (enabled and disabled)
     */
    List<AlertRuleEntity> findAllByServiceId(Long serviceId);

    /**
     * Returns only the enabled alert rules for a service.
     * Used by {@code AlertEvaluationService} after every metric record is saved.
     *
     * @param serviceId the service identifier
     * @return enabled rules to evaluate
     */
    List<AlertRuleEntity> findAllByServiceIdAndEnabledTrue(Long serviceId);
}
