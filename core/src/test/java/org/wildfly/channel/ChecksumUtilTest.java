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
            ChecksumUtil.computeSHA256(null);
        });
    }

    @Test()
    public void testNonExistingFile() {
        assertThrows(Exception.class, () -> {
            ChecksumUtil.computeSHA256(new File("this does not exist"));
        });
    }

    @Test()
    public void testDirectory() {
        assertThrows(Exception.class, () -> {
            ChecksumUtil.computeSHA256(new File(System.getProperty("user.home")));
        });
    }

    @Test()
    public void testEmptyFile() throws IOException {
        Path temp = Files.createTempFile("hello", ".txt");
        String sha256 = ChecksumUtil.computeSHA256(temp.toFile());
        // expected sha256 computed with sha256sum /dev/null
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256);
    }

    @Test()
    public void testFileWithContent() throws IOException {
        Path temp = Files.createTempFile("hello", ".txt");
        Files.write(temp, "Hello, World".getBytes());
        String sha256 = ChecksumUtil.computeSHA256(temp.toFile());
        // expected sha256 computed with sha256sum
        assertEquals("03675ac53ff9cd1535ccc7dfcdfa2c458c5218371f418dc136f2d19ac1fbe8a5", sha256);
    }
}
