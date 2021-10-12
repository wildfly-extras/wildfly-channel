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
import org.wildfly.channel.spi.MavenVersionsResolver;

@Path("/latest")
public class LatestVersion {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public String getLatestVersion(@FormParam("channels") String yamlChannels,
                                   @FormParam("groupId") String groupId,
                                   @FormParam("artifactId") String artifactId) {
        requireNonNull(groupId);
        requireNonNull(artifactId);

        try {

            // must be provided by client of this library
            MavenVersionsResolver.Factory factory = new SimpleMavenVersionResolverFactory();

            List<Channel> channels = ChannelMapper.channelsFromString(yamlChannels);
            ChannelSession<SimpleMavenVersionsResolver> session = new ChannelSession<>(channels, factory);

            Optional<ChannelSession.Result<SimpleMavenVersionsResolver>> result = session.getLatestVersion(groupId, artifactId, null, null);
            if (result.isPresent()) {
                String gav =  groupId + ":" + artifactId + ":" + result.get().getVersion();

                // here, we could have a MavenVersionResolver that does the actual resolution of the file corresponding to the gav
                SimpleMavenVersionsResolver resolver = result.get().getResolver();
                resolver.install(gav);

                return gav;
            } else {
                return "N/A";
            }
        } catch (Throwable t) {
            return "Err: " + t.getMessage();
        }

    }
}
