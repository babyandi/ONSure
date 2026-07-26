package io.onsure.platform.oruda;

import io.onsure.platform.OrudaTargetAdapter;
import io.onsure.platform.ValidationEngine;
import java.nio.file.Path;
import java.util.List;

/** Optional ORUDA adapter assembly. This class belongs to the ORUDA adapter module, not Core. */
public final class OrudaValidationEngineFactory {
    private OrudaValidationEngineFactory() {}

    public static ValidationEngine create(Path storeRoot) {
        return ValidationEngine.withOptionalAdapters(
                storeRoot, List.of(new OrudaTargetAdapter()));
    }
}
