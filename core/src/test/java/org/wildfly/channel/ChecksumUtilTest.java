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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class ChecksumUtilTest {

    @Test()
    public void testNullFile() {
        assertThrows(Exception.class, () -> {
            ChecksumUtil.computeSHA1(null);
        });
    }

    @Test()
    public void testNonExistingFile() {
        assertThrows(Exception.class, () -> {
            ChecksumUtil.computeSHA1(new File("this does not exist"));
        });
    }

    @Test()
    public void testDirectory() {
        assertThrows(Exception.class, () -> {
            ChecksumUtil.computeSHA1(new File(System.getProperty("user.home")));
        });
    }

    @Test()
    public void testEmptyFile() throws IOException {
        Path temp = Files.createTempFile("hello", ".txt");
        String sha1 = ChecksumUtil.computeSHA1(temp.toFile());
        // expected sha1 computed with sha1sum /dev/null
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", sha1);
    }

    @Test()
    public void testFileWithContent() throws IOException {
        Path temp = Files.createTempFile("hello", ".txt");
        Files.write(temp, "Hello, World".getBytes());
        String sha1 = ChecksumUtil.computeSHA1(temp.toFile());
        // expected sha1 computed with sha1sum
        assertEquals("907d14fb3af2b0d4f18c2d46abe8aedce17367bd", sha1);
    }
}
