/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.channel.maven;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.jboss.logging.Logger;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.ArtifactTransferException;
import org.wildfly.channel.Repository;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Retries resolving artifacts. If the set of failing artifacts changes (i.e. some artifacts were resolved), the retry counter
 * is reset. Optionally uses a timeout between attempts.
 */
class RetryHandler {
    private static final Logger LOG = Logger.getLogger(RetryHandler.class);

    private final int maxRetries;
    private final long timeout;
    private final TimeUnit timeUnit;

    /**
     *
     * @param maxRetries - maximum times the resolution will be attempted per failing artifact set.
     * @param timeout - timeout between attempts
     * @param timeUnit - {@code TimeUnit} of the attempt timeout
     */
    RetryHandler(int maxRetries, long timeout, TimeUnit timeUnit) {
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
    }

    /**
     * attempts to resolved artifact using {@code supplier}. If ArtifactResolutionException is thrown, perform retries.
     *
     * @param supplier - performs artifact resolution
     * @param mapper - maps {@code ArtifactResolutionException} to a set of failed {@code ArtifactCoordinate}s
     * @param attemptedRepos - repositories used by {@code supplier}
     * @throws ArtifactTransferException if the number of retries exceeds {@code maxRetries}
     * @return
     */
    List<File> attemptResolve(Supplier supplier,
                              Function<ArtifactResolutionException, Set<ArtifactCoordinate>> mapper,
                              Set<Repository> attemptedRepos) {
        final RetryCounter retryCounter = new RetryCounter(attemptedRepos);
        while (true) {
            try {
                return supplier.get();
            } catch (ArtifactResolutionException ex) {
                final Set<ArtifactCoordinate> failed = mapper.apply(ex);

                retryCounter.addRetry(failed, ex);
            }
        }
    }

    /**
     * attempts to resolved artifact versions using {@code supplier}. If a MetadataTransferException occurs during resolution, perform retries.
     *
     * @param supplier - performs version resolution
     * @param attemptedRepos - repositories used by {@code supplier}
     * @throws ArtifactTransferException if the number of retries exceeds {@code maxRetries}
     * @return
     */
    VersionRangeResult attemptResolveMetadata(java.util.function.Supplier<VersionRangeResult> supplier,
                                       Set<Repository> attemptedRepos) {
        final RetryCounter retryCounter = new RetryCounter(attemptedRepos);

        while (true) {
            final VersionRangeResult versionRangeResult = supplier.get();

            final Optional<Exception> transferException = versionRangeResult.getExceptions().stream()
                    .filter(e -> e.getClass().equals(MetadataTransferException.class))
                    .findFirst();
            if (!transferException.isPresent()) {
                return versionRangeResult;
            } else {
                final Artifact artifact = versionRangeResult.getRequest().getArtifact();
                final Set<ArtifactCoordinate> failed = Set.of(new ArtifactCoordinate(
                        artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                        artifact.getClassifier(), artifact.getVersion()));

                retryCounter.addRetry(failed, transferException.get());
            }
        }
    }

    private class RetryCounter {
        private final Set<Repository> attemptedRepos;
        private int retryCount = 0;
        private Set<ArtifactCoordinate> lastFailed = Collections.emptySet();

        private RetryCounter(Set<Repository> attemptedRepos) {
            this.attemptedRepos = attemptedRepos;
        }

        private void addRetry(Set<ArtifactCoordinate> failed, Exception ex) throws ArtifactTransferException {
            if (!lastFailed.equals(failed)) {
                LOG.debugf("Resetting retry counter. The set of failing artifacts changed");
                // reset retry counter
                retryCount = 0;
            }

            if (retryCount++ < maxRetries) {
                // retry
                lastFailed = failed;
                LOG.debugf("Artifact resolution failed - retry #%d", retryCount);
                if (timeout > 0) {
                    try {
                        LOG.debugf("Pausing resolution retry for %dms", timeout);
                        Thread.sleep(timeUnit.toMillis(timeout));
                    } catch (InterruptedException e) {
                        throw new ArtifactTransferException(ex.getLocalizedMessage(), e, failed, attemptedRepos);
                    }
                }
            } else {
                LOG.debug("Max retry count reached, failed to resolve artifacts");
                throw new ArtifactTransferException(ex.getLocalizedMessage(), ex, failed, attemptedRepos);
            }
        }

    }

    interface Supplier {
        List<File> get() throws ArtifactResolutionException;
    }
}
