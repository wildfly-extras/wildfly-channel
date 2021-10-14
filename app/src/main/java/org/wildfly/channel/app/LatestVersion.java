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

import java.io.File;
import java.nio.file.Paths;
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
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.MavenVersionsResolver;

@Path("/latest")
public class LatestVersion {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public String getLatestVersion(@FormParam("channels") String yamlChannels,
                                   @FormParam("groupId") String groupId,
                                   @FormParam("artifactId") String artifactId,
                                   @FormParam("extension") String extension,
                                   @FormParam("baseVersion") String baseVersion) {
        requireNonNull(groupId);
        requireNonNull(artifactId);

        try {
            // must be provided by client of this library
            MavenVersionsResolver.Factory factory = new SimpleMavenVersionResolverFactory();

            List<Channel> channels = ChannelMapper.channelsFromString(yamlChannels);
            ChannelSession session = new ChannelSession(channels, factory);

            try {
                MavenArtifact artifact = session.resolveMavenArtifact(groupId, artifactId, extension, null, baseVersion);
                java.nio.file.Path localRepo = Paths.get(new File("target/local-repo").toURI());
                java.nio.file.Path artifactPath = localRepo.relativize(Paths.get(artifact.getFile().toURI()));

                return String.format("GAV: %s:%s:%s:%s\nto File: \n%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                        artifact.getVersion(), artifactPath);
            } catch (UnresolvedMavenArtifactException e) {
                return "Unresolved: " + e.getMessage();
            }
        } catch (Throwable t) {
            return "Err: " + t.getMessage();
        }

    }
}
