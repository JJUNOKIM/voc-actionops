package com.vocactionops.backend.common.web;

import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequestFactory {

	private static final int MAX_PAGE_SIZE = 100;

	private PageRequestFactory() {
	}

	public static Pageable newestFirst(int page, int size, String sortProperty) {
		Sort sort = Sort.by(Sort.Direction.DESC, sortProperty)
				.and(Sort.by(Sort.Direction.DESC, "id"));
		return ordered(page, size, sort);
	}

	public static Pageable ordered(int page, int size, Sort sort) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return PageRequest.of(page, size, sort);
	}
}
