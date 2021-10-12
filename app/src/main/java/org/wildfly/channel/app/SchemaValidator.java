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

import java.io.IOException;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.InvalidChannelException;

@Path("/")
public class SchemaValidator {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/validate")
    public String validate(@FormParam("channels") String yamlChannels) throws IOException {
        try {
            ChannelMapper.channelsFromString(yamlChannels);
        } catch (InvalidChannelException e) {
            return e.getValidationMessages().stream().collect(Collectors.joining("\n"));
        } catch (Throwable t) {
            return t.getLocalizedMessage();
        }
        return "Validation is OK";
    }

    @GET
    @Path("/schema")
    @Produces(MediaType.TEXT_PLAIN)
    public String getSchema() throws IOException {
        return ChannelMapper.getSchema();
    }
}