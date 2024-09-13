/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.wildfly.channel.spi.ArtifactIdentifier;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;
import org.wildfly.channel.version.VersionMatcher;

/**
 * Resolve and validate a signature using the wrapped {@code MavenVersionsResolver}.
 */
public class SignedVersionResolverWrapper implements MavenVersionsResolver {

    protected static final String SIGNATURE_FILE_SUFFIX = ".asc";
    private final MavenVersionsResolver wrapped;
    private final SignatureValidator signatureValidator;
    private final List<String> gpgUrls;
    private final Collection<Repository> repositories;

    public SignedVersionResolverWrapper(MavenVersionsResolver wrapped, Collection<Repository> repositories,
                                        SignatureValidator signatureValidator, List<String> gpgUrls) {
        this.wrapped = wrapped;
        this.repositories = repositories;
        this.signatureValidator = signatureValidator;
        this.gpgUrls = gpgUrls;
    }

    private void validateGpgSignature(String groupId, String artifactId, String extension, String classifier,
                                      String version, File artifact) {
        final ArtifactIdentifier mavenArtifact = new ArtifactIdentifier.MavenResource(groupId, artifactId, extension,
                classifier, version);
        try {
            final File signature = wrapped.resolveArtifact(groupId, artifactId, extension + SIGNATURE_FILE_SUFFIX,
                    classifier, version);
            final SignatureResult signatureResult = signatureValidator.validateSignature(
                    mavenArtifact, new FileInputStream(artifact), new FileInputStream(signature),
                    gpgUrls);
            if (signatureResult.getResult() != SignatureResult.Result.OK) {
                throw new SignatureValidator.SignatureException("Failed to verify an artifact signature", signatureResult);
            }
        } catch (ArtifactTransferException | FileNotFoundException e) {
            throw new SignatureValidator.SignatureException("Unable to find required signature for " + mavenArtifact,
                    e, SignatureResult.noSignature(mavenArtifact));
        }
    }

    private void validateGpgSignature(URL artifactFile, URL signature) throws IOException {
        final SignatureResult signatureResult = signatureValidator.validateSignature(
                new ArtifactIdentifier.UrlResource(artifactFile),
                artifactFile.openStream(), signature.openStream(),
                gpgUrls
        );

        if (signatureResult.getResult() != SignatureResult.Result.OK) {
            throw new SignatureValidator.SignatureException("Failed to verify an artifact signature", signatureResult);
        }
    }

    @Override
    public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier) {
        return wrapped.getAllVersions(groupId, artifactId, extension, classifier);
    }

    @Override
    public File resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws ArtifactTransferException {
        final File artifact = wrapped.resolveArtifact(groupId, artifactId, extension, classifier, version);

        validateGpgSignature(groupId, artifactId, extension, classifier, version, artifact);

        return artifact;
    }

    @Override
    public List<File> resolveArtifacts(List<ArtifactCoordinate> coordinates) throws ArtifactTransferException {
        final List<File> resolvedArtifacts = wrapped.resolveArtifacts(coordinates);

        try {
            final List<File> signatures = wrapped.resolveArtifacts(coordinates.stream()
                    .map(c->new ArtifactCoordinate(c.getGroupId(), c.getArtifactId(), c.getExtension() + SIGNATURE_FILE_SUFFIX,
                            c.getClassifier(), c.getVersion()))
                    .collect(Collectors.toList()));
            for (int i = 0; i < resolvedArtifacts.size(); i++) {
                final File artifact = resolvedArtifacts.get(i);
                final ArtifactCoordinate c = coordinates.get(i);
                final ArtifactIdentifier.MavenResource mavenArtifact = new ArtifactIdentifier.MavenResource(c.getGroupId(), c.getArtifactId(),
                        c.getExtension(), c.getClassifier(), c.getVersion());
                final File signature = signatures.get(i);
                try {
                    final SignatureResult signatureResult = signatureValidator.validateSignature(mavenArtifact,
                            new FileInputStream(artifact), new FileInputStream(signature), gpgUrls);
                    if (signatureResult.getResult() != SignatureResult.Result.OK) {
                        throw new SignatureValidator.SignatureException("Failed to verify an artifact signature", signatureResult);
                    }
                } catch (FileNotFoundException e) {
                    throw new SignatureValidator.SignatureException(String.format("Unable to find required signature for %s:%s:%s",
                            mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(), mavenArtifact.getVersion()),
                            SignatureResult.noSignature(mavenArtifact));
                }
            }
        } catch (ArtifactTransferException e) {
            final ArtifactIdentifier.MavenResource artifact = new ArtifactIdentifier.MavenResource(e.getUnresolvedArtifacts().stream().findFirst().get());
            throw new SignatureValidator.SignatureException(String.format("Unable to find required signature for %s:%s:%s",
                    artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()),
                    SignatureResult.noSignature(artifact));
        }


        return resolvedArtifacts;
    }

    @Override
    public List<URL> resolveChannelMetadata(List<? extends ChannelMetadataCoordinate> coords) throws ArtifactTransferException {
        requireNonNull(coords);

        final List<URL> resolvedMetadata = wrapped.resolveChannelMetadata(coords);

        List<URL> signatures = new ArrayList<>();

        for (ChannelMetadataCoordinate coord : coords) {
            if (coord.getUrl() != null) {
                try {
                    final URL signatureUrl;
                    if (coord.getSignatureUrl() == null) {
                        signatureUrl = new URL(coord.getUrl().toExternalForm() + SIGNATURE_FILE_SUFFIX);
                    } else {
                        signatureUrl = coord.getSignatureUrl();
                    }
                    signatures.add(signatureUrl);
                } catch (IOException e) {
                    throw new InvalidChannelMetadataException("Unable to download a detached signature file from: " + coord.getUrl().toExternalForm()+ SIGNATURE_FILE_SUFFIX,
                            List.of(e.getMessage()), e);
                }
                continue;
            }

            String version = coord.getVersion();
            if (version == null) {
                Set<String> versions = wrapped.getAllVersions(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier());
                Optional<String> latestVersion = VersionMatcher.getLatestVersion(versions);
                if (latestVersion.isPresent()){
                    version = latestVersion.get();
                } else {
                    throw new ArtifactTransferException(String.format("Unable to resolve the latest version of channel metadata signature %s:%s", coord.getGroupId(), coord.getArtifactId()),
                            singleton(new ArtifactCoordinate(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier(), "")),
                            attemptedRepositories());
                }
            }

            try {
                File channelArtifact = wrapped.resolveArtifact(coord.getGroupId(), coord.getArtifactId(),
                        coord.getExtension() + SIGNATURE_FILE_SUFFIX, coord.getClassifier(), version);
                signatures.add(channelArtifact.toURI().toURL());
            } catch (ArtifactTransferException e) {
                throw new SignatureValidator.SignatureException("Unable to find required signature for " + coord,
                        e, SignatureResult.noSignature(new ArtifactIdentifier.MavenResource(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier(), version)));
            } catch (MalformedURLException e) {
                throw new ArtifactTransferException(String.format("Unable to resolve the latest version of channel metadata signature %s:%s", coord.getGroupId(), coord.getArtifactId()), e,
                        singleton(new ArtifactCoordinate(coord.getGroupId(), coord.getArtifactId(),
                                coord.getExtension(), coord.getClassifier(), coord.getVersion())),
                        attemptedRepositories());
            }
        }

        try {
            for (int i = 0; i < resolvedMetadata.size(); i++) {
                validateGpgSignature(resolvedMetadata.get(i), signatures.get(i));
            }
        } catch (IOException e) {
            throw new InvalidChannelMetadataException("Unable to read a detached signature file from: " + signatures,
                    List.of(e.getMessage()), e);
        }
        return resolvedMetadata;
    }

    private Set<Repository> attemptedRepositories() {
        return repositories.stream()
                .map(r -> new Repository(r.getId(), r.getUrl()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getMetadataReleaseVersion(String groupId, String artifactId) {
        return wrapped.getMetadataReleaseVersion(groupId, artifactId);
    }

    @Override
    public String getMetadataLatestVersion(String groupId, String artifactId) {
        return wrapped.getMetadataLatestVersion(groupId, artifactId);
    }
}
