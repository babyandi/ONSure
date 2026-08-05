package io.onsure.provider.spi;

import java.util.List;

/** Non-secret provider metadata used for policy and capability negotiation. */
public record ProviderDescriptor(
        String providerId,
        String implementationVersion,
        List<String> modelIds,
        boolean localOnly,
        boolean networkEgressRequired) {
    public ProviderDescriptor {
        providerId = requireId(providerId, "providerId");
        implementationVersion = requireText(implementationVersion, "implementationVersion");
        modelIds = List.copyOf(modelIds == null ? List.of() : modelIds);
        if (modelIds.isEmpty() || modelIds.stream().anyMatch(value -> !value.matches("[A-Za-z0-9._:/-]{1,160}"))) {
            throw new IllegalArgumentException("modelIds");
        }
        if (localOnly && networkEgressRequired) throw new IllegalArgumentException("provider locality conflict");
    }

    private static String requireId(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) throw new IllegalArgumentException(name);
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 160) throw new IllegalArgumentException(name);
        return value;
    }
}
