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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.wildfly.channel.ChannelMapper.CURRENT_SCHEMA_VERSION;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class ChannelMapperTestCase {

    @Test
    public void testWriteReadChannel() throws Exception {
        final Channel channel = new Channel("test_name", "test_desc", new Vendor("test_vendor_name", Vendor.Support.COMMUNITY), Collections.emptyList(), Collections.emptyList());
        final String yaml = ChannelMapper.toYaml(channel);

        final Channel channel1 = ChannelMapper.fromString(yaml).get(0);
        assertEquals(Vendor.Support.COMMUNITY, channel1.getVendor().getSupport());
    }

    @Test
    public void testWriteMultipleChannels() throws Exception {
        final ChannelRequirement req = new ChannelRequirement("org", "foo", "1.2.3");
        final Stream stream1 = new Stream("org.bar", "example", "1.2.3");
        final Stream stream2 = new Stream("org.bar", "other-example", Pattern.compile("\\.*"));
        final Channel channel1 = new Channel("test_name_1", "test_desc", new Vendor("test_vendor_name", Vendor.Support.COMMUNITY), Arrays.asList(req), Arrays.asList(stream1, stream2));
        final Channel channel2 = new Channel("test_name_2", "test_desc", new Vendor("test_vendor_name", Vendor.Support.COMMUNITY), Collections.emptyList(), Collections.emptyList());
        final String yaml = ChannelMapper.toYaml(channel1, channel2);

        System.out.println(yaml);
        List<Channel> channels = ChannelMapper.fromString(yaml);
        assertEquals(2, channels.size());
        final Channel c1 = channels.get(0);
        assertEquals(channel1.getName(), c1.getName());
        assertEquals(1, c1.getChannelRequirements().size());
        assertEquals("foo", c1.getChannelRequirements().get(0).getArtifactId());
        final Channel c2 = channels.get(1);
        assertEquals(channel2.getName(), c2.getName());
    }

    @Test
    public void testReadChannelWithUnknownProperties() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/channel-with-unknown-properties.yaml");

        Channel channel = ChannelMapper.from(file);
        assertNotNull(channel);
    }
}
