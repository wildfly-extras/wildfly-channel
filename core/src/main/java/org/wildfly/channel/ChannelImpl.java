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
import static org.wildfly.channel.version.VersionMatcher.COMPARATOR;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;
import org.wildfly.channel.spi.ValidationResource;
import org.wildfly.channel.version.VersionMatcher;

/**
 * Java representation of a Channel.
 */
class ChannelImpl implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ChannelImpl.class);

    private final Channel channelDefinition;
    private Channel resolvedChannel;

    private List<ChannelImpl> requiredChannels = Collections.emptyList();

    private ChannelManifest channelManifest;

    private MavenVersionsResolver resolver;

    // marks an instance of Channel as dependency of another channel
    private boolean dependency = false;

    public Optional<Blocklist> blocklist = Optional.empty();

    private ChannelManifestCoordinate resolvedCoordinate;

    private SignatureValidator signatureValidator;

    public ChannelManifest getManifest() {
        return channelManifest;
    }

    public ChannelImpl(Channel channelDefinition) {
        this.channelDefinition = channelDefinition;
    }


    /**
     *
     * @param factory
     * @param channels
     * @throws UnresolvedRequiredManifestException - if a required manifest cannot be resolved either via maven coordinates or in the list of channels
     * @throws CyclicDependencyException - if the required manifests form a cyclic dependency
     */
    void init(MavenVersionsResolver.Factory factory, List<ChannelImpl> channels, SignatureValidator signatureValidator) {
        if (resolver != null) {
            //already initialized
            return;
        }
        this.signatureValidator = signatureValidator;

        resolver = factory.create(channelDefinition.getRepositories());

        final Channel.Builder resolvedChannelBuilder = new Channel.Builder(channelDefinition);
        if (channelDefinition.getManifestCoordinate() != null) {
            final ChannelManifestCoordinate coordinate = resolveManifestVersion(channelDefinition);
            resolvedChannelBuilder.setManifestCoordinate(coordinate);
            channelManifest = resolveManifest(coordinate);
        } else {
            channelManifest = new ChannelManifest(null, null, null, Collections.emptyList());
        }

        final List<ManifestRequirement> manifestRequirements = channelManifest.getManifestRequirements();
        if (!manifestRequirements.isEmpty()) {
            requiredChannels = new ArrayList<>();
        }
        for (ManifestRequirement manifestRequirement : manifestRequirements) {
            ChannelImpl foundChannel = findRequiredChannel(factory, channels, manifestRequirement);
            requiredChannels.add(foundChannel);
        }

        if (channelDefinition.getBlocklistCoordinate() != null) {
            BlocklistCoordinate blocklistCoordinate = resolveBlocklistVersion(channelDefinition);
            if (blocklistCoordinate != null) {
                resolvedChannelBuilder.setBlocklistCoordinate(blocklistCoordinate);
                final List<URL> urls = resolveChannelMetadata(List.of(blocklistCoordinate), true);
                this.blocklist = urls.stream()
                        .map(Blocklist::from)
                        .findFirst();
            }
        }

        this.resolvedChannel = resolvedChannelBuilder.build();
    }

    private ChannelImpl findRequiredChannel(MavenVersionsResolver.Factory factory, List<ChannelImpl> channels, ManifestRequirement manifestRequirement) {
        ChannelImpl foundChannel = null;
        for (ChannelImpl c: channels) {
            if (c.getManifest() == null) {
                c.init(factory, channels, signatureValidator);
            }
            if (manifestRequirement.getId().equals(c.getManifest().getId())) {
                foundChannel = c;
                break;
            }
        }

        if (foundChannel == null) {
            if (manifestRequirement.getMavenCoordinate() == null) {
                throw new UnresolvedRequiredManifestException("Manifest with ID " + manifestRequirement.getId() + " is not available", manifestRequirement.getId());
            }
            foundChannel = createNewChannelFromMaven(factory, channels, manifestRequirement);
        }

        checkForCycles(foundChannel);

        foundChannel.markAsDependency();
        return foundChannel;
    }

    private ChannelImpl createNewChannelFromMaven(MavenVersionsResolver.Factory factory, List<ChannelImpl> channels, ManifestRequirement manifestRequirement) {
        String groupId = manifestRequirement.getGroupId();
        String artifactId = manifestRequirement.getArtifactId();
        String version = manifestRequirement.getVersion();
        if (version == null) {
            Set<String> versions = resolver.getAllVersions(groupId, artifactId, ChannelManifest.EXTENSION, ChannelManifest.CLASSIFIER);
            Optional<String> latest = VersionMatcher.getLatestVersion(versions);
            version = latest.orElseThrow(() -> new RuntimeException(String.format("Can not determine the latest version for Maven artifact %s:%s:%s:%s",
                    groupId, artifactId, ChannelManifest.EXTENSION, ChannelManifest.CLASSIFIER)));
        }
        final ChannelImpl requiredChannel = new ChannelImpl(new Channel(ChannelMapper.CURRENT_SCHEMA_VERSION, null, null, null, channelDefinition.getRepositories(),
                new ChannelManifestCoordinate(groupId, artifactId, version), null,
                Channel.NoStreamStrategy.NONE, channelDefinition.isGpgCheck(), channelDefinition.getGpgUrls()));
        try {
            requiredChannel.init(factory, channels, signatureValidator);
        } catch (UnresolvedMavenArtifactException e) {
            throw new UnresolvedRequiredManifestException("Manifest with ID " + manifestRequirement.getId() + " is not available", manifestRequirement.getId(), e);
        }
        return requiredChannel;
    }

    private void checkForCycles(ChannelImpl foundChannel) {
        final String manifestId = this.getManifest().getId();
        if (foundChannel.getManifest().getId() != null && foundChannel.getManifest().getId().equals(manifestId)) {
            throw new CyclicDependencyException("Illegal manifest dependency: " + manifestId + "->" + foundChannel.getManifest().getId());
        }
        if (foundChannel.requiredChannels.stream().map(ChannelImpl::getManifest).map(ChannelManifest::getId).filter((id)->id != null && id.equals(manifestId)).findFirst().isPresent()) {
            throw new CyclicDependencyException("Illegal manifest dependency: " + manifestId + "->" + foundChannel.getManifest().getId());
        }
        for (ChannelImpl requiredChannel : foundChannel.requiredChannels) {
            checkForCycles(requiredChannel);
        }
    }

    @Override
    public void close() {
        if (resolver != null) {
            for (ChannelImpl requiredChannel : requiredChannels) {
                requiredChannel.close();
            }
            this.resolver.close();
            this.resolver = null;
        }
    }
    private void markAsDependency() {
        this.dependency = true;
    }

    boolean isDependency() {
        return dependency;
    }

    Channel getResolvedChannelDefinition() {
        return resolvedChannel;
    }

    public Blocklist getBlocklist() {
        return blocklist.orElse(null);
    }

    static class ResolveLatestVersionResult {
        final String version;
        final ChannelImpl channel;

        ResolveLatestVersionResult(String version, ChannelImpl channel) {
            this.version = version;
            this.channel = channel;
        }
    }

    private ChannelManifestCoordinate resolveManifestVersion(Channel baseDefinition) {
        final ChannelManifestCoordinate manifestCoordinate = baseDefinition.getManifestCoordinate();

        // if we already have a version or it is a URL manifest, return it
        if (manifestCoordinate.getUrl() != null || manifestCoordinate.getMaven().getVersion() != null) {
            return manifestCoordinate;
        }

        final Set<String> allVersions = resolver.getAllVersions(
                manifestCoordinate.getGroupId(),
                manifestCoordinate.getArtifactId(),
                manifestCoordinate.getExtension(),
                manifestCoordinate.getClassifier()
        );
        Optional<String> latestVersion = VersionMatcher.getLatestVersion(allVersions);
        String version = latestVersion.orElseThrow(() ->
                new ArtifactTransferException(String.format("Unable to resolve the latest version of channel metadata %s:%s", manifestCoordinate.getGroupId(), manifestCoordinate.getArtifactId()),
                        singleton(new ArtifactCoordinate(manifestCoordinate.getGroupId(), manifestCoordinate.getArtifactId(), manifestCoordinate.getExtension(), manifestCoordinate.getClassifier(), "")),
                        attemptedRepositories()));

        return new ChannelManifestCoordinate(manifestCoordinate.getGroupId(), manifestCoordinate.getArtifactId(), version);

    }

    private BlocklistCoordinate resolveBlocklistVersion(Channel baseDefinition) {
        final BlocklistCoordinate blocklistCoordinate = baseDefinition.getBlocklistCoordinate();

        if (blocklistCoordinate == null) {
            return null;
        }

        // if we already have a version or it is a URL blocklist, return it
        if (blocklistCoordinate.getUrl() != null || blocklistCoordinate.getMaven().getVersion() != null) {
            return blocklistCoordinate;
        }

        final Set<String> allVersions = resolver.getAllVersions(
                blocklistCoordinate.getGroupId(),
                blocklistCoordinate.getArtifactId(),
                blocklistCoordinate.getExtension(),
                blocklistCoordinate.getClassifier()
        );
        Optional<String> latestVersion = VersionMatcher.getLatestVersion(allVersions);

        // different from manifest resolution. If we were not able to resolve the blocklist, we assume it doesn't exist (yet) and
        // we proceed without blocklist
        return latestVersion
                .map(v->new BlocklistCoordinate(blocklistCoordinate.getGroupId(), blocklistCoordinate.getArtifactId(), v))
                .orElse(null);
    }

    private ChannelManifest resolveManifest(ChannelManifestCoordinate manifestCoordinate) throws UnresolvedMavenArtifactException {
        if (manifestCoordinate.getUrl() == null && manifestCoordinate.getMaven().getVersion() == null) {
            final Set<String> allVersions = resolver.getAllVersions(
                    manifestCoordinate.getGroupId(),
                    manifestCoordinate.getArtifactId(),
                    manifestCoordinate.getExtension(),
                    manifestCoordinate.getClassifier()
                    );
            Optional<String> latestVersion = VersionMatcher.getLatestVersion(allVersions);
            String version = latestVersion.orElseThrow(() ->
                    new ArtifactTransferException(String.format("Unable to resolve the latest version of channel metadata %s:%s", manifestCoordinate.getGroupId(), manifestCoordinate.getArtifactId()),
                            singleton(new ArtifactCoordinate(manifestCoordinate.getGroupId(), manifestCoordinate.getArtifactId(), manifestCoordinate.getExtension(), manifestCoordinate.getClassifier(), "")),
                            attemptedRepositories()));
            resolvedCoordinate = new ChannelManifestCoordinate(manifestCoordinate.getGroupId(), manifestCoordinate.getArtifactId(), version);
        } else {
            resolvedCoordinate = manifestCoordinate;
        }
        return resolveChannelMetadata(List.of(resolvedCoordinate), false)
                .stream()
                .map(ChannelManifestMapper::from)
                .findFirst().orElseThrow();
    }

    /**
     * Resolve a list of channel metadata artifacts based on the coordinates.
     * If the {@code ChannelMetadataCoordinate} contains non-null URL, that URL is returned.
     * If the {@code ChannelMetadataCoordinate} contains non-null Maven coordinates, the Maven artifact will be resolved
     * and a URL to it will be returned.
     * If the Maven coordinates specify only groupId and artifactId, latest available version of matching Maven artifact
     * will be resolved.
     *
     * The order of returned URLs is the same as order of coordinates.
     *
     * @param coords - list of ChannelMetadataCoordinate.
     * @param optional - if artifact is optional, the method will return an empty collection if no versions are found
     *
     * @return a list of URLs to the metadata files
     *
     * @throws ArtifactTransferException if any artifacts can not be resolved.
     */
    public List<URL> resolveChannelMetadata(List<? extends ChannelMetadataCoordinate> coords, boolean optional) throws ArtifactTransferException {
        requireNonNull(coords);

        List<URL> channels = new ArrayList<>();

        for (ChannelMetadataCoordinate coord : coords) {
            if (coord.getUrl() != null) {
                LOG.infof("Resolving channel metadata at %s", coord.getUrl());
                channels.add(coord.getUrl());
                if (channelDefinition.isGpgCheck()) {
                    try {
                        validateGpgSignature(coord.getUrl(), new URL(coord.getUrl().toExternalForm()+".asc"));
                    } catch (IOException e) {
                        throw new InvalidChannelMetadataException("Unable to download a detached signature file from: " + coord.getUrl().toExternalForm()+".asc",
                                List.of(e.getMessage()), e);
                    }
                }
                continue;
            }

            String version = coord.getVersion();
            if (version == null) {
                Set<String> versions = resolver.getAllVersions(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier());
                Optional<String> latestVersion = VersionMatcher.getLatestVersion(versions);
                if (latestVersion.isPresent()){
                    version = latestVersion.get();
                } else if (optional) {
                    return Collections.emptyList();
                } else {
                    throw new ArtifactTransferException(String.format("Unable to resolve the latest version of channel metadata %s:%s", coord.getGroupId(), coord.getArtifactId()),
                            singleton(new ArtifactCoordinate(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier(), "")),
                            attemptedRepositories());
                }
            }
            LOG.infof("Resolving channel metadata from Maven artifact %s:%s:%s", coord.getGroupId(), coord.getArtifactId(), version);
            File channelArtifact = resolver.resolveArtifact(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier(), version);
            try {
                channels.add(channelArtifact.toURI().toURL());
                if (channelDefinition.isGpgCheck()) {
                    validateGpgSignature(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier(), version, channelArtifact);
                }
            } catch (MalformedURLException e) {
                throw new ArtifactTransferException(String.format("Unable to resolve the latest version of channel metadata %s:%s", coord.getGroupId(), coord.getArtifactId()), e,
                        singleton(new ArtifactCoordinate(coord.getGroupId(), coord.getArtifactId(),
                                coord.getExtension(), coord.getClassifier(), coord.getVersion())),
                        attemptedRepositories());
            }
        }
        return channels;
    }

    private Set<Repository> attemptedRepositories() {
        return channelDefinition.getRepositories().stream()
                .map(r -> new Repository(r.getId(), r.getUrl()))
                .collect(Collectors.toSet());
    }

    Optional<ResolveLatestVersionResult> resolveLatestVersion(String groupId, String artifactId, String extension, String classifier, String baseVersion) {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(resolver);

        Set<String> blocklistedVersions = Collections.emptySet();
        if (this.blocklist.isPresent()) {
            blocklistedVersions = this.blocklist.get().getVersionsFor(groupId, artifactId);
        }

        // first we find if there is a stream for that given (groupId, artifactId).
        Optional<Stream> foundStream = channelManifest.findStreamFor(groupId, artifactId);
        // no stream for this artifact, let's look into the required channel
        if (!foundStream.isPresent()) {
            // we return the latest value from the required channels
            Map<String, ChannelImpl> foundVersions = new HashMap<>();
            for (ChannelImpl requiredChannel : requiredChannels) {
                Optional<ChannelImpl.ResolveLatestVersionResult> found = requiredChannel.resolveLatestVersion(groupId, artifactId, extension, classifier, baseVersion);
                if (found.isPresent()) {
                    foundVersions.put(found.get().version, found.get().channel);
                }
            }
            foundVersions.keySet().removeAll(blocklistedVersions);
            Optional<String> foundVersionInRequiredChannels = foundVersions.keySet().stream().sorted(COMPARATOR.reversed()).findFirst();
            if (foundVersionInRequiredChannels.isPresent()) {
                return Optional.of(new ResolveLatestVersionResult(foundVersionInRequiredChannels.get(), foundVersions.get(foundVersionInRequiredChannels.get())));
            }

            // finally try the NoStreamStrategy
            switch (channelDefinition.getNoStreamStrategy()) {
                case LATEST:
                    Set<String> versions = resolver.getAllVersions(groupId, artifactId, extension, classifier);
                    versions.removeAll(blocklistedVersions);
                    final Optional<String> latestVersion = versions.stream().sorted(COMPARATOR.reversed()).findFirst();
                    if (latestVersion.isPresent()) {
                        return Optional.of(new ResolveLatestVersionResult(latestVersion.get(), this));
                    } else {
                        return Optional.empty();
                    }
                case MAVEN_LATEST:
                    try {
                        String latestMetadataVersion = resolver.getMetadataLatestVersion(groupId, artifactId);
                        if (blocklistedVersions.contains(latestMetadataVersion)) {
                            return Optional.empty();
                        }
                        return Optional.of(new ResolveLatestVersionResult(latestMetadataVersion, this));
                    } catch (NoStreamFoundException e) {
                        LOG.debugf(e, "Metadata resolution for %s:%s failed in channel %s",
                                groupId, artifactId, this.getResolvedChannelDefinition().getName());
                        return Optional.empty();
                    }
                case MAVEN_RELEASE:
                    try {
                        String releaseMetadataVersion = resolver.getMetadataReleaseVersion(groupId, artifactId);
                        if (blocklistedVersions.contains(releaseMetadataVersion)) {
                            return Optional.empty();
                        }
                        return Optional.of(new ResolveLatestVersionResult(releaseMetadataVersion, this));
                    } catch (NoStreamFoundException e) {
                        LOG.debugf(e, "Metadata resolution for %s:%s failed in channel %s",
                                groupId, artifactId, this.getResolvedChannelDefinition().getName());
                        return Optional.empty();
                    }
                default:
                    return Optional.empty();
            }
        }

        Stream stream = foundStream.get();
        Optional<String> foundVersion = Optional.empty();
        // there is a stream, let's now check its version
        if (stream.getVersion() != null) {
            foundVersion = Optional.of(stream.getVersion());
            if (foundVersion.isPresent() && blocklistedVersions.contains(foundVersion.get())) {
                return Optional.empty();
            }
        } else if (stream.getVersionPattern() != null) {
            // if there is a version pattern, we resolve all versions from Maven to find the latest one
            Set<String> versions = resolver.getAllVersions(groupId, artifactId, extension, classifier);
            versions.removeAll(blocklistedVersions);
            foundVersion = foundStream.get().getVersionComparator().matches(versions);
        }

        if (foundVersion.isPresent()) {
            return Optional.of(new ResolveLatestVersionResult(foundVersion.get(), this));
        }
        return Optional.empty();
    }

    static class ResolveArtifactResult {
        File file;
        ChannelImpl channel;

        ResolveArtifactResult(File file, ChannelImpl channel) {
            this.file = file;
            this.channel = channel;
        }
    }

    ResolveArtifactResult resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(version);
        requireNonNull(resolver);

        // first we looked into the required channels
        for (ChannelImpl requiredChannel : requiredChannels) {
            try {
                return requiredChannel.resolveArtifact(groupId, artifactId, extension, classifier, version);
            } catch (UnresolvedMavenArtifactException e) {
                // ignore if the required channel are not able to resolve the artifact
            }
        }

        final File artifact = resolver.resolveArtifact(groupId, artifactId, extension, classifier, version);
        if (channelDefinition.isGpgCheck()) {
            validateGpgSignature(groupId, artifactId, extension, classifier, version, artifact);
        }
        return new ResolveArtifactResult(artifact, this);
    }

    private void validateGpgSignature(String groupId, String artifactId, String extension, String classifier,
                                      String version, File artifact) {
        final ValidationResource mavenArtifact = new ValidationResource.MavenResource(groupId, artifactId, extension,
                classifier, version);
        try {
            final File signature = resolver.resolveArtifact(groupId, artifactId, extension + ".asc",
                    classifier, version);
            final SignatureResult signatureResult = signatureValidator.validateSignature(
                    mavenArtifact, new FileInputStream(artifact), new FileInputStream(signature),
                    channelDefinition.getGpgUrls());
            if (signatureResult.getResult() != SignatureResult.Result.OK) {
                throw new SignatureValidator.SignatureException("Failed to verify an artifact signature", signatureResult);
            }
        } catch (ArtifactTransferException | FileNotFoundException e) {
            throw new SignatureValidator.SignatureException("Unable to find required signature for " + mavenArtifact,
                    SignatureResult.noSignature(mavenArtifact));
        }
    }

    private void validateGpgSignature(URL artifactFile, URL signature) throws IOException {
        final SignatureResult signatureResult = signatureValidator.validateSignature(
                new ValidationResource.UrlResource(artifactFile),
                artifactFile.openStream(), signature.openStream(),
                channelDefinition.getGpgUrls()
        );

        if (signatureResult.getResult() != SignatureResult.Result.OK) {
            throw new SignatureValidator.SignatureException("Failed to verify an artifact signature", signatureResult);
        }
    }

    List<ResolveArtifactResult> resolveArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
        final List<File> resolvedArtifacts = resolver.resolveArtifacts(coordinates);

        if (channelDefinition.isGpgCheck()) {
            try {
                final List<File> signatures = resolver.resolveArtifacts(coordinates.stream()
                        .map(c->new ArtifactCoordinate(c.getGroupId(), c.getArtifactId(), c.getExtension() + ".asc",
                                c.getClassifier(), c.getVersion()))
                        .collect(Collectors.toList()));
                for (int i = 0; i < resolvedArtifacts.size(); i++) {
                    final File artifact = resolvedArtifacts.get(i);
                    final ArtifactCoordinate c = coordinates.get(i);
                    final ValidationResource.MavenResource mavenArtifact = new ValidationResource.MavenResource(c.getGroupId(), c.getArtifactId(),
                            c.getExtension(), c.getClassifier(), c.getVersion());
                    final File signature = signatures.get(i);
                    try {
                        final SignatureResult signatureResult = signatureValidator.validateSignature(mavenArtifact,
                                new FileInputStream(artifact), new FileInputStream(signature), channelDefinition.getGpgUrls());
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
                final ValidationResource.MavenResource artifact = new ValidationResource.MavenResource(e.getUnresolvedArtifacts().stream().findFirst().get());
                throw new SignatureValidator.SignatureException(String.format("Unable to find required signature for %s:%s:%s",
                        artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()),
                        SignatureResult.noSignature(artifact));
            }
        }

        return resolvedArtifacts.stream().map(f->new ResolveArtifactResult(f, this)).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "Channel{" +
                "channelDefinition=" + channelDefinition +
                ", requiredChannels=" + requiredChannels +
                ", channelManifest=" + channelManifest +
                ", resolver=" + resolver +
                ", dependency=" + dependency +
                ", blocklist=" + blocklist +
                '}';
    }
}
