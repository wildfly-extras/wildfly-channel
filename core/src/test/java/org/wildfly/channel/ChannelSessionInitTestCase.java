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

package org.wildfly.channel;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.channel.spi.MavenVersionsResolver;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChannelSessionInitTestCase {
    @TempDir
    private Path tempDir;

    /*
     * Verify that a manifest required by ID is resolved from a list of channels
     */
    @Test
    public void resolveRequiredChannelById() throws Exception {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        when(factory.create(any())).thenReturn(resolver);

        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "1.2.0.Final"))
                .thenReturn(mock(File.class));

        final ChannelManifest requiredManifest = new ManifestBuilder()
                .setId("required-manifest-one")
                .addStream("org.example", "foo-bar", "1.2.0.Final")
                .build();
        mockManifest(resolver, requiredManifest, "test.channels:required-manifest:1.0.0");

        final ChannelManifest baseManifest = new ManifestBuilder()
                .addRequires("required-manifest-one")
                .build();
        mockManifest(resolver, baseManifest, "test.channels:base-manifest:1.0.0");

        // two channels providing base- and required- manifests
        List<Channel> channels = List.of(new ChannelBuilder()
                        .setName("root level requiring channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "base-manifest", "1.0.0")
                        .build(),
                new ChannelBuilder()
                        .setName("required channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "required-manifest", "1.0.0")
                        .build()
        );

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact artifact = session.resolveMavenArtifact("org.example", "foo-bar", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("foo-bar", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("1.2.0.Final", artifact.getVersion());
        }
    }

    @Test
    public void throwExceptionRequiredChannelIdNotAvailable() throws Exception {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        when(factory.create(any())).thenReturn(resolver);

        final ChannelManifest baseManifest = new ManifestBuilder()
                .addRequires("i-dont-exist")
                .build();
        mockManifest(resolver, baseManifest, "test.channels:base-manifest:1.0.0");

        List<Channel> channels = List.of(new ChannelBuilder()
                        .setName("root level requiring channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "base-manifest", "1.0.0")
                        .build()
                );
        assertThrows(UnresolvedRequiredManifestException.class, () -> new ChannelSession(channels, factory));
    }

    @Test
    public void throwExceptionRequiredChannelIdNotAvailableAndNotAbleToResolve() throws Exception {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        when(factory.create(any())).thenReturn(resolver);

        final ChannelManifest baseManifest = new ManifestBuilder()
                .addRequires("i-dont-exist", "test.channels", "i-dont-exist", "1.0.0")
                .build();
        mockManifest(resolver, baseManifest, "test.channels:base-manifest:1.0.0");

        when(resolver.resolveChannelMetadata(List.of(new ChannelManifestCoordinate("test.channels", "i-dont-exist", "1.0.0"))))
                .thenThrow(UnresolvedMavenArtifactException.class);

        List<Channel> channels = List.of(new ChannelBuilder()
                        .setName("root level requiring channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "base-manifest", "1.0.0")
                        .build()
        );
        assertThrows(UnresolvedRequiredManifestException.class, () -> new ChannelSession(channels, factory));
    }

    @Test
    public void versionInRequiredChannelIsOverridenByBase() throws Exception {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        when(factory.create(any())).thenReturn(resolver);

        when(resolver.resolveArtifact(eq("org.example"), eq("foo-bar"), eq(null), eq(null), any()))
                .thenReturn(mock(File.class));

        final ChannelManifest requiredManifest = new ManifestBuilder()
                .setId("required-manifest-one")
                .addStream("org.example", "foo-bar", "1.2.0.Final")
                .build();
        mockManifest(resolver, requiredManifest, "test.channels:required-manifest:1.0.0");

        final ChannelManifest baseManifest = new ManifestBuilder()
                .addRequires("required-manifest-one")
                .addStream("org.example", "foo-bar", "1.0.0.Final")
                .build();
        mockManifest(resolver, baseManifest, "test.channels:base-manifest:1.0.0");

        // two channels providing base- and required- manifests
        List<Channel> channels = List.of(new ChannelBuilder()
                        .setName("root level requiring channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "base-manifest", "1.0.0")
                        .build(),
                new ChannelBuilder()
                        .setName("required channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "required-manifest", "1.0.0")
                        .build()
        );

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact artifact = session.resolveMavenArtifact("org.example", "foo-bar", null, null, "0");
            assertNotNull(artifact);
            assertEquals("1.0.0.Final", artifact.getVersion());
        }
    }

    @Test
    public void cyclicDependencyCausesError() throws Exception {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        when(factory.create(any())).thenReturn(resolver);

        final ChannelManifest requiredManifest = new ManifestBuilder()
                .setId("required-manifest-one")
                .addRequires("base-manifest")
                .build();
        mockManifest(resolver, requiredManifest, "test.channels:required-manifest:1.0.0");

        final ChannelManifest baseManifest = new ManifestBuilder()
                .setId("base-manifest")
                .addRequires("required-manifest-one")
                .build();
        mockManifest(resolver, baseManifest, "test.channels:base-manifest:1.0.0");

        // two channels providing base- and required- manifests
        List<Channel> channels = List.of(new ChannelBuilder()
                        .setName("root level requiring channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "base-manifest", "1.0.0")
                        .build(),
                new ChannelBuilder()
                        .setName("required channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "required-manifest", "1.0.0")
                        .build()
        );
        assertThrows(CyclicDependencyException.class, () -> new ChannelSession(channels, factory));
    }

    @Test
    public void indirectCyclicDependency() throws Exception {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        when(factory.create(any())).thenReturn(resolver);

        final ChannelManifest requiredManifest = new ManifestBuilder()
                .setId("required-manifest-one")
                .addRequires("required-manifest-two")
                .build();
        mockManifest(resolver, requiredManifest, "test.channels:required-manifest:1.0.0");

        final ChannelManifest requiredManifest2 = new ManifestBuilder()
                .setId("required-manifest-two")
                .addRequires("base-manifest")
                .build();
        mockManifest(resolver, requiredManifest2, "test.channels:required-manifest-two:1.0.0");

        final ChannelManifest baseManifest = new ManifestBuilder()
                .setId("base-manifest")
                .addRequires("required-manifest-one")
                .build();
        mockManifest(resolver, baseManifest, "test.channels:base-manifest:1.0.0");

        // two channels providing base- and required- manifests
        List<Channel> channels = List.of(new ChannelBuilder()
                        .setName("root level requiring channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "base-manifest", "1.0.0")
                        .build(),
                new ChannelBuilder()
                        .setName("required channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "required-manifest", "1.0.0")
                        .build(),
                new ChannelBuilder()
                        .setName("required channel two")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "required-manifest-two", "1.0.0")
                        .build()
        );
        assertThrows(CyclicDependencyException.class, () -> new ChannelSession(channels, factory));
    }

    @Test
    public void detectCyclicDependencyOnSelf() throws Exception {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        when(factory.create(any())).thenReturn(resolver);

        final ChannelManifest baseManifest = new ManifestBuilder()
                .setId("base-manifest")
                .addRequires("base-manifest")
                .build();
        mockManifest(resolver, baseManifest, "test.channels:base-manifest:1.0.0");

        // two channels providing base- and required- manifests
        List<Channel> channels = List.of(new ChannelBuilder()
                        .setName("root level requiring channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "base-manifest", "1.0.0")
                        .build()
        );
        assertThrows(CyclicDependencyException.class, () -> new ChannelSession(channels, factory));
    }

    @Test
    public void indirectCyclicDependency2() throws Exception {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        when(factory.create(any())).thenReturn(resolver);

        final ChannelManifest requiredManifest = new ManifestBuilder()
                .setId("required-manifest-one")
                .addRequires("required-manifest-two")
                .build();
        mockManifest(resolver, requiredManifest, "test.channels:required-manifest:1.0.0");

        final ChannelManifest requiredManifest2 = new ManifestBuilder()
                .setId("required-manifest-two")
                .addRequires("required-manifest-three")
                .build();
        mockManifest(resolver, requiredManifest2, "test.channels:required-manifest-two:1.0.0");

        final ChannelManifest requiredManifest3 = new ManifestBuilder()
                .setId("required-manifest-three")
                .addRequires("required-manifest-two")
                .build();
        mockManifest(resolver, requiredManifest3, "test.channels:required-manifest-three:1.0.0");

        final ChannelManifest baseManifest = new ManifestBuilder()
                .setId("base-manifest")
                .addRequires("required-manifest-one")
                .build();
        mockManifest(resolver, baseManifest, "test.channels:base-manifest:1.0.0");

        // two channels providing base- and required- manifests
        List<Channel> channels = List.of(new ChannelBuilder()
                        .setName("root level requiring channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "base-manifest", "1.0.0")
                        .build(),
                new ChannelBuilder()
                        .setName("required channel")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "required-manifest", "1.0.0")
                        .build(),
                new ChannelBuilder()
                        .setName("required channel two")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "required-manifest-two", "1.0.0")
                        .build(),
                new ChannelBuilder()
                        .setName("required channel three")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "required-manifest-three", "1.0.0")
                        .build()
        );
        assertThrows(CyclicDependencyException.class, () -> new ChannelSession(channels, factory));
    }

    @Test
    public void duplicatedManifestIDsAreDetected() throws Exception {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        when(factory.create(any())).thenReturn(resolver);

        final ChannelManifest requiredManifest = new ManifestBuilder()
                .setId("manifest-one")
                .build();
        mockManifest(resolver, requiredManifest, "test.channels:required-manifest:1.0.0");

        final ChannelManifest baseManifest = new ManifestBuilder()
                .setId("manifest-one")
                .build();
        mockManifest(resolver, baseManifest, "test.channels:base-manifest:1.0.0");

        // two channels providing base- and required- manifests
        List<Channel> channels = List.of(new ChannelBuilder()
                        .setName("channel one")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "base-manifest", "1.0.0")
                        .build(),
                new ChannelBuilder()
                        .setName("channel two")
                        .addRepository("test", "test")
                        .setManifestCoordinate("test.channels", "required-manifest", "1.0.0")
                        .build()
        );
        assertThrows(RuntimeException.class, () -> new ChannelSession(channels, factory));
    }

    private void mockManifest(MavenVersionsResolver resolver, ChannelManifest manifest, String gav) throws IOException {
        mockManifest(resolver, ChannelManifestMapper.toYaml(manifest), gav);
    }

    private void mockManifest(MavenVersionsResolver resolver, String manifest, String gav) throws IOException {
        Path manifestFile = tempDir.resolve("manifest_" + RandomUtils.nextInt() + ".yaml");
        Files.writeString(manifestFile, manifest);

        mockManifest(resolver, manifestFile.toUri().toURL(), gav);
    }

    private void mockManifest(MavenVersionsResolver resolver, URL manifestUrl, String gavString) throws IOException {
        final String[] splitGav = gavString.split(":");
        final MavenCoordinate gav = new MavenCoordinate(splitGav[0], splitGav[1], splitGav.length == 3 ? splitGav[2] : null);
        when(resolver.resolveChannelMetadata(eq(List.of(ChannelManifestCoordinate.create(null, gav)))))
                .thenReturn(List.of(manifestUrl));
    }
}
