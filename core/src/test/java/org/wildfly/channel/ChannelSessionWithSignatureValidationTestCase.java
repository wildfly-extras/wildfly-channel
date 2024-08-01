package org.wildfly.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.wildfly.channel.ChannelImpl.SIGNATURE_FILE_SUFFIX;
import static org.wildfly.channel.ChannelManifestMapper.CURRENT_SCHEMA_VERSION;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;
import org.wildfly.channel.spi.ValidationResource;

public class ChannelSessionWithSignatureValidationTestCase {

    private static final ValidationResource.MavenResource ARTIFACT = new ValidationResource.MavenResource(
            "org.wildfly", "wildfly-ee-galleon-pack", "zip", null, "25.0.1.Final");

    private static final ValidationResource.MavenResource MANIFEST = new ValidationResource.MavenResource(
            "org.channels", "test-manifest", ChannelManifest.EXTENSION, ChannelManifest.CLASSIFIER, "1.0.0");

    @TempDir
    private Path tempDir;
    private MavenVersionsResolver resolver;
    private SignatureValidator signatureValidator;
    private MavenVersionsResolver.Factory factory;
    private File resolvedArtifactFile;
    private List<Channel> channels;
    private File signatureFile;

    @BeforeEach
    public void setUp() throws Exception {
        factory = mock(MavenVersionsResolver.Factory.class);
        resolver = mock(MavenVersionsResolver.class);
        signatureValidator = mock(SignatureValidator.class);
        when(factory.create(any())).thenReturn(resolver);

        // create a manfiest with a versionPattern to test signature od the latest resolved version is downloaded
        final String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'";
        // create a channel requiring a gpg check
        channels = List.of(new Channel.Builder()
                .setName("channel-0")
                .setGpgCheck(true)
                .setManifestCoordinate(MANIFEST.groupId, MANIFEST.artifactId, MANIFEST.version)
                .build());

        // the resolved files need to exist otherwise we can't create streams from them
        resolvedArtifactFile = tempDir.resolve("test-artifact").toFile();
        Files.createFile(resolvedArtifactFile.toPath());
        signatureFile = tempDir.resolve("test-signature.asc").toFile();
        Files.createFile(signatureFile.toPath());


        when(resolver.getAllVersions(ARTIFACT.groupId, ARTIFACT.artifactId, ARTIFACT.extension, ARTIFACT.classifier))
                .thenReturn(new HashSet<>(Arrays.asList("25.0.0.Final", ARTIFACT.version)));
        when(resolver.resolveArtifact(ARTIFACT.groupId, ARTIFACT.artifactId, ARTIFACT.extension, ARTIFACT.classifier, ARTIFACT.version))
                .thenReturn(resolvedArtifactFile);


        Path manifestFile = Files.writeString(tempDir.resolve("manifest.yaml"), manifest);
        when(resolver.resolveArtifact(MANIFEST.groupId, MANIFEST.artifactId, MANIFEST.extension, MANIFEST.classifier, MANIFEST.version))
                .thenReturn(manifestFile.toFile());
        when(resolver.resolveArtifact(MANIFEST.groupId, MANIFEST.artifactId,
                MANIFEST.extension + SIGNATURE_FILE_SUFFIX, MANIFEST.classifier, MANIFEST.version))
                .thenReturn(signatureFile);
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


        try (ChannelSession session = new ChannelSession(channels, factory, signatureValidator)) {
            MavenArtifact artifact = session.resolveMavenArtifact(
                    ARTIFACT.groupId, ARTIFACT.artifactId, ARTIFACT.extension, ARTIFACT.classifier, null);
            assertNotNull(artifact);

            assertEquals(ARTIFACT.groupId, artifact.getGroupId());
            assertEquals(ARTIFACT.artifactId, artifact.getArtifactId());
            assertEquals(ARTIFACT.extension, artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals(ARTIFACT.version, artifact.getVersion());
            assertEquals(resolvedArtifactFile, artifact.getFile());
            assertEquals("channel-0", artifact.getChannelName().get());
        }

        // validateSignature should have been called for the manifest and the artifact
        verify(signatureValidator, times(2)).validateSignature(any(), any(), any(), any());
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

        try (ChannelSession session = new ChannelSession(channels, factory, signatureValidator)) {
            assertThrows(SignatureValidator.SignatureException.class, () -> session.resolveMavenArtifact(
                    ARTIFACT.groupId, ARTIFACT.artifactId, ARTIFACT.extension, ARTIFACT.classifier, null));
        }

        // validateSignature should have been called for the manifest only
        verify(signatureValidator, times(1)).validateSignature(any(), any(), any(), any());
    }

    @Test
    public void failedSignatureValidationThrowsException() throws Exception {
        // return signature when resolving it from Maven repository
        when(resolver.resolveArtifact(ARTIFACT.groupId, ARTIFACT.artifactId, ARTIFACT.extension + SIGNATURE_FILE_SUFFIX,
                ARTIFACT.classifier, ARTIFACT.version))
                .thenReturn(signatureFile);
        // simulate a valid signature of the channel manifest, and invalid signature of the artifact
        when(signatureValidator.validateSignature(eq(new ValidationResource.MavenResource(
                        MANIFEST.groupId, MANIFEST.artifactId, MANIFEST.extension, MANIFEST.classifier, MANIFEST.version)),
                any(), any(), any())).thenReturn(SignatureResult.ok());
        when(signatureValidator.validateSignature(eq(ARTIFACT),
                any(), any(), any())).thenReturn(SignatureResult.invalid(ARTIFACT));


        try (ChannelSession session = new ChannelSession(channels, factory, signatureValidator)) {
            assertThrows(SignatureValidator.SignatureException.class, () -> session.resolveMavenArtifact("org.wildfly",
                    "wildfly-ee-galleon-pack", "zip", null, "25.0.0.Final"));
        }

        // validateSignature should have been called for the manifest and the artifact
        verify(signatureValidator, times(2)).validateSignature(any(), any(), any(), any());
    }
}
