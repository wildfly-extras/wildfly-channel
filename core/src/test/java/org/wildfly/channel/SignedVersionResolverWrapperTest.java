/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.wildfly.channel.SignedVersionResolverWrapper.SIGNATURE_FILE_SUFFIX;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.channel.spi.ArtifactIdentifier;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;

class SignedVersionResolverWrapperTest {

    private static final ArtifactIdentifier.MavenResource ARTIFACT = new ArtifactIdentifier.MavenResource(
            "org.wildfly", "wildfly-ee-galleon-pack", "zip", null, "25.0.1.Final");

    @TempDir
    private Path tempDir;
    private MavenVersionsResolver resolver;
    private SignatureValidator signatureValidator;
    private SignedVersionResolverWrapper signedResolver;

    private File signatureFile;
    private File resolvedArtifactFile;

    @BeforeEach
    public void setUp() throws Exception {
        resolver = mock(MavenVersionsResolver.class);
        signatureValidator = mock(SignatureValidator.class);
        signedResolver = new SignedVersionResolverWrapper(resolver, List.of(new Repository("test", "test")),
                signatureValidator, Collections.emptyList());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        when(factory.create(any())).thenReturn(resolver);

        signatureFile = tempDir.resolve("test-signature.asc").toFile();
        Files.createFile(signatureFile.toPath());

        resolvedArtifactFile = tempDir.resolve("test-artifact").toFile();
        Files.createFile(resolvedArtifactFile.toPath());

        when(factory.create(any()))
                .thenReturn(resolver);
        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "1.2.0.Final"))
                .thenReturn(resolvedArtifactFile);
        when(resolver.resolveArtifact(ARTIFACT.groupId, ARTIFACT.artifactId, ARTIFACT.extension, ARTIFACT.classifier, ARTIFACT.version))
                .thenReturn(resolvedArtifactFile);
    }

    @Test
    public void invalidSignatureCausesError() throws Exception {
        final ChannelManifest baseManifest = new ManifestBuilder()
                .setId("manifest-one")
                .build();
        mockManifest(resolver, baseManifest, "test.channels:base-manifest:1.0.0");

        Files.createFile(tempDir.resolve("test-manifest.yaml.asc"));
        when(resolver.resolveArtifact("test.channels", "base-manifest",
                ChannelManifest.EXTENSION + SIGNATURE_FILE_SUFFIX, ChannelManifest.CLASSIFIER, "1.0.0"))
                .thenReturn(tempDir.resolve("test-manifest.yaml.asc").toFile());
        when(signatureValidator.validateSignature(any(), any(), any(), any())).thenReturn(SignatureResult.invalid(mock(ArtifactIdentifier.class)));
        assertThrows(SignatureValidator.SignatureException.class,
                () -> signedResolver.resolveChannelMetadata(List.of(new ChannelManifestCoordinate("test.channels", "base-manifest", "1.0.0"))));
    }

    @Test
    public void mvnManifestWithoutSignatureCausesError() throws Exception {
        final ChannelManifest baseManifest = new ManifestBuilder()
                .setId("manifest-one")
                .build();
        final Path manifestFile = tempDir.resolve("test-manifest.yaml");
        Files.writeString(manifestFile, ChannelManifestMapper.toYaml(baseManifest));

        when(resolver.resolveArtifact("test.channels", "base-manifest",
                ChannelManifest.EXTENSION + SIGNATURE_FILE_SUFFIX, ChannelManifest.CLASSIFIER, "1.0.0"))
                .thenThrow(ArtifactTransferException.class);
        assertThrows(SignatureValidator.SignatureException.class,
                () -> signedResolver.resolveChannelMetadata(List.of(new ChannelManifestCoordinate("test.channels", "base-manifest", "1.0.0"))));
    }

    @Test
    public void artifactWithCorrectSignatureIsValidated() throws Exception {
        // return signature when resolving it from Maven repository
        when(resolver.resolveArtifact(ARTIFACT.groupId, ARTIFACT.artifactId, ARTIFACT.extension + SIGNATURE_FILE_SUFFIX,
                ARTIFACT.classifier, ARTIFACT.version))
                .thenReturn(signatureFile);
        // accept all the validation requests
        when(signatureValidator.validateSignature(any(),
                any(), any(), any())).thenReturn(SignatureResult.ok());


        assertEquals(resolvedArtifactFile, signedResolver.resolveArtifact(ARTIFACT.groupId, ARTIFACT.artifactId, ARTIFACT.extension, ARTIFACT.classifier, ARTIFACT.version));

        verify(signatureValidator).validateSignature(any(), any(), any(), any());
    }

    @Test
    public void artifactWithoutSignatureIsRejected() throws Exception {
        // simulate situation where the signature file does not exist in the repository
        when(resolver.resolveArtifact(ARTIFACT.groupId, ARTIFACT.artifactId, ARTIFACT.extension + SIGNATURE_FILE_SUFFIX,
                ARTIFACT.classifier, ARTIFACT.version))
                .thenThrow(ArtifactTransferException.class);
        // accept all the validation requests
        when(signatureValidator.validateSignature(any(),
                any(), any(), any())).thenReturn(SignatureResult.ok());


        assertThrows(SignatureValidator.SignatureException.class, () -> signedResolver.resolveArtifact(
                ARTIFACT.groupId, ARTIFACT.artifactId, ARTIFACT.extension, ARTIFACT.classifier, ARTIFACT.version));


        // validateSignature should not have been called
        verify(signatureValidator, never()).validateSignature(any(), any(), any(), any());
    }

    @Test
    public void failedSignatureValidationThrowsException() throws Exception {
        // return signature when resolving it from Maven repository
        when(resolver.resolveArtifact(ARTIFACT.groupId, ARTIFACT.artifactId, ARTIFACT.extension + SIGNATURE_FILE_SUFFIX,
                ARTIFACT.classifier, ARTIFACT.version))
                .thenReturn(signatureFile);
        when(signatureValidator.validateSignature(eq(ARTIFACT),
                any(), any(), any())).thenReturn(SignatureResult.invalid(ARTIFACT));

        assertThrows(SignatureValidator.SignatureException.class, () -> signedResolver.resolveArtifact(ARTIFACT.groupId,
                ARTIFACT.artifactId, ARTIFACT.extension, ARTIFACT.classifier, ARTIFACT.version));


        verify(signatureValidator).validateSignature(any(), any(), any(), any());
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