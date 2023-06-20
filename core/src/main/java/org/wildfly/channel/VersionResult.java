/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

import java.util.Objects;
import java.util.Optional;

/**
 * The result of version search.
 */
public class VersionResult {
    private final String version;
    private final Optional<String> channelName;

    public VersionResult(String version, String channelName) {
        Objects.requireNonNull(version);

        this.version = version;
        this.channelName = Optional.ofNullable(channelName);
    }

    /**
     * The discovered artifact version.
     *
     * @return
     */
    public String getVersion() {
        return version;
    }

    /**
     * The name of the channel the artifact was found in.
     * @return {@code Optional} with the name of the channel providing the version, or an empty Optional if the channel is not named
     */
    public Optional<String> getChannelName() {
        return channelName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionResult that = (VersionResult) o;
        return Objects.equals(version, that.version) && Objects.equals(channelName, that.channelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, channelName);
    }

    @Override
    public String toString() {
        return "VersionResult{" +
                "version='" + version + '\'' +
                ", channelName='" + channelName + '\'' +
                '}';
    }
}
