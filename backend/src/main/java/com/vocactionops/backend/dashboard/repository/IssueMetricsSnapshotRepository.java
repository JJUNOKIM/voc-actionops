package com.vocactionops.backend.dashboard.repository;

import com.vocactionops.backend.dashboard.domain.IssueMetricsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface IssueMetricsSnapshotRepository
		extends JpaRepository<IssueMetricsSnapshot, Long> {

	@Query("""
			SELECT snapshot
			FROM IssueMetricsSnapshot snapshot
			WHERE snapshot.snapshotDate = :snapshotDate
			  AND snapshot.issue.organization.id = :organizationId
			""")
	List<IssueMetricsSnapshot> findAllByOrganizationAndDate(
			@Param("organizationId") Long organizationId,
			@Param("snapshotDate") LocalDate snapshotDate
	);

	@Query("""
			SELECT snapshot
			FROM IssueMetricsSnapshot snapshot
			WHERE snapshot.issue.id = :issueId
			  AND snapshot.issue.organization.id = :organizationId
			  AND snapshot.snapshotDate BETWEEN :from AND :to
			ORDER BY snapshot.snapshotDate, snapshot.id
			""")
	List<IssueMetricsSnapshot> findTrend(
			@Param("organizationId") Long organizationId,
			@Param("issueId") Long issueId,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to
	);
}
