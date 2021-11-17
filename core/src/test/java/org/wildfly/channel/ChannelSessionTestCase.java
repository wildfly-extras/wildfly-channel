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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelSessionTestCase {

    @Test
    public void testSession() throws UnresolvedMavenArtifactException {
        List<Channel> channels = ChannelMapper.fromString("---\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '24\\.\\d+\\.\\d+.Final'\n" +
                "---\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'");
        Assertions.assertNotNull(channels);
        Assertions.assertEquals(2, channels.size());

        ChannelSession session = new ChannelSession(channels,
                // dummy maven resolver that returns the version based on the id of the maven repositories
                new MavenVersionsResolver.Factory() {
                    @Override
                    public MavenVersionsResolver create() {
                        return new MavenVersionsResolver() {
                            @Override
                            public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier) {
                                    return singleton("25.0.0.Final");
                            }

                            @Override
                            public File resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) {
                                return new File("/tmp");
                            }

                            @Override
                            public File resolveLatestVersionFromMavenMetadata(String groupId, String artifactId, String extension, String classifier) throws UnresolvedMavenArtifactException {
                                return new File("/tmp");
                            }
                        };
                    }
                });


        MavenArtifact artifact = session.resolveLatestMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null);
        assertNotNull(artifact);
        assertEquals("25.0.0.Final", artifact.getVersion());
    }
}
