package com.vocactionops.backend.action.application;

import com.vocactionops.backend.action.domain.Action;
import com.vocactionops.backend.action.domain.ActionStatus;
import com.vocactionops.backend.action.repository.ActionRepository;
import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.issue.domain.Issue;
import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.repository.IssueRepository;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class ActionService {

	private final ActionRepository actionRepository;
	private final IssueRepository issueRepository;
	private final UserRepository userRepository;

	public ActionService(
			ActionRepository actionRepository,
			IssueRepository issueRepository,
			UserRepository userRepository
	) {
		this.actionRepository = actionRepository;
		this.issueRepository = issueRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	@PreAuthorize("hasAnyRole('ADMIN', 'PM')")
	public ActionView createAction(
			AuthenticatedUser authenticatedUser,
			Long issueId,
			String title,
			String description,
			Long assigneeId,
			LocalDate dueDate
	) {
		Issue issue = issueRepository.findByIdAndOrganizationId(
					issueId,
					authenticatedUser.organizationId()
			)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		if (issue.getStatus() == IssueStatus.CLOSED) {
			throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
		}
		User assignee = assigneeId == null ? null : userRepository.findByIdAndOrganizationId(
					assigneeId,
					authenticatedUser.organizationId()
			)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		try {
			return ActionView.from(actionRepository.save(new Action(
					issue,
					title,
					description,
					assignee,
					dueDate
			)));
		} catch (IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	@Transactional
	@PreAuthorize("hasAnyRole('ADMIN', 'PM', 'DEVELOPER')")
	public ActionView changeStatus(
			AuthenticatedUser authenticatedUser,
			Long actionId,
			ActionStatus status
	) {
		Action action = actionRepository.findByIdAndIssueOrganizationId(
					actionId,
					authenticatedUser.organizationId()
			)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		if (!canChangeStatus(authenticatedUser, action)) {
			throw new CustomException(ErrorCode.FORBIDDEN);
		}
		try {
			action.changeStatus(status);
			return ActionView.from(action);
		} catch (IllegalStateException exception) {
			throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
		} catch (IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	private boolean canChangeStatus(AuthenticatedUser authenticatedUser, Action action) {
		if (authenticatedUser.role() == Role.ADMIN || authenticatedUser.role() == Role.PM) {
			return true;
		}
		User assignee = action.getAssignee();
		return assignee != null && assignee.getId().equals(authenticatedUser.userId());
	}
}
