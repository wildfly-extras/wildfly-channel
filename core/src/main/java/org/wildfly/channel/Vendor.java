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
    public Vendor(@JsonProperty(value = "name", required = true) String name, @JsonProperty(value = "support", required = true) Support support) {
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
