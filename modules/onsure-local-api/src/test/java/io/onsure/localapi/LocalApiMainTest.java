package io.onsure.localapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class LocalApiMainTest {
    @Test
    void exposesPublicStaticMainWithoutDuplicatingPlatformImplementation() throws Exception {
        var method = LocalApiMain.class.getMethod("main", String[].class);
        assertEquals(true, Modifier.isPublic(method.getModifiers()));
        assertEquals(true, Modifier.isStatic(method.getModifiers()));
    }
}
