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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Java representation of a Vendor.
 */
public class Vendor {
    /**
     * Name of the vendor.
     */
    private final String name;

    /**
     * Support level provided by the vendor.
     */
    private final Support support;

    @JsonCreator
    Vendor(@JsonProperty(value = "name", required = true) String name, @JsonProperty(value = "support", required = true) Support support) {
        this.name = name;
        this.support = support;
    }

    public String getName() {
        return name;
    }

    public Support getSupport() {
        return support;
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum Support {
        @JsonProperty("supported")
        SUPPORTED,
        @JsonProperty("tech-preview")
        TECH_PREVIEW,
        @JsonProperty("community")
        COMMUNITY
    }

    @Override
    public String toString() {
        return "Vendor{" +
                "name='" + name + '\'' +
                ", support=" + support +
                '}';
    }
}
