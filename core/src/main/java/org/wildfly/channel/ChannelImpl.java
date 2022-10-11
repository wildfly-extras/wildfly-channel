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

import static java.util.Objects.requireNonNull;
import static org.wildfly.channel.version.VersionMatcher.COMPARATOR;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;

/**
 * Java representation of a Channel.
 */
class ChannelImpl implements AutoCloseable {

    private Channel channelDefinition;

    private List<ChannelImpl> requiredChannels = Collections.emptyList();

    private ChannelManifest channelManifest;

    private MavenVersionsResolver resolver;

    // marks an instance of Channel as dependency of another channel
    private boolean dependency = false;

    public Optional<Blocklist> blocklist = Optional.empty();

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
    void init(MavenVersionsResolver.Factory factory, List<ChannelImpl> channels) {
        if (resolver != null) {
            //already initialized
            return;
        }

        resolver = factory.create(channelDefinition.getRepositories());

        if (channelDefinition.getManifestCoordinate() != null) {
            channelManifest = resolveManifest(channelDefinition.getManifestCoordinate());
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
            BlocklistCoordinate blocklistCoordinate = channelDefinition.getBlocklistCoordinate();
            final List<URL> urls = resolver.resolveChannelMetadata(List.of(blocklistCoordinate));
            this.blocklist = urls.stream()
                    .map(Blocklist::from)
                    .findFirst();
        }
    }

    private ChannelImpl findRequiredChannel(MavenVersionsResolver.Factory factory, List<ChannelImpl> channels, ManifestRequirement manifestRequirement) {
        ChannelImpl foundChannel = null;
        for (ChannelImpl c: channels) {
            if (c.getManifest() == null) {
                c.init(factory, channels);
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
        final ChannelImpl requiredChannel = new ChannelImpl(new Channel(null, null, null, channelDefinition.getRepositories(),
                new ChannelManifestCoordinate(groupId, artifactId, version), null,
                Channel.NoStreamStrategy.NONE));
        try {
            requiredChannel.init(factory, channels);
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


    static class ResolveLatestVersionResult {
        final String version;
        final ChannelImpl channel;

        ResolveLatestVersionResult(String version, ChannelImpl channel) {
            this.version = version;
            this.channel = channel;
        }
    }

    private ChannelManifest resolveManifest(ChannelManifestCoordinate manifestCoordinate) throws UnresolvedMavenArtifactException {
        return resolver.resolveChannelMetadata(List.of(manifestCoordinate))
                .stream()
                .map(ChannelManifestMapper::from)
                .findFirst().orElseThrow();
    }

    Optional<ResolveLatestVersionResult> resolveLatestVersion(String groupId, String artifactId, String extension, String classifier, String baseVersion) {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(resolver);

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
            Optional<String> foundVersionInRequiredChannels = foundVersions.keySet().stream().sorted(COMPARATOR.reversed()).findFirst();
            if (foundVersionInRequiredChannels.isPresent()) {
                return Optional.of(new ResolveLatestVersionResult(foundVersionInRequiredChannels.get(), foundVersions.get(foundVersionInRequiredChannels.get())));
            }

            // finally try the NoStreamStrategy
            switch (channelDefinition.getNoStreamStrategy()) {
                case LATEST:
                    Set<String> versions = resolver.getAllVersions(groupId, artifactId, extension, classifier);
                    final Optional<String> latestVersion = versions.stream().sorted(COMPARATOR.reversed()).findFirst();
                    if (latestVersion.isPresent()) {
                        return Optional.of(new ResolveLatestVersionResult(latestVersion.get(), this));
                    } else {
                        return Optional.empty();
                    }
                case MAVEN_LATEST:
                    String latestMetadataVersion = resolver.getMetadataLatestVersion(groupId, artifactId);
                    return Optional.of(new ResolveLatestVersionResult(latestMetadataVersion, this));
                case MAVEN_RELEASE:
                    String releaseMetadataVersion = resolver.getMetadataReleaseVersion(groupId, artifactId);
                    return Optional.of(new ResolveLatestVersionResult(releaseMetadataVersion, this));
                default:
                    return Optional.empty();
            }
        }

        Stream stream = foundStream.get();
        Optional<String> foundVersion = Optional.empty();
        // there is a stream, let's now check its version
        if (stream.getVersion() != null) {
            foundVersion = Optional.of(stream.getVersion());
        } else if (stream.getVersionPattern() != null) {
            // if there is a version pattern, we resolve all versions from Maven to find the latest one
            Set<String> versions = resolver.getAllVersions(groupId, artifactId, extension, classifier);
            if (this.blocklist.isPresent()) {
                final Set<String> blocklistedVersions = this.blocklist.get().getVersionsFor(groupId, artifactId);

                versions.removeAll(blocklistedVersions);
            }
            foundVersion = foundStream.get().getVersionComparator().matches(versions);
        }

        if (foundVersion.isPresent()) {
            return Optional.of(new ResolveLatestVersionResult(foundVersion.get(), this));
        }
        return Optional.empty();
    }

    MavenArtifact resolveDirectMavenArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(version);

        File file = resolver.resolveArtifact(groupId, artifactId, extension, classifier, version);
        return new MavenArtifact(groupId, artifactId, extension, classifier, version, file);
    }

    List<MavenArtifact> resolveDirectMavenArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
        coordinates.stream().forEach(c->{
            requireNonNull(c.getGroupId());
            requireNonNull(c.getArtifactId());
            requireNonNull(c.getVersion());
        });
        final List<File> files = resolver.resolveArtifacts(coordinates);

        final ArrayList<MavenArtifact> res = new ArrayList<>();
        for (int i = 0; i < coordinates.size(); i++) {
            final ArtifactCoordinate request = coordinates.get(i);
            final MavenArtifact resolvedArtifact = new MavenArtifact(request.getGroupId(), request.getArtifactId(), request.getExtension(), request.getClassifier(), request.getVersion(), files.get(i));

            res.add(resolvedArtifact);
        }
        return res;
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

        return new ResolveArtifactResult(resolver.resolveArtifact(groupId, artifactId, extension, classifier, version), this);
    }

    List<ResolveArtifactResult> resolveArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
        final List<File> resolvedArtifacts = resolver.resolveArtifacts(coordinates);
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
