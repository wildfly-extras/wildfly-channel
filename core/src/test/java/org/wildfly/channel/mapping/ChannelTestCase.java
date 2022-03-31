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
package org.wildfly.channel.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelRequirement;
import org.wildfly.channel.Stream;
import org.wildfly.channel.Vendor;

public class ChannelTestCase {

    @Test
    public void nonExistingChannelTest() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("this-channel-does-not-exist.yaml");
        Assertions.assertThrows(RuntimeException.class, () -> {
            ChannelMapper.from(file);
        });
    }

    @Test()
    public void emptyChannelTest() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/empty-channel.yaml");
        Assertions.assertThrows(RuntimeException.class, () -> {
            ChannelMapper.from(file);
        });
    }

    @Test
    public void simpleChannelTest() throws MalformedURLException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/simple-channel.yaml");

        Channel channel = ChannelMapper.from(file);

        assertEquals("My Channel", channel.getName());
        assertEquals("This is my channel\n" +
                "with my stuff", channel.getDescription());

        Vendor vendor = channel.getVendor();
        assertNotNull(vendor);
        assertEquals("My Vendor", vendor.getName());
        assertEquals(Vendor.Support.COMMUNITY, vendor.getSupport());

        Collection<ChannelRequirement> requires = channel.getChannelRequirements();
        assertEquals(0, requires.size());

        Collection<Stream> streams = channel.getStreams();
        assertEquals(1, streams.size());
        Stream stream = streams.iterator().next();
        assertEquals("org.wildfly", stream.getGroupId());
        assertEquals("wildfly-ee-galleon-pack", stream.getArtifactId());
        assertEquals("26.0.0.Final", stream.getVersion());
    }

    @Test
    public void channelWithoutStreams() {
        List<Channel> channels = ChannelMapper.fromString("name: My Channel\n" +
                "description: |-\n" +
                "  This is my channel\n" +
                "  with no stream");
        assertEquals(1, channels.size());
        Channel channel = channels.get(0);

        assertTrue(channel.getStreams().isEmpty());
    }

    @Test
    public void channelWithRequires() {
        List<Channel> channels = ChannelMapper.fromString("name: My Channel\n" +
                "description: |-\n" +
                "  This is my channel\n" +
                "  with no stream\n" +
                "requires:\n" +
                "  - channel: org.foo.channels:my-required-channel");
        assertEquals(1, channels.size());
        Channel channel = channels.get(0);

        assertEquals(1, channel.getChannelRequirements().size());
        ChannelRequirement requirement = channel.getChannelRequirements().get(0);
        assertEquals("org.foo.channels:my-required-channel", requirement.getChannelCoordinate());
    }
}
