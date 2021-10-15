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

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.wildfly.channel.version.VersionPatternMatcher;

public class VersionRule {

    private VersionStream stream;
    private Pattern qualifierPattern;

    @JsonCreator
    public VersionRule(@JsonProperty(value = "stream", required = true) VersionStream stream,
                       @JsonProperty(value = "qualifierPattern") Pattern qualifierPattern) {
        this.stream = stream;
        this.qualifierPattern = qualifierPattern;
    }

    public VersionStream getStream() {
        return stream;
    }

    public Pattern getQualifierPattern() {
        return qualifierPattern;
    }

    public Optional<String> matches(String baseVersion, Set<String> samples) {
        requireNonNull(baseVersion);
        requireNonNull(samples);

        Pattern pattern = buildPattern(baseVersion, stream, qualifierPattern);
        VersionPatternMatcher matcher = new VersionPatternMatcher(pattern);
        return matcher.matches(samples);
    }

    private Pattern buildPattern(String baseVersion, VersionStream stream, Pattern qualifierPattern) {
        String prefix = "";
        switch (stream) {
            case MAJOR:
                prefix = ".*";
                break;
            case MINOR:
                prefix = baseVersion.substring(0, baseVersion.indexOf('.'));
                break;
            case MICRO:
                String[] splits = baseVersion.split("\\.");
                prefix = splits[0] + "\\." + splits[1];
                break;
            default:
                splits = baseVersion.split("\\.");
                prefix = splits[0] + "\\." + splits[1] + "\\." + splits[2];
                break;
        }
        String pattern = prefix + "\\..*";
        if (qualifierPattern != null) {
            pattern += qualifierPattern.pattern();
        }
        return Pattern.compile(pattern);
    }

    @Override
    public String toString() {
        return "VersionRule{" +
                "stream=" + stream +
                ", qualifierPattern=" + qualifierPattern +
                '}';
    }

    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    public enum VersionStream {
        @JsonProperty("major")
        MAJOR,
        @JsonProperty("minor")
        MINOR,
        @JsonProperty("micro")
        MICRO,
        @JsonProperty("qualifier")
        QUALIFIER;
    }
}
