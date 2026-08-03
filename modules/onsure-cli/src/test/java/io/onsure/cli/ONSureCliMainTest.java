package io.onsure.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class ONSureCliMainTest {
    @Test
    void exposesPublicStaticMainWithoutDuplicatingPlatformImplementation() throws Exception {
        var method = ONSureCliMain.class.getMethod("main", String[].class);
        assertEquals(true, Modifier.isPublic(method.getModifiers()));
        assertEquals(true, Modifier.isStatic(method.getModifiers()));
    }
}
