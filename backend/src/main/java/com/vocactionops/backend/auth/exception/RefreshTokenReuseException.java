package com.vocactionops.backend.auth.exception;

import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;

public class RefreshTokenReuseException extends CustomException {

	public RefreshTokenReuseException() {
		super(ErrorCode.INVALID_REFRESH_TOKEN);
	}
}
