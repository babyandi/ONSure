package kr.co.oruda.onsure.platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fail-safe git metadata inventory: submodules, LFS-tracked patterns, and remote providers.
 * Branch, HEAD SHA, and dirty-state detection already exist in {@link SourceReferenceBinding}
 * and {@link ProgramLearningService}; this class fills the remaining gap in the FR-01-B "Git
 * metadata inventory" requirement without duplicating that logic. Unlike
 * {@link SourceReferenceBinding}, this is descriptive inventory rather than a security boundary,
 * so a missing git binary, a non-repository directory, or any subprocess failure degrades to
 * empty results instead of throwing.
 */
public final class GitMetadataInventory {

    public enum SubmoduleStatus {
        NOT_INITIALIZED,
        CHECKED_OUT_DIFFERENT_COMMIT,
        MERGE_CONFLICT,
        UP_TO_DATE
    }

    public enum Provider {
        GITHUB,
        GITLAB,
        BITBUCKET,
        AZURE_DEVOPS,
        UNKNOWN
    }

    public record Submodule(String path, String commitSha, SubmoduleStatus status) {}

    public record Remote(String name, String url, Provider provider) {}

    public record Inventory(
            boolean repository,
            List<Submodule> submodules,
            List<String> lfsPatterns,
            List<Remote> remotes) {}

    private static final Inventory EMPTY = new Inventory(false, List.of(), List.of(), List.of());

    private GitMetadataInventory() {}

    public static Inventory inspect(Path sourceRoot) {
        Path root;
        try {
            root = sourceRoot.toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return EMPTY;
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return EMPTY;
        }
        if (!isRepository(root)) {
            return EMPTY;
        }
        return new Inventory(true, submodules(root), lfsPatterns(root), remotes(root));
    }

    private static boolean isRepository(Path root) {
        CommandResult result = tryGit(root, List.of("rev-parse", "--show-toplevel"));
        return result != null && result.exitCode() == 0 && !result.outputTruncated();
    }

    private static List<Submodule> submodules(Path root) {
        CommandResult result = tryGit(root, List.of("submodule", "status", "--recursive"));
        if (result == null || result.exitCode() != 0 || result.outputTruncated()) return List.of();
        List<Submodule> values = new ArrayList<>();
        for (String line : result.output().split("\\R")) {
            Submodule submodule = parseSubmoduleLine(line);
            if (submodule != null) values.add(submodule);
        }
        return List.copyOf(values);
    }

    /**
     * Parses one line of {@code git submodule status} output. The leading character encodes
     * status: {@code -} not initialized, {@code +} checked out at a different commit than the
     * superproject records, {@code U} merge conflict, and a plain space means up to date.
     * The remainder is {@code <sha1> <path> (<describe-output>)}.
     */
    static Submodule parseSubmoduleLine(String line) {
        if (line.isEmpty()) return null;
        char prefix = line.charAt(0);
        SubmoduleStatus status = switch (prefix) {
            case '-' -> SubmoduleStatus.NOT_INITIALIZED;
            case '+' -> SubmoduleStatus.CHECKED_OUT_DIFFERENT_COMMIT;
            case 'U' -> SubmoduleStatus.MERGE_CONFLICT;
            case ' ' -> SubmoduleStatus.UP_TO_DATE;
            default -> null;
        };
        if (status == null) return null;
        String rest = line.substring(1).strip();
        if (rest.isEmpty()) return null;
        String[] parts = rest.split("\\s+", 3);
        if (parts.length < 2) return null;
        String sha = parts[0];
        String path = parts[1];
        if (sha.isBlank() || path.isBlank()) return null;
        return new Submodule(path, sha, status);
    }

    private static List<String> lfsPatterns(Path root) {
        Path attributes = root.resolve(".gitattributes");
        if (!Files.isRegularFile(attributes, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(attributes)) {
            return List.of();
        }
        try {
            List<String> patterns = new ArrayList<>();
            for (String line : Files.readAllLines(attributes, StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (!trimmed.contains("filter=lfs")) continue;
                String pattern = trimmed.split("\\s+", 2)[0].strip();
                if (!pattern.isBlank()) patterns.add(pattern);
            }
            return List.copyOf(patterns);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<Remote> remotes(Path root) {
        CommandResult result = tryGit(root, List.of("remote", "-v"));
        if (result == null || result.exitCode() != 0 || result.outputTruncated()) return List.of();
        Map<String, String> byName = new LinkedHashMap<>();
        for (String line : result.output().split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 2) continue;
            byName.putIfAbsent(parts[0], parts[1]);
        }
        List<Remote> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : byName.entrySet()) {
            values.add(new Remote(entry.getKey(), entry.getValue(), detectProvider(entry.getValue())));
        }
        return List.copyOf(values);
    }

    /**
     * Detects a known remote provider from a fetch/push URL, handling both HTTPS
     * ({@code https://github.com/org/repo.git}) and SSH ({@code git@github.com:org/repo.git})
     * forms, case-insensitively.
     */
    static Provider detectProvider(String url) {
        if (url == null || url.isBlank()) return Provider.UNKNOWN;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("github.com")) return Provider.GITHUB;
        if (lower.contains("gitlab.com")) return Provider.GITLAB;
        if (lower.contains("bitbucket.org")) return Provider.BITBUCKET;
        if (lower.contains("dev.azure.com") || lower.contains("visualstudio.com")) return Provider.AZURE_DEVOPS;
        return Provider.UNKNOWN;
    }

    private static CommandResult tryGit(Path root, List<String> arguments) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.add("-C");
            command.add(root.toString());
            command.addAll(arguments);
            Map<String, String> environment = new LinkedHashMap<>();
            String path = System.getenv("PATH");
            if (path != null) environment.put("PATH", path);
            environment.put("GIT_TERMINAL_PROMPT", "0");
            BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                    command, root, Duration.ofSeconds(15), environment, "GIT_METADATA_INVENTORY");
            return new CommandResult(result.exitCode(), result.output(), result.outputTruncated());
        } catch (Exception ignored) {
            return null;
        }
    }

    private record CommandResult(int exitCode, String output, boolean outputTruncated) {}
}
