package io.onsure.platform;

import static io.onsure.platform.StandardValidationPackSupport.step;

import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Standard static PostgreSQL/Flyway migration profile; live DB execution remains explicit. */
public final class PostgresqlValidationPack implements ValidationPack {
    @Override public String id() { return "postgresql"; }

    @Override
    public Contribution detect(Path root) throws Exception {
        Path migrations = StandardValidationPackSupport.findMigrationDirectory(root);
        if (migrations == null) return Contribution.none();
        Set<String> technologies = new LinkedHashSet<>();
        technologies.add("DATABASE_MIGRATIONS");
        boolean postgres = root.relativize(migrations).toString().toLowerCase(java.util.Locale.ROOT)
                .contains("postgres")
                || StandardValidationPackSupport.contains(root.resolve("pom.xml"), "postgresql")
                || StandardValidationPackSupport.contains(root.resolve("pom-modular.xml"), "postgresql")
                || StandardValidationPackSupport.contains(root.resolve("build.gradle"), "postgresql")
                || StandardValidationPackSupport.contains(root.resolve("build.gradle.kts"), "postgresql");
        if (postgres) technologies.add("POSTGRESQL");
        return new Contribution(technologies, List.of(step(
                "postgresql.migration-static", Phase.COMPONENT_AND_NEGATIVE, StepKind.DATABASE_MIGRATION,
                List.of(), Duration.ofMinutes(2), List.of("validator.meta-check"))));
    }
}
