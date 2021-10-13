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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.spi.AbstractMavenVersionsResolver;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelSessionTestCase {

    @Test
    public void testSession() {
        List<Channel> channels = ChannelMapper.channelsFromString("---\n" +
                "id: wildfly-24\n" +
                "repositories:\n" +
                "  - id: repo-wildfly-24\n" +
                "    url: https://repo1.maven.org/maven2/\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '24\\.\\d+\\.\\d+.Final'\n" +
                "---\n" +
                "id: wildfly-25\n" +
                "repositories:\n" +
                "  - id: repo-wildfly-25\n" +
                "    url: https://repo1.maven.org/maven2/\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'");
        Assertions.assertNotNull(channels);
        Assertions.assertEquals(2, channels.size());

        ChannelSession session = new ChannelSession(channels,
                // dummy maven resolver that returns the version based on the id of the maven repositories
                new MavenVersionsResolver.Factory<MavenVersionsResolver>() {
                    @Override
                    public MavenVersionsResolver create(List<MavenRepository> mavenRepositories, boolean resolveLocalCache) {
                        return new AbstractMavenVersionsResolver(mavenRepositories, resolveLocalCache) {
                            @Override
                            public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier) {
                                if ("repo-wildfly-24".equals(mavenRepositories.get(0).getId())) {
                                    return singleton("24.0.0.Final");
                                } else {
                                    return singleton("25.0.0.Final");
                                }
                            }
                        };
                    }
                });

        Optional<ChannelSession.Result> found = session.getLatestVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null);
        assertTrue(found.isPresent());
        assertEquals("25.0.0.Final", found.get().getVersion());
    }
}
