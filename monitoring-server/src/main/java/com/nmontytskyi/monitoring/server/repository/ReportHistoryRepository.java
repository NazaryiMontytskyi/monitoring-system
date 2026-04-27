package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.server.entity.ReportHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportHistoryRepository extends JpaRepository<ReportHistoryEntity, Long> {

    List<ReportHistoryEntity> findAllByServiceIdOrderByGeneratedAtDesc(Long serviceId);
}
