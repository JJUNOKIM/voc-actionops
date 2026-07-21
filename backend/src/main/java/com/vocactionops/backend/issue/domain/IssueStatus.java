package com.vocactionops.backend.issue.domain;

public enum IssueStatus {
	NEW,
	TRIAGED,
	ASSIGNED,
	IN_PROGRESS,
	RESOLVED,
	MONITORING,
	CLOSED;

	public boolean canTransitionTo(IssueStatus target) {
		return switch (this) {
			case NEW -> target == TRIAGED;
			case TRIAGED -> target == ASSIGNED;
			case ASSIGNED -> target == IN_PROGRESS;
			case IN_PROGRESS -> target == RESOLVED;
			case RESOLVED -> target == MONITORING;
			case MONITORING -> target == CLOSED || target == IN_PROGRESS;
			case CLOSED -> false;
		};
	}

	public boolean requiresAssignee() {
		return this != NEW && this != TRIAGED;
	}
}
