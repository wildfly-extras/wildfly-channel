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

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.wildfly.channel.version.VersionMatcher;
import org.wildfly.channel.version.VersionPatternMatcher;

public class VersionRule {

    private final List<Pattern> qualifiers;
    private final VersionStream stream;

    private static final Pattern ANY_QUALIFIER = Pattern.compile(".*");

    @JsonCreator
    public VersionRule(@JsonProperty(value = "stream", required = true) VersionStream stream,
                       @JsonProperty(value = "qualifierPattern") List<Pattern> qualifiers) {
        this.stream = stream;
        this.qualifiers = qualifiers != null ? qualifiers : emptyList();
    }

    public VersionStream getStream() {
        return stream;
    }

    public List<Pattern> getQualifiers() {
        return qualifiers;
    }

    Optional<String> matches(String baseVersion, Set<String> samples) {
        requireNonNull(baseVersion);
        requireNonNull(samples);

        if (qualifiers.isEmpty()) {
            Pattern pattern = buildPattern(baseVersion, ANY_QUALIFIER);
            VersionPatternMatcher matcher = new VersionPatternMatcher(pattern);
            return matcher.matches(samples);
        }

        Set<String> matches = new HashSet<>();
        for (Pattern qualifier : qualifiers) {
            Pattern pattern = buildPattern(baseVersion, qualifier);
            VersionPatternMatcher matcher = new VersionPatternMatcher(pattern);
            Optional<String> match = matcher.matches(samples);
            if (match.isPresent()) {
                matches.add(match.get());
            }
        }
        return matches.stream().sorted(VersionMatcher.COMPARATOR.reversed()).findFirst();
    }

    private Pattern buildPattern(String baseVersion, Pattern qualifierPattern) {
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
                "qualifiers=" + qualifiers +
                ", stream=" + stream +
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
