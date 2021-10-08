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
package org.wildfly.channel.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.MavenRepository;
import org.wildfly.channel.Stream;
import org.wildfly.channel.spi.MavenVersionResolver;

public class VersionResolutionTestCase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

    static Stream streamFromYaml(String yamlContent) throws IOException {
        return OBJECT_MAPPER.readValue(yamlContent, Stream.class);
    }

    static MavenRepository mavenRepositoryFromYaml(String yamlContent) throws IOException {
        return OBJECT_MAPPER.readValue(yamlContent, MavenRepository.class);
    }

    static Path getTestMavenRepositoryURI(String name) {
        return Paths.get("src","test","resources", "maven-repositories", name);
    }

    private static SimplisticMavenRepoManager getTestMavenRepositoryManager(String name) {
        return SimplisticMavenRepoManager.getInstance(getTestMavenRepositoryURI(name));
    }

    @Test
    public void testGetAllVersions() {
        SimplisticMavenRepoManager mavenRepoManager = getTestMavenRepositoryManager("maven-repo1");

        List<String> versions = mavenRepoManager.getAllVersions("org.example.foo", "foo-bar");
        assertEquals(5, versions.size());
        assertTrue(versions.contains("1.0.0.Final"));
        assertTrue(versions.contains("1.0.1.Final"));
        assertTrue(versions.contains("1.1.0.Final"));
        assertTrue(versions.contains("1.1.1.Final"));
        assertTrue(versions.contains("2.0.0.Final"));

        versions = mavenRepoManager.getAllVersions("org.example.foo.foo-bar", "baz");
        assertEquals(1, versions.size());
        assertTrue(versions.contains("1.0.0.Final"));

        versions = mavenRepoManager.getAllVersions("org.example.foo", "unknown-artifact");
        assertEquals(0, versions.size());
    }

    @Test
    public void resolveStreamWithSingleVersion() throws IOException {
        List<MavenRepository> mavenRepos = new ArrayList<>();
        mavenRepos.add(mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("maven-repo1").toUri()));

        MavenVersionResolver resolver = new SimpleVersionResolver();

        Stream stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "version: 1.0.1.Final");
        Optional<String> foundVersion = resolver.resolve("org.example.foo", "foo-bar", mavenRepos, stream.isResolveWithLocalCache(), stream.getVersionComparator());
        assertTrue(foundVersion.isPresent());
        assertEquals(foundVersion.get(), "1.0.1.Final");

        stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "version: 1.3.0.Final");
        foundVersion = resolver.resolve("org.example.foo", "foo-bar", mavenRepos, stream.isResolveWithLocalCache(), stream.getVersionComparator());
        assertFalse(foundVersion.isPresent());
    }

    @Test
    public void resolveStreamWithWildCard() throws IOException {
        List<MavenRepository> mavenRepos = new ArrayList<>();
        mavenRepos.add(mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("maven-repo1").toUri()));

        MavenVersionResolver resolver = new SimpleVersionResolver();

        Stream stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: '*'\n" +
                "version: 1.0.1.Final");

        Optional<String> foundVersion = resolver.resolve("org.example.foo", "foo-bar", mavenRepos, stream.isResolveWithLocalCache(), stream.getVersionComparator());
        assertTrue(foundVersion.isPresent());
        assertEquals(foundVersion.get(), stream.getVersion());
    }

    @Test
    public void resolveStreamWithManyVersions() throws IOException {
        List<MavenRepository> mavenRepos = new ArrayList<>();
        mavenRepos.add(mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("maven-repo1").toUri()));

        MavenVersionResolver resolver = new SimpleVersionResolver();

        Stream stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "version: 1.0.0.Final, 1.0.1.Final");

        Optional<String> foundVersion = resolver.resolve("org.example.foo", "foo-bar", mavenRepos, stream.isResolveWithLocalCache(), stream.getVersionComparator());
        assertTrue(foundVersion.isPresent());
        assertEquals("1.0.1.Final", foundVersion.get());

        // order of the versions does not matter
        stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "version: 1.0.0.Final, 1.1.1.Final, 1.0.1.Final");

        foundVersion = resolver.resolve("org.example.foo", "foo-bar", mavenRepos, stream.isResolveWithLocalCache(), stream.getVersionComparator());
        assertTrue(foundVersion.isPresent());
        assertEquals("1.1.1.Final", foundVersion.get());
    }

    @Test
    public void resolveStreamWithVersionPattern() throws IOException {
        List<MavenRepository> mavenRepos = new ArrayList<>();
        mavenRepos.add(mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("maven-repo1").toUri()));

        MavenVersionResolver resolver = new SimpleVersionResolver();

        Stream stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "version-pattern: '1\\.0\\..*'");
        Optional<String> foundVersion = resolver.resolve("org.example.foo", "foo-bar", mavenRepos, stream.isResolveWithLocalCache(), stream.getVersionComparator());
        assertTrue(foundVersion.isPresent());
        assertEquals( "1.0.1.Final", foundVersion.get());
    }

    @Test
    public void resolveStreamWithVersionPattern_2() throws IOException {
        List<MavenRepository> mavenRepos = new ArrayList<>();
        mavenRepos.add(mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("maven-repo1").toUri()));

        MavenVersionResolver resolver = new SimpleVersionResolver();

        Stream stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "version-pattern: '1\\.*\\..*'");
        Optional<String> foundVersion = resolver.resolve("org.example.foo", "foo-bar", mavenRepos, stream.isResolveWithLocalCache(), stream.getVersionComparator());
        assertTrue(foundVersion.isPresent());
        assertEquals( "1.1.1.Final", foundVersion.get());
    }

    @Test
    public void resolveStreamWithLocalCache() throws IOException {
        List<MavenRepository> mavenRepos = new ArrayList<>();
        mavenRepos.add(mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("maven-repo1").toUri()));

        MavenVersionResolver resolver = new SimpleVersionResolver();

        Stream stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "version-pattern: '1\\.1\\..*'\n" +
                "resolve-with-local-cache: true");
        Optional<String> foundVersion = resolver.resolve("org.example.foo", "foo-bar", mavenRepos, stream.isResolveWithLocalCache(), stream.getVersionComparator());
        assertTrue(foundVersion.isPresent());
        assertEquals( "1.1.2.Final-SNAPSHOT", foundVersion.get());

        stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "version-pattern: '2\\.0\\..*'\n" +
                "resolve-with-local-cache: true");
        foundVersion = resolver.resolve("org.example.foo", "foo-bar", mavenRepos, stream.isResolveWithLocalCache(), stream.getVersionComparator());
        assertTrue(foundVersion.isPresent());
        assertEquals( "2.0.0.Final", foundVersion.get());
    }

}
