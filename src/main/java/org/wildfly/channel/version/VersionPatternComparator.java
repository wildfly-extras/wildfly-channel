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
package org.wildfly.channel.version;

import static java.util.Optional.empty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class VersionPatternComparator implements VersionComparator {
    private Pattern pattern;

    public VersionPatternComparator(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public Optional<String> matches(List<String> samples) {
        List<String> matches = new ArrayList<>();
        for (String sample : samples) {
            if (pattern.matcher(sample).matches()) {
                matches.add(sample);
            }
        }
        if (matches.isEmpty()) {
            return empty();
        }

        matches.sort(Comparator.comparing( String::toString ).reversed());
        return Optional.of(matches.get(0));
    }
}
