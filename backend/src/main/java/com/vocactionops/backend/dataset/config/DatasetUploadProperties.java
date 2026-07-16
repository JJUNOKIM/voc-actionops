package com.vocactionops.backend.dataset.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("app.dataset-upload")
public record DatasetUploadProperties(
		Path storageDirectory,
		long maxFileSizeBytes,
		int maxRecords
) {

	public DatasetUploadProperties {
		if (storageDirectory == null) {
			throw new IllegalArgumentException("app.dataset-upload.storage-directory must not be null");
		}
		if (maxFileSizeBytes < 1) {
			throw new IllegalArgumentException("app.dataset-upload.max-file-size-bytes must be positive");
		}
		if (maxRecords < 1) {
			throw new IllegalArgumentException("app.dataset-upload.max-records must be positive");
		}
	}
}
