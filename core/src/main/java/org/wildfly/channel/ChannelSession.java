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

import static org.wildfly.channel.version.VersionMatcher.COMPARATOR;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.wildfly.channel.spi.MavenResolverBuilder;
import org.wildfly.channel.spi.MavenVersionResolver;

public class ChannelSession<T extends MavenVersionResolver> {
    private List<Channel> channels;
    private MavenResolverBuilder<T> builder;

    public ChannelSession(List<Channel> channels, MavenResolverBuilder<T> builder) {
        Objects.requireNonNull(channels);
        Objects.requireNonNull(builder);
        this.channels = channels;
        this.builder = builder;
    }

    public Optional<Result<T>> getLatestVersion(String groupId, String artifactId, String extension, String classifier) {
        Objects.requireNonNull(groupId);
        Objects.requireNonNull(artifactId);

        // find all latest versions from the different channels;
        Set<Result<T>> found = new HashSet<>();
        for (Channel channel : channels) {
            Optional<Result<T>> result = channel.resolveLatestVersion(groupId, artifactId, extension, classifier, builder);
            if (result.isPresent()) {
                found.add(result.get());
            }
        }
        // compare all latest version from the channels to find the latest overall
        return found.stream()
                .sorted((lvr1, lvr2) -> COMPARATOR.reversed().compare(lvr1.version, lvr2.version))
                .findFirst();
    }

    public static class Result<T> {
        String version;
        T resolver;

        Result(String version, T resolver) {
            this.version = version;
            this.resolver = resolver;
        }

        /**
         * The latest version.
         */
        public String getVersion() {
            return version;
        }

        /**
         * The MavenVersionResolver that found the latest version.
         */
        public T getResolver() {
            return resolver;
        }
    }
}
