package io.onsure.platform;

import java.util.Set;

/** Shared fail-closed boundary for generated and vendored trees that are not product source. */
final class GeneratedPathPolicy {
    private static final Set<String> EXCLUDED_NAMES = Set.of(
            ".git", ".onsure", ".vscode-test", ".gradle", ".venv", "venv",
            ".pytest_cache", ".mypy_cache", ".ruff_cache", ".tox", ".nox", ".cache",
            "__pycache__", "node_modules", "target", "build", "dist", "coverage",
            ".next", ".nuxt", "out", "run", "logs", "backups");

    private GeneratedPathPolicy() {}

    static boolean excludes(String name) {
        return EXCLUDED_NAMES.contains(name);
    }
}
