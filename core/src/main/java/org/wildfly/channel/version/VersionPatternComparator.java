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
import java.util.Set;
import java.util.regex.Pattern;

public class VersionPatternComparator implements VersionComparator {
    private Pattern pattern;

    public VersionPatternComparator(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public Optional<String> matches(Set<String> samples) {
        List<String> matches = new ArrayList<>();
        for (String sample : samples) {
            if (pattern.matcher(sample).matches()) {
                matches.add(sample);
            }
        }
        if (matches.isEmpty()) {
            return empty();
        }

        matches.sort(COMPARATOR);
        return Optional.of(matches.get(matches.size() - 1));
    }

    /**
     * Copied from https://raw.githubusercontent.com/wolfc/updepres/master/model/src/main/java/org/jboss/up/depres/version/VersionComparator.java
     * FIXME: proper attribution
     */
    private final static Comparator<String> COMPARATOR = (v1, v2) -> {
        int i1 = 0, i2 = 0;
        final int epoch1;
        int i = v1.indexOf(":");
        if (i != -1) {
            epoch1 = Integer.valueOf(v1.substring(0, i));
            i1 = i;
        }
        else
            epoch1 = 0;
        final int epoch2;
        i = v2.indexOf(":");
        if (i != -1) {
            epoch2 = Integer.valueOf(v2.substring(0, i));
            i2 = i;
        }
        else
            epoch2 = 0;
        if (epoch1 != epoch2)
            return epoch1 - epoch2;

        final int lim1 = v1.length(), lim2 = v2.length();
        while (i1 < lim1 && i2 < lim2) {
            final char c1 = v1.charAt(i1);
            final char c2 = v2.charAt(i2);
            if (c1 == c2) {
                i1++;
                i2++;
            } else if (Character.isDigit(c1) || Character.isDigit(c2)) {
                int ei1 = i1, ei2 = i2;
                while (ei1 < lim1 && Character.isDigit(v1.charAt(ei1))) ei1++;
                while (ei2 < lim2 && Character.isDigit(v2.charAt(ei2))) ei2++;
                final int n1 = ei1 == i1 ? 0 : Integer.valueOf(v1.substring(i1, ei1));
                final int n2 = ei2 == i2 ? 0 : Integer.valueOf(v2.substring(i2, ei2));
                if (n1 != n2)
                    return n1 - n2;
                i1 = ei1;
                i2 = ei2;
                if (i1 != i2)
                    return i2 - i1;
            } else {
                return c1 - c2;
            }
        }
        return lim1 - lim2;
    };
}
