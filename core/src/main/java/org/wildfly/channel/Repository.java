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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Java representation of Repository
 */
public class Repository {
    /**
     * ID of the repository.
     * Can be used to identify repository mirrors and proxies.
     */
    private String id;
    /**
     * URL of the repository.
     * Used when the client doesn't provide alternative URLs for a repository.
     */
    private String url;

    @JsonCreator
    public Repository(@JsonProperty(value = "id", required = true) String id, @JsonProperty(value = "url", required = true) String url) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(url);

        this.id = id;
        this.url = url;
    }

    public Repository() {

    }

    public String getId() {
        return id;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Repository that = (Repository) o;
        return Objects.equals(id, that.id) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, url);
    }
}
