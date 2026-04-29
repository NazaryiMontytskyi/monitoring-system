package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.server.entity.ReportHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReportHistoryRepository extends JpaRepository<ReportHistoryEntity, Long> {

    List<ReportHistoryEntity> findAllByServiceIdOrderByGeneratedAtDesc(Long serviceId);

    @Modifying
    @Query("DELETE FROM ReportHistoryEntity r WHERE r.generatedAt < :threshold")
    int deleteByGeneratedAtBefore(@Param("threshold") LocalDateTime threshold);
}
