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
package org.wildfly.channel;

import java.util.List;

public class InvalidChannelMetadataException extends RuntimeException {
    private final List<String> messages;

    public InvalidChannelMetadataException(String message, List<String> messages) {
        super(message);
        this.messages = messages;
    }

    public InvalidChannelMetadataException(String message, List<String> messages, Exception cause) {
        super(message, cause);
        this.messages = messages;
    }

    public List<String> getValidationMessages() {
        return messages;
    }

    @Override
    public String getMessage() {
        final StringBuilder sb = new StringBuilder(super.getMessage());
        sb.append(System.lineSeparator());
        if (!messages.isEmpty()) {
            sb.append("caused by:").append(System.lineSeparator());
            for (String message : messages) {
                sb.append(" ").append(message).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }
}
