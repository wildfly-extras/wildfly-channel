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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelWithRequirementsTestCase {

    @Test
    public void testChannelWithSingleChannelRequirement() throws URISyntaxException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL requiredChannel = tccl.getResource("channels/required-channel.yaml");
        Channel channel = ChannelMapper.fromString("id: my-channel\n" +
                "name: My Channel\n" +
                "requires:\n" +
                "  - url: " + requiredChannel.toURI());

        assertEquals(1, channel.getChannelRequirements().size());

        ChannelSession<MavenVersionsResolver> session = new ChannelSession(Collections.singletonList(channel), new MavenVersionsResolver.Factory() {
            @Override
            public MavenVersionsResolver create(List list) {
                return new MavenVersionsResolver() {
                    @Override
                    public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier, boolean resolveLocalCache) {
                        return Collections.singleton("1.2.0.Final");
                    }
                };
            }
        });

        Optional < ChannelSession.Result <MavenVersionsResolver>> result = session.getLatestVersion("org.example", "foo-bar", null, null);
        assertTrue(result.isPresent());
        assertEquals("1.2.0.Final", result.get().version);
    }
}
