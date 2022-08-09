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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.Stream;

public class StreamTestCase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

    static Stream fromYamlContent(String str) throws IOException {
        return OBJECT_MAPPER.readValue(str, Stream.class);
    }

    static Stream fromURL(URL url) throws IOException {
        return OBJECT_MAPPER.readValue(url, Stream.class);
    }

    @Test
    public void testValidStream() throws IOException {
        Stream stream = fromYamlContent("groupId: org.wildfly\n" +
                "artifactId: wildfly-ee-galleon-pack\n" +
                "version: 26.0.0.Final");
        assertEquals("org.wildfly", stream.getGroupId());
        assertEquals("wildfly-ee-galleon-pack", stream.getArtifactId());
        assertEquals("26.0.0.Final", stream.getVersion());
        assertNull(stream.getVersionPattern());

        System.out.println("stream = " + stream);
    }

    @Test
    public void testValidStreamWithVersionPattern() throws IOException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("streams/stream-with-versionPattern.yaml");
        Stream stream = fromURL(file);
        assertEquals("org.example", stream.getGroupId());
        assertEquals("foo", stream.getArtifactId());
        assertNull(stream.getVersion());
        assertEquals("2\\.2\\..*", stream.getVersionPattern().pattern());
    }

    @Test
    public void testAnyGroupIdIsNotValid() throws IOException {
        assertThrows(Exception.class, () -> {

            fromYamlContent("groupId: '*'\n" +
                    "artifactId: '*'\n" +
                    "version: 1.1.1.Final");
        });
    }

    @Test
    public void testAnyArtifactIdStream() throws IOException {
        Stream stream = fromYamlContent("groupId: org.wildfly\n" +
                "artifactId: '*'\n" +
                "version: 1.2.0.Final");
        assertEquals("org.wildfly", stream.getGroupId());
        assertEquals("*", stream.getArtifactId());
    }

    @Test
    public void testGroupIdIsMandatory() {
        assertThrows(Exception.class, () -> {
            fromYamlContent("artifactId: wildfly-ee-galleon-pack\n" +
                    "version: 26.0.0.Final");
        });
    }

    @Test
    public void testArtifactIdIsMandatory() {
        assertThrows(Exception.class, () -> {
            fromYamlContent("groupId: org.wildfly\n" +
                    "version: 26.0.0.Final");
        });
    }

    @Test
    public void testMissingVersionAndVersionPattern()  {
        assertThrows(Exception.class, () -> {
            Stream stream = fromYamlContent("groupId: org.wildfly\n" +
                    "artifactId: wildfly-ee-galleon-pack");
        });
    }

    @Test
    public void testVersionAndVersionPatternAreBothDefined()  {
        assertThrows(Exception.class, () -> {
            Stream stream = fromYamlContent("groupId: org.wildfly\n" +
                    "artifactId: wildfly-ee-galleon-pack\n" +
                    "version: 26.0.0.Final\n" +
                    "versionPattern: \"2\\\\.2\\\\..*\"");
        });
    }

    @Test
    public void testVersionAndExcludedVersionsAreBothDefined() throws Exception {
        assertThrows(Exception.class, () -> {
            Stream stream = fromYamlContent("groupId: org.wildfly\n" +
                    "artifactId: wildfly-ee-galleon-pack\n" +
                    "version: 26.0.0.Final\n" +
                    "excludedVersions: [ 26.0.0.Final ]");
        });
    }
}
