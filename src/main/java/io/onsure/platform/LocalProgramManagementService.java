package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Registers external source read-only and validates only an isolated bounded snapshot. */
final class LocalProgramManagementService {
    static final String CONTRACT = "ONSURE_READ_ONLY_PROGRAM_VALIDATION_V1";
    private static final long MAX_SOURCE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_SOURCE_FILES = 50_000L;
    private static final Set<String> EXCLUDED_SEGMENTS = Set.of(
            ".git", ".onsure", ".venv", "venv", ".pytest_cache", "target", "node_modules",
            "__pycache__", "backups", "logs", "run");
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path workspaceRoot;

    LocalProgramManagementService(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    Map<String, Object> register(JsonNode request) throws Exception {
        String workspaceId = id(request, "workspace_id");
        String workspaceName = text(request, "workspace_name", 200);
        String projectId = id(request, "project_id");
        String projectName = text(request, "project_name", 200);
        String targetId = id(request, "target_id");
        String targetName = text(request, "target_name", 200);
        String targetType = text(request, "target_type", 64);
        Path sourceRoot = sourceRoot(request.path("source_root").asText());
        String sourceDigest = inclusiveTreeDigest(sourceRoot).digest();
        ProductCatalog catalog = catalog();
        boolean workspaceCreated;
        try {
            catalog.registerWorkspace(new ProductCatalog.Workspace(workspaceId, workspaceName, Instant.now()));
            workspaceCreated = true;
        } catch (IllegalArgumentException exists) {
            if (!"WORKSPACE_EXISTS".equals(exists.getMessage())) throw exists;
            workspaceCreated = false;
        }
        boolean projectCreated;
        try {
            catalog.registerProject(new ProductCatalog.Project(projectId, workspaceId, projectName, Instant.now()));
            projectCreated = true;
        } catch (IllegalArgumentException exists) {
            if (!"PROJECT_EXISTS".equals(exists.getMessage())) throw exists;
            projectCreated = false;
        }
        boolean targetCreated;
        boolean sourceReferenceDrift = false;
        try {
            ValidationTarget target = new ValidationTarget(
                    targetId, targetName, ValidationModel.TargetType.valueOf(targetType), sourceRoot,
                    "sha256:" + sourceDigest, GenericManifestTargetAdapter.ID,
                    "ONSURE_DEFAULT_POLICY_V1", "REGISTERED_REVIEWED");
            catalog.registerTarget(new ProductCatalog.RegisteredTarget(projectId, target, Instant.now()));
            targetCreated = true;
        } catch (IllegalArgumentException exists) {
            if (!"TARGET_EXISTS".equals(exists.getMessage())) throw exists;
            ProductCatalog.RegisteredTarget current = registered(projectId, targetId);
            if (!sourceRoot.equals(current.target().sourceRoot())) {
                throw new IllegalArgumentException("REGISTERED_TARGET_SOURCE_MISMATCH");
            }
            if (!targetName.equals(current.target().targetName())
                    || !targetType.equals(current.target().targetType().name())) {
                throw new IllegalArgumentException("REGISTERED_TARGET_IDENTITY_MISMATCH");
            }
            sourceReferenceDrift = !("sha256:" + sourceDigest)
                    .equals(current.target().immutableSourceReference());
            targetCreated = false;
        }
        return Map.ofEntries(
                Map.entry("contract", CONTRACT), Map.entry("workspace_id", workspaceId),
                Map.entry("project_id", projectId), Map.entry("target_id", targetId),
                Map.entry("source_root", sourceRoot.toString()), Map.entry("observed_source_sha256", sourceDigest),
                Map.entry("read_only_registration", true), Map.entry("workspace_created", workspaceCreated),
                Map.entry("project_created", projectCreated), Map.entry("target_created", targetCreated),
                Map.entry("source_reference_drift", sourceReferenceDrift),
                Map.entry("final_claim_allowed", false));
    }

    Map<String, Object> validate(JsonNode request) throws Exception {
        String projectId = id(request, "project_id");
        String targetId = id(request, "target_id");
        String profile = request.path("profile").asText("INSPECT_ONLY");
        if (!Set.of("INSPECT_ONLY", "MAVEN_STANDARD").contains(profile)) {
            throw new IllegalArgumentException("PROGRAM_VALIDATION_PROFILE_INVALID");
        }
        ProductCatalog.RegisteredTarget registered = registered(projectId, targetId);
        ValidationTarget target = registered.target();
        Path source = sourceRoot(target.sourceRoot().toString());
        TreeObservation before = inclusiveTreeDigest(source);
        String runId = "readonly-" + RUN_TIME.format(Instant.now()) + "-" + UUID.randomUUID();
        Path runRoot = workspaceRoot.resolve(".onsure/validation-data").resolve(targetId).resolve(runId).normalize();
        if (!runRoot.startsWith(workspaceRoot)) throw new IllegalStateException("PROGRAM_RUN_ROOT_INVALID");
        Files.createDirectories(runRoot);

        List<Map<String, Object>> commands = new ArrayList<>();
        Path sandbox = workspaceRoot.resolve(".onsure/validation-sandboxes").resolve(runId).normalize();
        try {
            if ("MAVEN_STANDARD".equals(profile)) {
                copySnapshot(source, sandbox);
                commands.add(runCommand(sandbox, "CANONICAL_CLEAN_VERIFY",
                        List.of("mvn", "-B", "-ntp", "-q", "clean", "verify"), Duration.ofMinutes(20)));
                if (Files.isRegularFile(sandbox.resolve("pom-modular.xml"))) {
                    commands.add(runCommand(sandbox, "MODULAR_CLEAN_PACKAGE",
                            List.of("mvn", "-B", "-ntp", "-q", "-f", "pom-modular.xml", "clean", "package"),
                            Duration.ofMinutes(20)));
                }
                if (Files.isDirectory(sandbox.resolve("tests"))) {
                    commands.add(runCommand(sandbox, "PYTHON_REGRESSION",
                            List.of("python3", "-m", "unittest", "discover", "-s", "tests", "-p", "test_*.py"),
                            Duration.ofMinutes(10)));
                }
                if (Files.isRegularFile(sandbox.resolve("scripts/onsure_java_api_baseline.py"))) {
                    commands.add(runCommand(sandbox, "PUBLIC_API_BASELINE",
                            List.of("python3", "scripts/onsure_java_api_baseline.py", "validate"), Duration.ofMinutes(5)));
                }
            }

            TreeObservation after = inclusiveTreeDigest(source);
            if (!before.digest().equals(after.digest()) || before.fileCount() != after.fileCount()
                    || before.byteCount() != after.byteCount()) {
                throw new IllegalStateException("READ_ONLY_SOURCE_CHANGED_DURING_VALIDATION");
            }
            SourceAnalysis analysis = analyze(source);
            boolean sourceReferenceMatches = ("sha256:" + before.digest())
                    .equals(target.immutableSourceReference());
            List<Map<String, Object>> findings = findings(source, analysis, commands, sourceReferenceMatches);
            List<Map<String, Object>> evidence = evidence(before, analysis, commands, profile);
            List<Map<String, Object>> remediation = remediation(findings);
            String decision = findings.isEmpty() ? "PASS_NONFINAL" : "HOLD";
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("contract", CONTRACT);
            report.put("reportId", "report-" + runId);
            report.put("jobId", runId);
            report.put("projectId", projectId);
            report.put("targetId", targetId);
            report.put("targetName", target.targetName());
            report.put("targetType", target.targetType().name());
            report.put("decision", decision);
            report.put("generatedAt", Instant.now().toString());
            report.put("profile", profile);
            report.put("sourceDigestBefore", before.digest());
            report.put("sourceDigestAfter", after.digest());
            report.put("sourceMutationDetected", false);
            report.put("findings", findings);
            report.put("commands", commands);
            report.put("summary", Map.of(
                    "pom_count", analysis.pomCount(), "module_count", analysis.moduleCount(),
                    "java_main_file_count", analysis.javaMainCount(), "java_test_file_count", analysis.javaTestCount(),
                    "public_type_candidate_count", analysis.publicTypeCount(), "git_dirty_entry_count", analysis.gitDirtyCount(),
                    "license_present", analysis.licensePresent(), "source_reference_matches", sourceReferenceMatches,
                    "evidence_count", evidence.size()));
            report.put("assurance_class", "SELF_VALIDATION_NONFINAL");
            report.put("independent_otester", "NOT_RUN");
            report.put("independent_oaudit", "NOT_RUN");
            report.put("final_claim_allowed", false);
            write(runRoot.resolve("validation-report.json"), report);
            write(runRoot.resolve("evidence.json"), evidence);
            write(runRoot.resolve("remediation-plans.json"), remediation);
            return Map.ofEntries(
                    Map.entry("contract", CONTRACT), Map.entry("run_id", runId),
                    Map.entry("run_root", runRoot.toString()), Map.entry("decision", decision),
                    Map.entry("finding_count", findings.size()), Map.entry("evidence_count", evidence.size()),
                    Map.entry("improvement_candidate_count", remediation.size()),
                    Map.entry("source_mutation_detected", false), Map.entry("final_claim_allowed", false));
        } finally {
            deleteSandbox(sandbox);
        }
    }

    private ProductCatalog catalog() {
        return new ProductCatalog(workspaceRoot.resolve(".onsure/product-catalog"));
    }

    private ProductCatalog.RegisteredTarget registered(String projectId, String targetId) throws Exception {
        return catalog().targets(projectId).stream()
                .filter(value -> targetId.equals(value.target().targetId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("PROGRAM_TARGET_NOT_REGISTERED"));
    }

    private Map<String, Object> runCommand(Path sandbox, String id, List<String> command, Duration timeout)
            throws Exception {
        Instant started = Instant.now();
        Map<String, String> environment = new LinkedHashMap<>();
        for (String key : List.of("PATH", "JAVA_HOME", "LANG", "LC_ALL")) {
            String value = System.getenv(key);
            if (value != null) environment.put(key, value);
        }
        environment.put("GIT_TERMINAL_PROMPT", "0");
        BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                command, sandbox, timeout, 4 * 1024 * 1024, environment, "READ_ONLY_VALIDATION_" + id);
        long duration = Duration.between(started, Instant.now()).toMillis();
        return Map.of(
                "command_id", id, "exit_code", result.exitCode(), "duration_millis", duration,
                "output_sha256", Hashing.sha256(result.output()), "output_bytes", result.output().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                "output_truncated", result.outputTruncated(), "output_content_recorded", false);
    }

    private SourceAnalysis analyze(Path source) throws Exception {
        long pom = 0, modules = 0, main = 0, tests = 0, publicTypes = 0;
        List<Path> files = sourceFiles(source);
        for (Path file : files) {
            String relative = Hashing.relative(source, file);
            if (relative.endsWith("pom.xml")) pom++;
            if (relative.startsWith("modules/") && relative.endsWith("/pom.xml")) modules++;
            if (relative.endsWith(".java")) {
                if (relative.contains("/src/test/") || relative.startsWith("src/test/")) tests++; else main++;
                String text = Files.readString(file);
                if (text.matches("(?s).*\\bpublic\\s+(?:class|interface|record|enum)\\s+.*")) publicTypes++;
            }
        }
        long dirty = gitDirtyCount(source);
        boolean license = List.of("LICENSE", "LICENSE.md", "LICENSE.txt", "COPYING", "NOTICE").stream()
                .anyMatch(name -> Files.isRegularFile(source.resolve(name), LinkOption.NOFOLLOW_LINKS));
        return new SourceAnalysis(pom, modules, main, tests, publicTypes, dirty, license, files.size());
    }

    private List<Map<String, Object>> findings(
            Path source, SourceAnalysis analysis, List<Map<String, Object>> commands,
            boolean sourceReferenceMatches) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (!sourceReferenceMatches) values.add(finding(
                "SOURCE_REFERENCE_DRIFT", "HIGH", "등록된 source digest와 현재 검증 입력이 다릅니다.",
                "현재 입력의 소유권과 변경 범위를 검토한 뒤 새 불변 source reference로 재등록하십시오."));
        if (analysis.gitDirtyCount() > 0) values.add(finding(
                "SOURCE_WORKTREE_DIRTY", "MEDIUM", "검증 입력 working tree에 미커밋 변경이 있습니다.",
                "검증 기준 SHA와 변경 소유권을 확정한 뒤 재검증하십시오."));
        if (!analysis.licensePresent()) values.add(finding(
                "ROOT_LICENSE_UNDECLARED", "HIGH", "루트 라이선스가 선언되지 않았습니다.",
                "제품 소유자가 배포 전 라이선스를 확정해야 합니다."));
        for (Map<String, Object> command : commands) {
            if (((Number) command.get("exit_code")).intValue() != 0) values.add(finding(
                    "COMMAND_FAILED_" + command.get("command_id"), "HIGH",
                    command.get("command_id") + " 명령이 실패했습니다.",
                    "digest-bound 실행 로그를 격리 환경에서 검토하고 원인을 수정하십시오."));
            if (Boolean.TRUE.equals(command.get("output_truncated"))) values.add(finding(
                    "COMMAND_OUTPUT_TRUNCATED_" + command.get("command_id"), "MEDIUM",
                    command.get("command_id") + " 출력이 상한을 초과했습니다.",
                    "민감정보 노출 없이 별도 bounded 로그 증적을 생성하십시오."));
        }
        return List.copyOf(values);
    }

    private Map<String, Object> finding(String code, String severity, String title, String improvement) {
        return Map.of(
                "finding_id", "finding-" + code.toLowerCase(java.util.Locale.ROOT),
                "code", code, "severity", severity, "title", title,
                "improvement", improvement, "status", "OPEN", "final_claim_allowed", false);
    }

    private List<Map<String, Object>> evidence(
            TreeObservation tree, SourceAnalysis analysis, List<Map<String, Object>> commands, String profile) {
        List<Map<String, Object>> values = new ArrayList<>();
        values.add(Map.of(
                "evidence_id", "source-tree", "type", "SOURCE_DIGEST", "sha256", tree.digest(),
                "file_count", tree.fileCount(), "byte_count", tree.byteCount(), "read_only", true));
        values.add(Map.of(
                "evidence_id", "structure", "type", "STRUCTURE_ANALYSIS",
                "sha256", Hashing.sha256(analysis.toString()), "profile", profile,
                "public_type_candidate_count", analysis.publicTypeCount()));
        for (Map<String, Object> command : commands) {
            values.add(Map.of(
                    "evidence_id", "command-" + command.get("command_id"), "type", "BOUNDED_COMMAND",
                    "sha256", command.get("output_sha256"), "exit_code", command.get("exit_code"),
                    "duration_millis", command.get("duration_millis"), "output_content_recorded", false));
        }
        return List.copyOf(values);
    }

    private List<Map<String, Object>> remediation(List<Map<String, Object>> findings) {
        return findings.stream().map(finding -> Map.<String, Object>of(
                "plan_id", "plan-" + finding.get("code").toString().toLowerCase(java.util.Locale.ROOT),
                "finding_id", finding.get("finding_id"), "state", "CANDIDATE_REQUIRES_APPROVAL",
                "recommendation", finding.get("improvement"), "automatic_apply", false,
                "final_claim_allowed", false)).toList();
    }

    private TreeObservation inclusiveTreeDigest(Path source) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long[] count = {0L}, bytes = {0L};
        for (Path file : sourceFiles(source)) {
            String relative = Hashing.relative(source, file);
            digest.update(relative.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) {
                    digest.update(buffer, 0, read);
                    bytes[0] += read;
                    if (bytes[0] > MAX_SOURCE_BYTES) throw new IllegalArgumentException("PROGRAM_SOURCE_BYTE_LIMIT");
                }
            }
            digest.update((byte) 0);
            count[0]++;
            if (count[0] > MAX_SOURCE_FILES) throw new IllegalArgumentException("PROGRAM_SOURCE_FILE_LIMIT");
        }
        return new TreeObservation(HexFormat.of().formatHex(digest.digest()), count[0], bytes[0]);
    }

    private List<Path> sourceFiles(Path source) throws Exception {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(source) && excluded(source.relativize(dir))) return FileVisitResult.SKIP_SUBTREE;
                if (Files.isSymbolicLink(dir)) throw new IllegalArgumentException("PROGRAM_SOURCE_SYMLINK_PROHIBITED");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("PROGRAM_SOURCE_SYMLINK_PROHIBITED");
                if (attrs.isRegularFile() && !excluded(source.relativize(file))) files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(path -> Hashing.relative(source, path)));
        return List.copyOf(files);
    }

    private void copySnapshot(Path source, Path target) throws Exception {
        if (!target.startsWith(workspaceRoot) || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("PROGRAM_SANDBOX_INVALID");
        }
        Files.createDirectories(target);
        for (Path file : sourceFiles(source)) {
            Path relative = source.relativize(file);
            Path destination = target.resolve(relative).normalize();
            if (!destination.startsWith(target)) throw new IllegalArgumentException("PROGRAM_SNAPSHOT_PATH_ESCAPE");
            Files.createDirectories(destination.getParent());
            Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private void deleteSandbox(Path sandbox) throws Exception {
        if (!sandbox.startsWith(workspaceRoot.resolve(".onsure/validation-sandboxes").normalize())) {
            throw new IllegalStateException("PROGRAM_SANDBOX_DELETE_BOUNDARY");
        }
        if (!Files.exists(sandbox, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(sandbox)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(path)) throw new IllegalStateException("PROGRAM_SANDBOX_SYMLINK_DELETE_PROHIBITED");
                Files.deleteIfExists(path);
            }
        }
    }

    private long gitDirtyCount(Path source) throws Exception {
        BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                List.of("git", "-C", source.toString(), "status", "--porcelain", "--untracked-files=all", "--", "."),
                source, Duration.ofSeconds(30), 4 * 1024 * 1024,
                Map.of("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"), "GIT_TERMINAL_PROMPT", "0"),
                "READ_ONLY_GIT_STATUS");
        if (result.exitCode() != 0 || result.outputTruncated()) return -1L;
        return result.output().lines().filter(line -> !line.isBlank()).count();
    }

    private void write(Path file, Object value) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean excluded(Path relative) {
        for (Path segment : relative) if (EXCLUDED_SEGMENTS.contains(segment.toString())) return true;
        return false;
    }

    private static Path sourceRoot(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("PROGRAM_SOURCE_ROOT_REQUIRED");
        Path source = Path.of(raw).toAbsolutePath().normalize();
        if (source.getParent() == null || !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(source)) throw new IllegalArgumentException("PROGRAM_SOURCE_ROOT_INVALID");
        return source;
    }

    private static String id(JsonNode request, String field) {
        String value = request.path(field).asText();
        if (!value.matches("[A-Za-z0-9._-]{1,128}")) throw new IllegalArgumentException("PROGRAM_ID_INVALID:" + field);
        return value;
    }

    private static String text(JsonNode request, String field, int maximum) {
        String value = request.path(field).asText();
        if (value.isBlank() || value.length() > maximum) throw new IllegalArgumentException("PROGRAM_TEXT_INVALID:" + field);
        return value;
    }

    private record TreeObservation(String digest, long fileCount, long byteCount) {}
    private record SourceAnalysis(long pomCount, long moduleCount, long javaMainCount, long javaTestCount,
                                  long publicTypeCount, long gitDirtyCount, boolean licensePresent, long totalFiles) {}
}
