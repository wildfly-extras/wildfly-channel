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

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ChannelManifestMapperTestCase {

    @Test
    public void testWriteReadChannel() throws Exception {
        final ChannelManifest manifest = new ChannelManifest("test_name", null, "test_desc", Collections.emptyList());
        final String yaml = ChannelManifestMapper.toYaml(manifest);

        final ChannelManifest manifest1 = ChannelManifestMapper.fromString(yaml);
        assertEquals("test_desc", manifest1.getDescription());
    }

    @Test
    public void testWriteMultipleChannels() throws Exception {
        final Stream stream1 = new Stream("org.bar", "example", "1.2.3");
        final Stream stream2 = new Stream("org.bar", "other-example", Pattern.compile("\\.*"));
        final ChannelManifest manifest1 = new ChannelManifest("test_name_1", null, "test_desc", Arrays.asList(stream1, stream2));
        final ChannelManifest manifest2 = new ChannelManifest("test_name_2", null, "test_desc", Collections.emptyList());
        final String yaml1 = ChannelManifestMapper.toYaml(manifest1);
        final String yaml2 = ChannelManifestMapper.toYaml(manifest2);

        System.out.println(yaml1);
        System.out.println(yaml2);

        final ChannelManifest m1 = ChannelManifestMapper.fromString(yaml1);
        assertEquals(manifest1.getName(), m1.getName());
        assertEquals(2, m1.getStreams().size());
        assertEquals("example", m1.getStreams().stream().findFirst().get().getArtifactId());
        final ChannelManifest m2 = ChannelManifestMapper.fromString(yaml2);
        assertEquals(manifest2.getName(), m2.getName());
        assertEquals(0, m2.getStreams().size());
    }

    @Test
    public void testReadChannelWithUnknownProperties() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/manifest-with-unknown-properties.yaml");

        Channel channel = ChannelMapper.from(file);
        assertNotNull(channel);
    }

    @Test
    public void testReadChannelWithUnknownMicroVersionFallsBackToLatestParser() {
        String yaml = "schemaVersion: 1.0.99999";

        ChannelManifest manifest = ChannelManifestMapper.fromString(yaml);
        assertNotNull(manifest);
    }

    @Test
    public void testReadChannelWithUnknownMinorVersionReturnsError() {
        String yaml = "schemaVersion: 1.999999999.0";

        assertThrows(InvalidChannelMetadataException.class, ()->ChannelManifestMapper.fromString(yaml));
    }

    @Test
    public void testWriteRequires() throws Exception {
        final ChannelManifest manifest = new ManifestBuilder()
                .setId("test-id")
                .addRequires("required-id", "org.test", "required", "1.0.0")
                .build();

        final String yaml1 = ChannelManifestMapper.toYaml(manifest);
        System.out.println(yaml1);
        final ChannelManifest m1 = ChannelManifestMapper.fromString(yaml1);

        assertEquals("test-id", m1.getId());
        assertEquals("required-id", m1.getManifestRequirements().get(0).getId());
        assertEquals("org.test", m1.getManifestRequirements().get(0).getGroupId());
        assertEquals("required", m1.getManifestRequirements().get(0).getArtifactId());
        assertEquals("1.0.0", m1.getManifestRequirements().get(0).getVersion());
    }
}
