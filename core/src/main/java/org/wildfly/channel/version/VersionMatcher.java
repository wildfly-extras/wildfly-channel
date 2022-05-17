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
package org.wildfly.channel.version;

import static java.util.Objects.requireNonNull;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public interface VersionMatcher {
    /**
     * Determine the latest version among the parameters based on the {@link #COMPARATOR}.
     *
     * @param versions a Set of versions
     * @return an Optional of the latest version.
     */
    static Optional<String> getLatestVersion(Set<String> versions) {
        requireNonNull(versions);
        return versions.stream().sorted(COMPARATOR.reversed()).findFirst();
    }

    Optional<String> matches(Set<String> samples);

    /**
     * Copied from https://raw.githubusercontent.com/wolfc/updepres/master/model/src/main/java/org/jboss/up/depres/version/VersionComparator.java
     * FIXME: proper attribution
     */
    Comparator<String> COMPARATOR = (v1, v2) -> {
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
            if (Character.isDigit(c1) || Character.isDigit(c2)) {
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
            } else if (c1 == c2) {
                i1++;
                i2++;
            } else {
                return c1 - c2;
            }
        }
        return lim1 - lim2;
    };
}
