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

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Mapper class to transform YAML content (from URL or String) to Channel objects (and vice versa).
 *
 * YAML input is validated against a schema.
 */
public class ChannelMapper {

    private static final String SCHEMA_FILE = "org/wildfly/channel/channel-schema.json";
    private static final YAMLFactory YAML_FACTORY = new YAMLFactory();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(YAML_FACTORY);
    private static final JsonSchemaFactory SCHEMA_FACTORY = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)).objectMapper(OBJECT_MAPPER).build();
    private static final JsonSchema SCHEMA = SCHEMA_FACTORY.getSchema(ChannelMapper.class.getClassLoader().getResourceAsStream(SCHEMA_FILE));

    public static String toYaml(Channel... channels) throws IOException {
        return toYaml(Arrays.asList(channels));
    }

    public static String toYaml(List<Channel> channels) throws IOException {
        Objects.requireNonNull(channels);
        StringWriter w = new StringWriter();
        for (Channel channel : channels) {
            OBJECT_MAPPER.writeValue(w, channel);
        }
        return w.toString();
    }

    public static Channel from(URL channelURL) throws InvalidChannelException {
        requireNonNull(channelURL);

        try {
            // QoL improvement
            if (channelURL.toString().endsWith("/")) {
                channelURL = channelURL.toURI().resolve("channel.yaml").toURL();
            }

            List<String> messages = validate(channelURL);
            if (!messages.isEmpty()) {
                throw new InvalidChannelException("Invalid channel", messages);
            }
            Channel channel = OBJECT_MAPPER.readValue(channelURL, Channel.class);
            return channel;
        } catch (IOException | URISyntaxException e) {
            throw wrapException(e);
        }
    }

    public static List<Channel> fromString(String yamlContent) throws InvalidChannelException {
        requireNonNull(yamlContent);

        try {
            List<String> messages = validateString(yamlContent);
            if (!messages.isEmpty()) {
                throw new InvalidChannelException("Invalid channel", messages);
            }

            YAMLParser parser = YAML_FACTORY.createParser(yamlContent);
            List<Channel> channels = OBJECT_MAPPER.readValues(parser, Channel.class).readAll();
            return channels;
        } catch (IOException e) {
            throw wrapException(e);
        }
    }

    private static List<String> validate(URL url) throws IOException {
        JsonNode node = OBJECT_MAPPER.readTree(url);
        Set<ValidationMessage> validationMessages = SCHEMA.validate(node);
        return validationMessages.stream().map(ValidationMessage::getMessage).collect(Collectors.toList());
    }

    private static List<String> validateString(String yamlContent) throws IOException {
        JsonNode node = OBJECT_MAPPER.readTree(yamlContent);
        Set<ValidationMessage> validationMessages = SCHEMA.validate(node);
        return validationMessages.stream().map(ValidationMessage::getMessage).collect(Collectors.toList());
    }

    private static InvalidChannelException wrapException(Exception e) {
        InvalidChannelException ice = new InvalidChannelException("Invalid Channel", singletonList(e.getLocalizedMessage()));
        ice.initCause(e);
        return ice;
    }
}
