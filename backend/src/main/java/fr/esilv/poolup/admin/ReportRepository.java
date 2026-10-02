package fr.esilv.poolup.admin;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** Admin list, newest first, reporter loaded in the same query. */
    @EntityGraph(attributePaths = "reporter")
    List<Report> findByStatusOrderByCreatedAtDescIdDesc(ReportStatus status);

    @EntityGraph(attributePaths = "reporter")
    List<Report> findAllByOrderByCreatedAtDescIdDesc();

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(Long reporterId, ReportTargetType targetType,
            Long targetId, ReportStatus status);
}
