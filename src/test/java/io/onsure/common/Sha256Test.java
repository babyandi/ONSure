package io.onsure.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Sha256Test {
    @TempDir Path temp;

    @Test
    void matchesPublishedSha256Vectors() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                Sha256.digest(""));
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256.digest("abc"));
    }

    @Test
    void stringByteAndFileInputsUseTheSameExactBytes() throws Exception {
        String value = "ONSure-한글-\n";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        Path file = temp.resolve("value.txt");
        Files.write(file, bytes);

        String expected = Sha256.digest(bytes);
        assertEquals(expected, Sha256.digest(value));
        assertEquals(expected, Sha256.digest(file));
        assertEquals(expected, io.onsure.harness.Hashing.sha256(bytes));
        assertEquals(expected, io.onsure.harness.Hashing.sha256(file));
    }
}
