/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.channel;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelRecorderTestCase {
    @Test
    public void testChannelRecorder() throws IOException, UnresolvedMavenArtifactException {

        List<Channel> channels = ChannelMapper.channelsFromString("---\n" +
                "id: channel1\n" +
                "repositories:\n" +
                "  - id: repo-channel1\n" +
                "    url: https://repo1.maven.org/maven2/\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '24\\.\\d+\\.\\d+.Final'\n" +
                "  - groupId: io.undertow\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '2\\.\\1\\.\\d+.Final'\n" +
                "---\n" +
                "id: channel2\n" +
                "repositories:\n" +
                "  - id: repo-channel2\n" +
                "    url: https://repo1.maven.org/maven2/\n" +
                "streams:\n" +
                "  - groupId: io.undertow\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '2\\.\\d+\\.\\d+.Final'");
        Assertions.assertNotNull(channels);
        assertEquals(2, channels.size());

        ChannelSession session = new ChannelSession(channels,
                // dummy maven resolver that returns the version based on the id of the maven repositories
                new MavenVersionsResolver.Factory() {
                    @Override
                    public MavenVersionsResolver create(List<MavenRepository> mavenRepositories, boolean resolveLocalCache) {
                        return new MavenVersionsResolver() {
                            @Override
                            public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier) {
                                if ("repo-channel1".equals(mavenRepositories.get(0).getId())) {
                                    if (groupId.equals("org.wildfly")) {
                                        return singleton("24.0.0.Final");
                                    } else {
                                        return singleton("2.1.2.Final");
                                    }
                                } else {
                                    return singleton("2.2.0.Final");
                                }
                            }

                            @Override
                            public File resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) {
                                return new File("/tmp");
                            }
                        };
                    }
                });

        session.resolveMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, null);
        session.resolveMavenArtifact("org.wildfly.core", "wildfly.core.cli", null, null, "24.0.0.Final");
        session.resolveMavenArtifact("io.undertow", "undertow-core", null, null, "2.1.2.Final");
        session.resolveMavenArtifact("io.undertow", "undertow-servlet", null, null, "2.1.2.Final");

        List<Channel> recordedChannels = session.getRecordedChannels();
        System.out.println(ChannelMapper.toYaml(recordedChannels));
        assertEquals(1, recordedChannels.size());


        Channel channel = recordedChannels.get(0);

        List<MavenRepository> repositories = channel.getRepositories();
        assertEquals(1, repositories.size());
        assertEquals("https://repo1.maven.org/maven2/", repositories.get(0).getUrl().toString());

        Collection<Stream> streams = channel.getStreams();
        assertEquals(4, streams.size());

        assertTrue(streams.stream().anyMatch(s -> s.getGroupId().equals("org.wildfly") &&
                s.getArtifactId().equals("wildfly-ee-galleon-pack") &&
                s.getVersion().equals("24.0.0.Final")));
        assertTrue(streams.stream().anyMatch(s -> s.getGroupId().equals("org.wildfly.core") &&
                s.getArtifactId().equals("wildfly.core.cli") &&
                s.getVersion().equals("24.0.0.Final")));
        assertTrue(streams.stream().anyMatch(s -> s.getGroupId().equals("io.undertow") &&
                s.getArtifactId().equals("undertow-core") &&
                s.getVersion().equals("2.2.0.Final")));
        assertTrue(streams.stream().anyMatch(s -> s.getGroupId().equals("io.undertow") &&
                s.getArtifactId().equals("undertow-servlet") &&
                s.getVersion().equals("2.2.0.Final")));
    }
}
