package com.vocactionops.backend.dataset.storage;

import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dataset.config.DatasetUploadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Component
public class LocalDatasetFileStorage {

	private static final Logger log = LoggerFactory.getLogger(LocalDatasetFileStorage.class);

	private final Path rootDirectory;

	public LocalDatasetFileStorage(DatasetUploadProperties properties) {
		this.rootDirectory = properties.storageDirectory().toAbsolutePath().normalize();
	}

	public StoredFile store(Long organizationId, byte[] content) {
		Path organizationDirectory = rootDirectory.resolve(organizationId.toString()).normalize();
		if (!organizationDirectory.startsWith(rootDirectory)) {
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
		}

		String storedName = UUID.randomUUID() + ".csv";
		Path target = organizationDirectory.resolve(storedName).normalize();
		try {
			Files.createDirectories(organizationDirectory);
			Files.write(target, content, StandardOpenOption.CREATE_NEW);
			return new StoredFile(
					"local://dataset-files/" + organizationId + "/" + storedName,
					target
			);
		} catch (IOException exception) {
			deletePathQuietly(target);
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
	}

	public void deleteQuietly(StoredFile storedFile) {
		deletePathQuietly(storedFile.path());
	}

	private void deletePathQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException exception) {
			log.warn("Failed to delete dataset file: {}", path);
		}
	}

	public record StoredFile(String url, Path path) {
	}
}
