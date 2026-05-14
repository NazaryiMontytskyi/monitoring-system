package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link com.nmontytskyi.monitoring.server.entity.AlertRuleEntity}.
 *
 * <p>Provides finder methods for retrieving enabled rules by service ID, used by
 * {@link com.nmontytskyi.monitoring.server.alert.AlertEvaluationService} during each
 * alert evaluation cycle.
 *
 * @author Nazar Montytskyi
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
