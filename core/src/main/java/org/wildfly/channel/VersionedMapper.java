package org.wildfly.channel;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.networknt.schema.JsonSchema;
import org.jboss.logging.Logger;
import org.wildfly.channel.version.VersionMatcher;

abstract class VersionedMapper {

    private static final Logger LOG = Logger.getLogger(VersionedMapper.class.getName());

    protected static JsonSchema getSchema(String version, Map<String, JsonSchema> schemas) {
        if (schemas.containsKey(version)) {
            return schemas.get(version);
        }

        Pattern versionPattern = Pattern.compile(version.substring(0, version.lastIndexOf('.') + 1).replace(".", "\\.") + ".*");
        final Optional<String> latestVersion = schemas.keySet().stream().filter(v -> versionPattern.matcher(v).matches()).max(VersionMatcher.COMPARATOR);

        if (latestVersion.isPresent()) {
            LOG.warnf("The schema version [%s] is not supported. The latest supported version is [%s], some features might be ignored.", version, latestVersion.get());
            return schemas.get(latestVersion.get());
        }

        return null;
    }
}
