package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvironmentRequirementProfileTest {
    @TempDir Path temp;

    @Test
    void loadsStrictTargetExternalRequirementsAndBindsBothDigests() throws Exception {
        Path profile = temp.resolve("environment.json");
        Files.writeString(profile, """
                {
                  "contract":"ONSURE_ENVIRONMENT_REQUIREMENT_PROFILE_V1",
                  "profile_id":"rendering",
                  "requirements":[
                    {"requirement_id":"clamav","kind":"EXECUTABLE","value":"clamscan","required":true},
                    {"requirement_id":"font","kind":"FONT_FAMILY","value":"Noto Sans CJK KR","required":true}
                  ]
                }
                """);

        var loaded = EnvironmentRequirementProfile.load(profile);

        assertEquals("rendering", loaded.profileId());
        assertEquals(2, loaded.requirements().size());
        assertEquals(64, loaded.semanticSha256().length());
        assertEquals(Hashing.file(profile), loaded.sourceFileSha256());
        assertEquals(profile.toAbsolutePath().normalize(), loaded.sourceFile());
    }

    @Test
    void rejectsUnknownFieldsPathEscapeAndDuplicateIds() throws Exception {
        Path unknown = write("unknown.json", """
                {"contract":"ONSURE_ENVIRONMENT_REQUIREMENT_PROFILE_V1","profile_id":"x",
                 "requirements":[{"requirement_id":"x","kind":"SOURCE_FILE","value":"x","required":true,"command":"unsafe"}]}
                """);
        Path escape = write("escape.json", """
                {"contract":"ONSURE_ENVIRONMENT_REQUIREMENT_PROFILE_V1","profile_id":"x",
                 "requirements":[{"requirement_id":"x","kind":"SOURCE_FILE","value":"../x","required":true}]}
                """);
        Path duplicate = write("duplicate.json", """
                {"contract":"ONSURE_ENVIRONMENT_REQUIREMENT_PROFILE_V1","profile_id":"x",
                 "requirements":[
                   {"requirement_id":"x","kind":"SOURCE_FILE","value":"a","required":true},
                   {"requirement_id":"x","kind":"SOURCE_FILE","value":"b","required":true}]}
                """);

        assertThrows(IllegalArgumentException.class, () -> EnvironmentRequirementProfile.load(unknown));
        assertThrows(IllegalArgumentException.class, () -> EnvironmentRequirementProfile.load(escape));
        assertThrows(IllegalArgumentException.class, () -> EnvironmentRequirementProfile.load(duplicate));
    }

    @Test
    void runnerRejectsEnvironmentProfileChangedAfterReviewBeforeCreatingRun() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("openapi.yaml"), "openapi: 3.1.0\npaths: {}\n");
        Path profileFile = write("environment.json", """
                {"contract":"ONSURE_ENVIRONMENT_REQUIREMENT_PROFILE_V1","profile_id":"immutable",
                 "requirements":[{"requirement_id":"fixture","kind":"SOURCE_FILE",
                 "value":"fixture.json","required":false}]}
                """);
        var loaded = EnvironmentRequirementProfile.load(profileFile);
        var profile = new StandardValidationProfileDetector().detect(
                "immutable", source, loaded.requirements());
        Files.writeString(profileFile, Files.readString(profileFile) + " ");
        Path runRoot = temp.resolve("must-not-exist");

        assertThrows(IllegalStateException.class,
                () -> new UniversalValidationRunner().run(profile, runRoot, loaded));
        assertFalse(Files.exists(runRoot));
    }

    private Path write(String name, String value) throws Exception {
        Path file = temp.resolve(name);
        Files.writeString(file, value);
        return file;
    }
}
