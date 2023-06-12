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
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.ArtifactTransferException;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryHandlerTest {

    private RetryHandler resolver;
    @BeforeEach
    public void setUp() {
        resolver = new RetryHandler(5, 0);
    }

    @Test
    public void testResolveRetriedSupplier() throws Exception {
        final ArtifactResult res = new ArtifactResult(new ArtifactRequest());
        final ArtifactCoordinate coord = Mockito.mock(ArtifactCoordinate.class);

        AtomicInteger counter = new AtomicInteger(0);
        resolver.attemptResolve(()-> {
            if (counter.getAndIncrement() < 1) {
                throw new ArtifactResolutionException(List.of(res));
            }
            return Collections.emptyList();
        }, (ex)-> Set.of(coord), Collections.emptySet());

        assertEquals(2, counter.get());
    }

    @Test
    public void failAfterRetriesExceeded() throws Exception {
        final ArtifactResult res = new ArtifactResult(new ArtifactRequest());
        final ArtifactCoordinate coord = Mockito.mock(ArtifactCoordinate.class);

        AtomicInteger counter = new AtomicInteger(0);
        assertThrows(ArtifactTransferException.class,
                ()->resolver.attemptResolve(()-> {
                    counter.getAndIncrement();
                    throw new ArtifactResolutionException(List.of(res));
        }, (ex)-> Set.of(coord), Collections.emptySet()));

        assertEquals(6, counter.get());
    }

    @Test
    public void resetsCounterAfterArtifactResolvedExceeded() throws Exception {
        final Artifact artifact1 = new DefaultArtifact("org.test", "one", "jar" ,"1.2.3");
        final Artifact artifact2 = new DefaultArtifact("org.test", "two", "jar" ,"1.2.3");
        final ArtifactResult res1 = new ArtifactResult(new ArtifactRequest(artifact1, null, null));
        final ArtifactResult res2 = new ArtifactResult(new ArtifactRequest(artifact2, null, null));
        final ArtifactCoordinate coord = Mockito.mock(ArtifactCoordinate.class);

        AtomicInteger counter = new AtomicInteger(0);
        resolver.attemptResolve(() -> {
            // simulate first artifact being resolved after initial 4 reties
            // but second artifact requiring further 3 retries
            if (counter.getAndIncrement() < 4) {
                throw new ArtifactResolutionException(List.of(res1, res2));
            } else if (counter.getAndIncrement() < 7) {
                throw new ArtifactResolutionException(List.of(res2));
            } else {
                return Collections.emptyList();
            }
        }, (ex) -> Set.of(coord), Collections.emptySet());

        // in total retry count exceeds the allowed retries (as it was applied to different artifacts)
        assertEquals(8, counter.get());
    }
}