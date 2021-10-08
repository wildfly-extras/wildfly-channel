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

import java.net.URL;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Java representation of a Maven repository
 */
public class MavenRepository {
    /**
     * Identifier of the Maven repository.
     * This is an optional field.
     */
    private String id;

    /**
     * Base URL of the Maven repository.
     */
    private URL url;

    @JsonCreator
    MavenRepository(@JsonProperty("id") String id, @JsonProperty(value = "url", required = true) URL url) {
        this.id = id;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public URL getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "MavenRepository{" +
                "id='" + id + '\'' +
                ", url=" + url +
                '}';
    }
}
