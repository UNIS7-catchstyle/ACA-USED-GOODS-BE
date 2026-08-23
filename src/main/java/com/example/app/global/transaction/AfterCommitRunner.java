package com.example.app.global.transaction;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Runs `action` once the current transaction commits, e.g. deleting now-orphaned
// files from ImageStorage only after the row referencing them is durably gone —
// never before, or a failure between the two could leave a dangling reference.
public final class AfterCommitRunner {

	private AfterCommitRunner() {
	}

	public static void run(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}
}
