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
package org.wildfly.channel.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.wildfly.channel.Vendor.Support.COMMUNITY;
import static org.wildfly.channel.Vendor.Support.SUPPORTED;
import static org.wildfly.channel.Vendor.Support.TECH_PREVIEW;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.Vendor;

public class VendorTestCase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

    static Vendor from(String str) throws IOException {
        return OBJECT_MAPPER.readValue(str, Vendor.class);
    }

    @Test
    public void testValidVendor() throws IOException {
        Vendor vendor = from("name: My Vendor\n" +
                "support: community");
        assertEquals("My Vendor", vendor.getName());
        assertEquals(COMMUNITY, vendor.getSupport());

        System.out.println("vendor = " + vendor);
    }

    @Test
    public void testNameIsMandatory() {
        Assertions.assertThrows(Exception.class, () -> {
            from("support: community");
        });
    }

    @Test
    public void testSupportIsMandatory() {
        Assertions.assertThrows(Exception.class, () -> {
            from("name: My Vendor");
        });
    }

    @Test
    public void testValidSupportValues() throws IOException {
        Vendor vendor = from("name: My Vendor\n" +
                "support: community");
        assertEquals(COMMUNITY, vendor.getSupport());

         vendor = from("name: My Vendor\n" +
                "support: tech-preview");
        assertEquals(TECH_PREVIEW, vendor.getSupport());

         vendor = from("name: My Vendor\n" +
                "support: supported");
        assertEquals(SUPPORTED, vendor.getSupport());
    }

    @Test
    public void testInvalidSupport() {
        Assertions.assertThrows(Exception.class, () -> {
            Vendor vendor = from("name: My Vendor\n" +
                    "support: experimental-is-not-a-valid-value");
        });
    }

}
