package com.vocactionops.backend.dashboard.application;

import com.vocactionops.backend.organization.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class IssueMetricsSnapshotScheduler {

	private static final Logger log = LoggerFactory.getLogger(IssueMetricsSnapshotScheduler.class);

	private final IssueMetricsSnapshotService snapshotService;
	private final OrganizationRepository organizationRepository;

	public IssueMetricsSnapshotScheduler(
			IssueMetricsSnapshotService snapshotService,
			OrganizationRepository organizationRepository
	) {
		this.snapshotService = snapshotService;
		this.organizationRepository = organizationRepository;
	}

	@Scheduled(
			cron = "${app.dashboard.snapshot-cron:0 55 23 * * *}",
			zone = "${app.dashboard.snapshot-zone:Asia/Seoul}"
	)
	public void captureDailySnapshots() {
		LocalDate snapshotDate = snapshotService.currentDate();
		for (Long organizationId : organizationRepository.findAllIds()) {
			try {
				snapshotService.captureOrganization(organizationId, snapshotDate);
			} catch (RuntimeException exception) {
				log.error(
						"Failed to capture issue metrics snapshot. organizationId={}, snapshotDate={}",
						organizationId,
						snapshotDate,
						exception
				);
			}
		}
	}
}
