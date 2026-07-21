package com.vocactionops.backend.action.domain;

public enum ActionStatus {
	TODO,
	IN_PROGRESS,
	DONE,
	CANCELED;

	public boolean canTransitionTo(ActionStatus target) {
		return switch (this) {
			case TODO -> target == IN_PROGRESS || target == CANCELED;
			case IN_PROGRESS -> target == DONE || target == CANCELED;
			case DONE, CANCELED -> false;
		};
	}
}
