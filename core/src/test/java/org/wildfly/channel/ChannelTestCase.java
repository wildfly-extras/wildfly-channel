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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}
