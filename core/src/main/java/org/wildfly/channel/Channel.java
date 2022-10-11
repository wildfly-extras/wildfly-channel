package org.wildfly.channel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.wildfly.channel.version.VersionMatcher;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import java.util.ArrayList;
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
    public BlocklistCoordinate getBlocklistCoordinate() {
        return blocklistCoordinate;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ChannelManifestCoordinate getManifestCoordinate() {
        return manifestCoordinate;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
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
}
