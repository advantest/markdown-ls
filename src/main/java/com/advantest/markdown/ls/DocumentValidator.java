/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.advantest.markdown.service.MarkdownService;
import com.advantest.markdown.service.validation.ValidationIssue;
import com.advantest.resources.Resource;
import com.advantest.resources.UnresolvedResource;

/**
 * Validates the open documents and publishes what it finds.
 *
 * <p>A document is validated on a thread of its own, so that the server goes on reading the
 * messages of its client meanwhile. Each validation of a document replaces the one before: a
 * validation that has not started yet does not start, one that is running is cancelled, and what
 * it found is never published. A document that was edited is validated only once the edits have
 * paused for a moment, so that typing does not start a validation for every key.</p>
 */
final class DocumentValidator implements AutoCloseable {

	/**
	 * How long a document must stay unchanged after an edit before it is validated.
	 */
	static final Duration DELAY_AFTER_CHANGE = Duration.ofMillis(300);

	private static final Logger LOG = LoggerFactory.getLogger(DocumentValidator.class);

	private final MarkdownService service;
	private final Consumer<PublishDiagnosticsParams> publisher;
	private final Duration delayAfterChange;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
			task -> {
				Thread thread = new Thread(task, "markdown-ls validation");
				thread.setDaemon(true);
				return thread;
			});
	// guarded by this
	private final Map<String, Validation> validations = new HashMap<>();
	// guarded by this
	private boolean closed;

	/**
	 * @param service the service validating the documents, not <code>null</code>
	 * @param publisher hands the diagnostics of a document to the client, not <code>null</code>
	 * @param delayAfterChange how long a document must stay unchanged after an edit before it is
	 *                         validated, not <code>null</code>
	 */
	DocumentValidator(MarkdownService service, Consumer<PublishDiagnosticsParams> publisher,
			Duration delayAfterChange) {
		this.service = Objects.requireNonNull(service);
		this.publisher = Objects.requireNonNull(publisher);
		this.delayAfterChange = Objects.requireNonNull(delayAfterChange);
	}

	/**
	 * Validates the given text of a document as soon as possible.
	 *
	 * @param uri the document's URI, not <code>null</code>
	 * @param resource the file the document is, <code>null</code> if it is none
	 * @param text the text to be validated, not <code>null</code>
	 * @param version the version of the text, <code>null</code> if it is unknown
	 */
	void validateNow(String uri, Resource resource, String text, Integer version) {
		schedule(new Validation(uri, resource, text, version), Duration.ZERO);
	}

	/**
	 * Validates the given text of a document unless another one arrives before the delay after a
	 * change has passed.
	 *
	 * @see #validateNow(String, Resource, String, Integer)
	 */
	void validateAfterChange(String uri, Resource resource, String text, Integer version) {
		schedule(new Validation(uri, resource, text, version), this.delayAfterChange);
	}

	/**
	 * Gives up validating the given document and removes its diagnostics from the client.
	 *
	 * @param uri the document's URI, not <code>null</code>
	 */
	synchronized void forget(String uri) {
		if (this.closed) {
			return;
		}
		Validation previous = this.validations.remove(uri);
		if (previous != null) {
			previous.cancel();
		}
		this.publisher.accept(new PublishDiagnosticsParams(uri, List.of()));
	}

	/**
	 * Gives up every validation; nothing is validated or published afterwards.
	 */
	@Override
	public void close() {
		synchronized (this) {
			this.closed = true;
			this.validations.values().forEach(Validation::cancel);
			this.validations.clear();
		}
		this.executor.shutdownNow();
	}

	private synchronized void schedule(Validation validation, Duration delay) {
		if (this.closed) {
			return;
		}
		Validation previous = this.validations.put(validation.uri, validation);
		if (previous != null) {
			previous.cancel();
		}
		validation.scheduled(
				this.executor.schedule(() -> run(validation), delay.toMillis(), TimeUnit.MILLISECONDS));
	}

	private void run(Validation validation) {
		Resource document = validation.resource != null
				? validation.resource
				: UnresolvedResource.UNKNOWN_DOCUMENT;
		CompletableFuture<List<ValidationIssue>> promise;
		try {
			promise = this.service.validateMarkdownAsync(validation.text, document);
		} catch (IllegalStateException serviceClosed) {
			LOG.debug("The service was closed before {} was validated.", validation.uri);
			return;
		}
		if (validation.started(promise)) {
			promise.whenComplete((issues, failure) -> publish(validation, issues, failure));
		}
	}

	private synchronized void publish(Validation validation, List<ValidationIssue> issues, Throwable failure) {
		if (this.validations.get(validation.uri) != validation) {
			// replaced, forgotten or closed meanwhile, so what was found is out of date
			return;
		}
		this.validations.remove(validation.uri);
		if (failure != null) {
			Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
			if (!(cause instanceof CancellationException)) {
				LOG.error("Validating {} failed.", validation.uri, cause);
			}
			return;
		}
		this.publisher.accept(new PublishDiagnosticsParams(validation.uri,
				Diagnostics.of(issues, validation.text), validation.version));
	}

	/**
	 * One validation of one text of a document, from being scheduled until its findings arrive.
	 */
	private static final class Validation {

		final String uri;
		final Resource resource;
		final String text;
		final Integer version;
		// guarded by this
		private Future<?> scheduled;
		// guarded by this
		private CompletableFuture<?> promise;
		// guarded by this
		private boolean cancelled;

		Validation(String uri, Resource resource, String text, Integer version) {
			this.uri = Objects.requireNonNull(uri);
			this.resource = resource;
			this.text = Objects.requireNonNull(text);
			this.version = version;
		}

		synchronized void scheduled(Future<?> future) {
			this.scheduled = future;
		}

		/**
		 * Remembers the promise of the running validation, so that it can be cancelled.
		 *
		 * @return whether the validation is to go on, <code>false</code> if it was cancelled
		 *         meanwhile, in which case the given promise is cancelled as well
		 */
		synchronized boolean started(CompletableFuture<?> runningPromise) {
			if (this.cancelled) {
				runningPromise.cancel(true);
				return false;
			}
			this.promise = runningPromise;
			return true;
		}

		synchronized void cancel() {
			this.cancelled = true;
			if (this.scheduled != null) {
				this.scheduled.cancel(false);
			}
			if (this.promise != null) {
				this.promise.cancel(true);
			}
		}
	}
}
