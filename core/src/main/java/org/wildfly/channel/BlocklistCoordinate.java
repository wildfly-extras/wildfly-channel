/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.channel;

import java.net.MalformedURLException;
import java.net.URL;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties({"groupId", "artifactId", "version", "classifier", "extension"})
public class BlocklistCoordinate extends ChannelMetadataCoordinate {

   public static String CLASSIFIER = "blocklist";
   public static String EXTENSION = "yaml";

   public BlocklistCoordinate(String groupId, String artifactId) {
      super(groupId, artifactId, BlocklistCoordinate.CLASSIFIER, BlocklistCoordinate.EXTENSION);
   }

   public BlocklistCoordinate(String groupId, String artifactId, String version) {
      super(groupId, artifactId, version, BlocklistCoordinate.CLASSIFIER, BlocklistCoordinate.EXTENSION);
   }

   public BlocklistCoordinate(URL url) {
      super(url);
   }
   
   public BlocklistCoordinate() {
      super(BlocklistCoordinate.CLASSIFIER, BlocklistCoordinate.EXTENSION);
   }

   @JsonCreator
   public static BlocklistCoordinate create(@JsonProperty(value = "maven") MavenCoordinate coord,
                                            @JsonProperty(value = "url") String url)
           throws MalformedURLException {
      if (coord != null) {
         if (coord.getVersion() == null || coord.getVersion().isEmpty()) {
            return new BlocklistCoordinate(coord.getGroupId(), coord.getArtifactId());
         } else {
            return new BlocklistCoordinate(coord.getGroupId(), coord.getArtifactId(), coord.getVersion());
         }
      } else {
         return new BlocklistCoordinate(new URL(url));
      }
   }

   @JsonInclude(JsonInclude.Include.NON_NULL)
   @Override
   public URL getUrl() {
      return super.getUrl();
   }

   @JsonProperty("maven")
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public MavenCoordinate getMaven() {
      if (getUrl() == null) {
         return new MavenCoordinate(getGroupId(), getArtifactId(), getVersion());
      } else {
         return null;
      }
   }
}
