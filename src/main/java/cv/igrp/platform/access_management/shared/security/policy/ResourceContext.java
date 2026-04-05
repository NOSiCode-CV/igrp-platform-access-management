package cv.igrp.platform.access_management.shared.security.policy;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides context about the resource being accessed for policy evaluation.
 */
public class ResourceContext {
    private final Map<String, Object> attributes = new HashMap<>();

    public ResourceContext(Map<String, Object> attributes) {
        if (attributes != null) {
            this.attributes.putAll(attributes);
        }
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public String getStringAttribute(String key) {
        Object val = getAttribute(key);
        return val != null ? val.toString() : null;
    }

    public static ResourceContext empty() {
        return new ResourceContext(Map.of());
    }

    public static ResourceContext of(String key, Object value) {
        return new ResourceContext(Map.of(key, value));
    }

    public static ResourceContext of(Map<String, Object> attributes) {
        return new ResourceContext(attributes);
    }
}
