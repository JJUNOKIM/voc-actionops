package com.vocactionops.backend.action.repository;

import com.vocactionops.backend.action.domain.Action;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActionRepository extends JpaRepository<Action, Long> {

	@EntityGraph(attributePaths = "assignee")
	List<Action> findAllByIssueIdAndIssueOrganizationIdOrderByCreatedAtDesc(
			Long issueId,
			Long organizationId
	);

	@EntityGraph(attributePaths = {"issue", "assignee"})
	Optional<Action> findByIdAndIssueOrganizationId(Long id, Long organizationId);
}
