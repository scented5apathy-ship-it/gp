package com.genealogy.platform.spring.featureflags;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;

/**
 * Thin wrapper around the OpenFeature {@link Client} so services can
 * resolve a flag with a typed default without depending on the SDK
 * directly. Backed by a safe no-op client when no provider is
 * registered so the service never fails to start.
 */
public final class SafeFeatureClient {

    private final Client client;
    private final String serviceName;

    public SafeFeatureClient(OpenFeatureAPI api, String serviceName) {
        this.client = api.getClient(serviceName);
        this.serviceName = serviceName;
    }

    /**
     * Resolve a boolean flag. Returns {@code defaultValue} if the
     * provider is unavailable or the flag is missing — never throws.
     */
    public boolean getBoolean(String flag, boolean defaultValue) {
        try {
            return client.getBooleanValue(flag, defaultValue);
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    /**
     * Resolve a string flag. Returns {@code defaultValue} if the
     * provider is unavailable or the flag is missing — never throws.
     */
    public String getString(String flag, String defaultValue) {
        try {
            return client.getStringValue(flag, defaultValue);
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    public String getServiceName() {
        return serviceName;
    }
}
