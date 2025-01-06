package org.wildfly.channel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.wildfly.channel.version.VersionMatcher;

import java.net.URL;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import java.util.ArrayList;
import java.util.Objects;

import static java.util.Collections.emptyList;

public class Channel {

    public static final String CLASSIFIER="channel";
    public static final String EXTENSION="yaml";

    /** Version of the schema used by this channel.
     * This is a required field.
     */
    private String schemaVersion;

    /**
     * Name of the channel (as an one-line human readable description of the channel).
     * This is an optional field.
     */
    private String name;

    /**
     * Description of the channel. It can use multiple lines.
     * This is an optional field.
     */
    private String description;

    /**
     * Vendor of the channel.
     * This is an optional field.
     */
    private Vendor vendor;
    private List<Repository> repositories = new ArrayList<>();
    private BlocklistCoordinate blocklistCoordinate;
    private ChannelManifestCoordinate manifestCoordinate;
    private NoStreamStrategy noStreamStrategy = NoStreamStrategy.NONE;

    // no-arg constructor for maven plugins
    public Channel() {
        schemaVersion = ChannelMapper.CURRENT_SCHEMA_VERSION;
    }

    /**
     * Representation of a Channel resource using the current schema version.
     *
     * @see #Channel(String, String, String, Vendor, List, ChannelManifestCoordinate, BlocklistCoordinate, NoStreamStrategy)
     */
    public Channel(String name,
                   String description,
                   Vendor vendor,
                   List<Repository> repositories,
                   ChannelManifestCoordinate manifestCoordinate,
                   BlocklistCoordinate blocklistCoordinate,
                   NoStreamStrategy noStreamStrategy){
        this(ChannelMapper.CURRENT_SCHEMA_VERSION,
                name,
                description,
                vendor,
                repositories,
                manifestCoordinate,
                blocklistCoordinate,
                noStreamStrategy);
    }

    @JsonCreator
    public Channel(@JsonProperty(value = "schemaVersion", required = true) String schemaVersion,
                   @JsonProperty(value = "name") String name,
                   @JsonProperty(value = "description") String description,
                   @JsonProperty(value = "vendor") Vendor vendor,
                   @JsonProperty(value = "repositories")
                                 @JsonInclude(NON_EMPTY) List<Repository> repositories,
                   @JsonProperty(value = "manifest") ChannelManifestCoordinate manifestCoordinate,
                   @JsonProperty(value = "blocklist") @JsonInclude(NON_EMPTY) BlocklistCoordinate blocklistCoordinate,
                   @JsonProperty(value = "resolve-if-no-stream") NoStreamStrategy noStreamStrategy) {
        this.schemaVersion = schemaVersion;
        this.name = name;
        this.description = description;
        this.vendor = vendor;
        this.repositories = (repositories != null) ? repositories : emptyList();
        this.blocklistCoordinate = blocklistCoordinate;
        this.manifestCoordinate = manifestCoordinate;
        this.noStreamStrategy = (noStreamStrategy != null) ? noStreamStrategy: NoStreamStrategy.NONE;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getName() {
        return name;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getDescription() {
        return description;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Vendor getVendor() {
        return vendor;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Repository> getRepositories() {
        return repositories;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("blocklist")
    public BlocklistCoordinate getBlocklistCoordinate() {
        return blocklistCoordinate;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("manifest")
    public ChannelManifestCoordinate getManifestCoordinate() {
        return manifestCoordinate;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("resolve-if-no-stream")
    public NoStreamStrategy getNoStreamStrategy() {
        return noStreamStrategy;
    }

    /**
     * Strategies for resolving artifact versions if it is not listed in streams.
     * <ul>
     *    <li>LATEST - Use the latest version according to {@link VersionMatcher#COMPARATOR}</li>
     *    <li>ORIGINAL - Use the {@code baseVersion} if provided in the query</li>
     *    <li>MAVEN_LATEST - Use the value of {@code <latest>} in maven-metadata.xml</li>
     *    <li>MAVEN_RELEASE - Use the value of {@code <release>} in maven-metadata.xml</li>
     *    <li>NONE - throw {@link UnresolvedMavenArtifactException}</li>
     * </ul>
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum NoStreamStrategy {
        @JsonProperty("latest")
        LATEST,
        @JsonProperty("maven-latest")
        MAVEN_LATEST,
        @JsonProperty("maven-release")
        MAVEN_RELEASE,
        @JsonProperty("none")
        NONE
    }

    @Override
    public String toString() {
        return "Channel{" +
                "schemaVersion='" + schemaVersion + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", vendor=" + vendor +
                ", repositories=" + repositories +
                ", blocklistCoordinate=" + blocklistCoordinate +
                ", manifestCoordinate=" + manifestCoordinate +
                ", noStreamStrategy=" + noStreamStrategy +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Channel channel = (Channel) o;
        return Objects.equals(schemaVersion, channel.schemaVersion) && Objects.equals(name, channel.name) && Objects.equals(description, channel.description) && Objects.equals(vendor, channel.vendor) && Objects.equals(repositories, channel.repositories) && Objects.equals(blocklistCoordinate, channel.blocklistCoordinate) && Objects.equals(manifestCoordinate, channel.manifestCoordinate) && noStreamStrategy == channel.noStreamStrategy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, name, description, vendor, repositories, blocklistCoordinate, manifestCoordinate, noStreamStrategy);
    }

    /**
     * Builder for channel class
     */
    public static class Builder {
        private String name;
        private List<Repository> repositories = new ArrayList<>();
        private ChannelManifestCoordinate manifestCoordinate;
        private BlocklistCoordinate blocklistCoordinate;
        private NoStreamStrategy strategy;
        private String description;
        private Vendor vendor;

        public Builder() {
        }

        public Builder(Channel from) {
            this.name = from.getName();
            this.repositories = new ArrayList<>(from.getRepositories());
            this.manifestCoordinate = from.getManifestCoordinate();
            this.blocklistCoordinate = from.getBlocklistCoordinate();
            this.strategy = from.getNoStreamStrategy();
            this.description = from.getDescription();
            this.vendor = from.getVendor();
        }

        public Channel build() {
            return new Channel(name, description, vendor, repositories, manifestCoordinate, blocklistCoordinate, strategy);
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setRepositories(List<Repository> repositories) {
            this.repositories = repositories;
            return this;
        }

        public Builder setVendor(Vendor vendor) {
            this.vendor = vendor;
            return this;
        }

        public Builder addRepository(String repoId, String url) {
            repositories.add(new Repository(repoId, url));
            return this;
        }

        public Builder setManifestCoordinate(String groupId, String artifactId, String version) {
            this.manifestCoordinate = new ChannelManifestCoordinate(groupId, artifactId, version);
            return this;
        }

        public Builder setManifestCoordinate(String groupId, String artifactId) {
            this.manifestCoordinate = new ChannelManifestCoordinate(groupId, artifactId);
            return this;
        }

        public Builder setManifestUrl(URL url) {
            this.manifestCoordinate = new ChannelManifestCoordinate(url);
            return this;
        }

        public Builder setManifestCoordinate(ChannelManifestCoordinate coordinate) {
            this.manifestCoordinate = coordinate;
            return this;
        }

        public Builder setBlocklist(String groupId, String artifactId, String version) {
            if (version == null) {
                this.blocklistCoordinate = new BlocklistCoordinate(groupId, artifactId);
            } else {
                this.blocklistCoordinate = new BlocklistCoordinate(groupId, artifactId, version);
            }
            return this;
        }

        public Builder setBlocklistCoordinate(BlocklistCoordinate blocklistCoordinate) {
            this.blocklistCoordinate = blocklistCoordinate;
            return this;
        }

        public Builder setResolveStrategy(NoStreamStrategy strategy) {
            this.strategy = strategy;
            return this;
        }
    }
}
