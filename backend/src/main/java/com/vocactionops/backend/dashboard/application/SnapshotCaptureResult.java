package com.vocactionops.backend.dashboard.application;

import java.time.LocalDate;

public record SnapshotCaptureResult(LocalDate snapshotDate, int issueCount) {
}
