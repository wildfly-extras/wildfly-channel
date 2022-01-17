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
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.MavenRepository;
import org.wildfly.channel.Stream;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class VersionMatcherTestCase {
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

        SimpleResolverFactory factory = new SimpleResolverFactory();
        MavenVersionsResolver resolver = factory.create(mavenRepos, false);

        Stream stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "version: 1.0.1.Final");
        Set<String> versions = resolver.getAllVersions("org.example.foo", "foo-bar", null, null);
        Optional<String> foundVersion = stream.getVersionComparator().matches(versions);
        assertTrue(foundVersion.isPresent());
        assertEquals(foundVersion.get(), "1.0.1.Final");

        stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "version: 1.3.0.Final");
        versions = resolver.getAllVersions("org.example.foo", "foo-bar", null, null);
        foundVersion = stream.getVersionComparator().matches(versions);
        assertFalse(foundVersion.isPresent());
    }

    @Test
    public void resolveStreamWithWildCard() throws IOException {
        List<MavenRepository> mavenRepos = new ArrayList<>();
        mavenRepos.add(mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("maven-repo1").toUri()));

        SimpleResolverFactory factory = new SimpleResolverFactory();
        MavenVersionsResolver resolver = factory.create(mavenRepos, false);

        Stream stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: '*'\n" +
                "version: 1.0.1.Final");

        Set<String> versions = resolver.getAllVersions("org.example.foo", "foo-bar", null, null);
        Optional<String> foundVersion = stream.getVersionComparator().matches(versions);
        assertTrue(foundVersion.isPresent());
        assertEquals(foundVersion.get(), stream.getVersion());
    }

    @Test
    public void resolveStreamWithManyVersions() throws IOException {
        List<MavenRepository> mavenRepos = new ArrayList<>();
        mavenRepos.add(mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("maven-repo1").toUri()));

        SimpleResolverFactory factory = new SimpleResolverFactory();
        MavenVersionsResolver resolver = factory.create(mavenRepos, false);

        Stream stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "version: 1.0.0.Final, 1.0.1.Final");

        Set<String> versions = resolver.getAllVersions("org.example.foo", "foo-bar", null, null);
        Optional<String> foundVersion = stream.getVersionComparator().matches(versions);
        assertTrue(foundVersion.isPresent());
        assertEquals("1.0.1.Final", foundVersion.get());

        // order of the versions does not matter
        stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "version: 1.0.0.Final, 1.1.1.Final, 1.0.1.Final");

        versions = resolver.getAllVersions("org.example.foo", "foo-bar", null, null);
        foundVersion = stream.getVersionComparator().matches(versions);
        assertTrue(foundVersion.isPresent());
        assertEquals("1.1.1.Final", foundVersion.get());
    }

    @Test
    public void resolveStreamWithVersionPattern() throws IOException {
        List<MavenRepository> mavenRepos = new ArrayList<>();
        mavenRepos.add(mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("maven-repo1").toUri()));

        SimpleResolverFactory factory = new SimpleResolverFactory();
        MavenVersionsResolver resolver = factory.create(mavenRepos, false);

        Stream stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "versionPattern: '1\\.0\\..*'");
        Set<String> versions = resolver.getAllVersions("org.example.foo", "foo-bar", null, null);
        Optional<String> foundVersion = stream.getVersionComparator().matches(versions);
        assertTrue(foundVersion.isPresent());
        assertEquals( "1.0.1.Final", foundVersion.get());
    }

    @Test
    public void resolveStreamWithVersionPattern_2() throws IOException {
        List<MavenRepository> mavenRepos = new ArrayList<>();
        mavenRepos.add(mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("maven-repo1").toUri()));

        SimpleResolverFactory factory = new SimpleResolverFactory();
        MavenVersionsResolver resolver = factory.create(mavenRepos, false);

        Stream stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "versionPattern: '1\\.*\\..*'");
        Set<String> versions = resolver.getAllVersions("org.example.foo", "foo-bar", null, null);
        Optional<String> foundVersion = stream.getVersionComparator().matches(versions);
        assertEquals( "1.1.1.Final", foundVersion.get());
    }

    @Test
    public void resolveStreamWithLocalCache() throws IOException {
        List<MavenRepository> mavenRepos = new ArrayList<>();
        mavenRepos.add(mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("maven-repo1").toUri()));

        SimpleResolverFactory factory = new SimpleResolverFactory();
        MavenVersionsResolver resolver = factory.create(mavenRepos, true);

        Stream stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "versionPattern: '1\\.1\\..*'");
        Set<String> versions = resolver.getAllVersions("org.example.foo", "foo-bar", null, null);
        Optional<String> foundVersion = stream.getVersionComparator().matches(versions);
        assertEquals( "1.1.2.Final-SNAPSHOT", foundVersion.get());

        stream = streamFromYaml("groupId: org.example.foo\n" +
                "artifactId: foo-bar\n" +
                "versionPattern: '2\\.0\\..*'");
        versions = resolver.getAllVersions("org.example.foo", "foo-bar", null, null);
        foundVersion = stream.getVersionComparator().matches(versions);
        assertTrue(foundVersion.isPresent());
        assertEquals( "2.0.0.Final", foundVersion.get());
    }

    @Test
    public void version10isLargerThen1() {
        assertTrue(VersionMatcher.COMPARATOR.compare("1.0", "10.0") < 0);
        assertTrue(VersionMatcher.COMPARATOR.compare("1.1", "1.10") < 0);
    }

}
