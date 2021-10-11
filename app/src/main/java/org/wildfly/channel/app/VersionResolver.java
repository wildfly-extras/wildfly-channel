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
package org.wildfly.channel.app;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.MavenRepository;
import org.wildfly.channel.spi.MavenVersionResolver;
import org.wildfly.channel.version.VersionComparator;

@Path("/latest")
public class VersionResolver {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public String getLatestVersion(@FormParam("channels") String yamlChannels,
                                   @FormParam("groupId") String groupId,
                                   @FormParam("artifactId") String artifactId) {
        requireNonNull(groupId);
        requireNonNull(artifactId);

        MavenVersionResolver resolver = createMavenResolver();
        List<Channel> channels = ChannelMapper.channelsFromString(yamlChannels);

        Optional<String> foundVersion = Optional.empty();
        for (Channel channel : channels) {
            Optional<String> found = channel.resolveLatestVersion(groupId, artifactId, resolver);
            if (found.isPresent()) {
                foundVersion = found;
                break;
            }
        }

        if (foundVersion.isPresent()) {
            return groupId + ":" + artifactId + ":" + foundVersion.get();
        } else {
            return "N/A";
        }
    }

    private MavenVersionResolver createMavenResolver() {
        return new MavenVersionResolver() {
            @Override
            public Optional<String> resolve(String groupId, String artifactId, List<MavenRepository> mavenRepositories, boolean resolveLocalCache, VersionComparator versionComparator) {
                return Optional.of("1.0.0.Final");
            }
        };
    }


}
