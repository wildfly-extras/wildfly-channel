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
package org.wildfly.channel.mapping;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.Stream;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wildfly.channel.ChannelManifestMapper.CURRENT_SCHEMA_VERSION;

public class ChannelManifestTestCase {

    @Test
    public void nonExistingManifestTest() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("this-manifest-does-not-exist.yaml");
        Assertions.assertThrows(RuntimeException.class, () -> {
            ChannelManifestMapper.from(file);
        });
    }

    @Test()
    public void emptyManifestTest() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/empty-channel.yaml");
        Assertions.assertThrows(RuntimeException.class, () -> {
            ChannelManifestMapper.from(file);
        });
    }

    @Test()
    public void multipleManifestsTest() throws IOException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/multiple-manifests.yaml");

        try (InputStream in = file.openStream())
        {
            byte[] bytes = in.readAllBytes();
            String content = new String(bytes, Charset.defaultCharset());
            ChannelManifest manifest = ChannelManifestMapper.fromString(content);
            assertEquals("Channel for WildFly 27", manifest.getName());
        }
    }

    @Test
    public void simpleManifestTest() throws MalformedURLException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/simple-manifest.yaml");

        ChannelManifest manifest = ChannelManifestMapper.from(file);

        assertEquals("My Channel", manifest.getName());
        assertEquals("This is my manifest\n" +
                "with my stuff", manifest.getDescription());

        Collection<Stream> streams = manifest.getStreams();
        assertEquals(1, streams.size());
        Stream stream = streams.iterator().next();
        assertEquals("org.wildfly", stream.getGroupId());
        assertEquals("wildfly-ee-galleon-pack", stream.getArtifactId());
        assertEquals("26.0.0.Final", stream.getVersion());
    }

    @Test
    public void manifestWithoutStreams() {
        ChannelManifest manifest = ChannelManifestMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "name: My Channel\n" +
                "description: |-\n" +
                "  This is my manifest\n" +
                "  with no stream");

        assertTrue(manifest.getStreams().isEmpty());
    }
}
