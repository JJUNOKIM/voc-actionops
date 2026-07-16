package com.vocactionops.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static com.vocactionops.backend.common.web.PageRequestFactory.newestFirst;
import static org.assertj.core.api.Assertions.assertThat;

class PageRequestFactoryTests {

	@Test
	void usesIdAsTieBreakerForStablePagination() {
		Pageable pageable = newestFirst(1, 20, "createdAt");

		assertThat(pageable.getPageNumber()).isEqualTo(1);
		assertThat(pageable.getPageSize()).isEqualTo(20);
		assertThat(pageable.getSort().stream().map(Sort.Order::getProperty))
				.containsExactlyElementsOf(List.of("createdAt", "id"));
		assertThat(pageable.getSort()).allMatch(Sort.Order::isDescending);
	}
}
