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
package org.wildfly.channel.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;

import org.junit.jupiter.api.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;

/**
 * Test class to be able to read channels from released schemas
 */
public class SchemaTestCase {

    @Test
    public void testReadChannel_1_0_0() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("versions/channel-1.0.0.yaml");
        Channel channel = ChannelMapper.from(file);
        assertNotNull(channel);
        assertEquals(ChannelMapper.SCHEMA_VERSION_1_0_0, channel.getSchemaVersion());
    }

    @Test
    public void testReadChannelWithoutSchema() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("versions/channel-without-schema.yaml");
        assertThrows(Exception.class, () -> {
            ChannelMapper.from(file);
        });
    }
}
