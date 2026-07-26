package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** File-backed product catalog with organization isolation and cross-process locking. */
public final class ProductCatalog {
    public static final String LOCAL_ORGANIZATION = "LOCAL_SINGLE_TENANT";

    public record Workspace(
            String organizationId, String workspaceId, String name, Instant createdAt) {
        public Workspace {
            requireId(organizationId, "organizationId");
            requireId(workspaceId, "workspaceId");
            requireText(name, "name");
            Objects.requireNonNull(createdAt, "createdAt");
        }

        public Workspace(String workspaceId, String name, Instant createdAt) {
            this(LOCAL_ORGANIZATION, workspaceId, name, createdAt);
        }
    }

    public record Project(
            String organizationId, String projectId, String workspaceId,
            String name, Instant createdAt) {
        public Project {
            requireId(organizationId, "organizationId");
            requireId(projectId, "projectId");
            requireId(workspaceId, "workspaceId");
            requireText(name, "name");
            Objects.requireNonNull(createdAt, "createdAt");
        }

        public Project(String projectId, String workspaceId, String name, Instant createdAt) {
            this(LOCAL_ORGANIZATION, projectId, workspaceId, name, createdAt);
        }
    }

    public record RegisteredTarget(
            String organizationId, String projectId,
            ValidationTarget target, Instant registeredAt) {
        public RegisteredTarget {
            requireId(organizationId, "organizationId");
            requireId(projectId, "projectId");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(registeredAt, "registeredAt");
        }

        public RegisteredTarget(String projectId, ValidationTarget target, Instant registeredAt) {
            this(LOCAL_ORGANIZATION, projectId, target, registeredAt);
        }
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;
    private final Path lockFile;

    public ProductCatalog(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.lockFile = this.root.resolve(".catalog.lock");
    }

    public void registerWorkspace(Workspace workspace) throws Exception {
        withLock(() -> {
            List<Workspace> values = read("workspaces.json", new TypeReference<>() {});
            ensureUnique(values.stream()
                    .map(value -> value.organizationId() + ":" + value.workspaceId()).toList(),
                    workspace.organizationId() + ":" + workspace.workspaceId(),
                    "WORKSPACE_EXISTS");
            values.add(workspace);
            write("workspaces.json", values);
            return null;
        });
    }

    public void registerProject(Project project) throws Exception {
        withLock(() -> {
            List<Workspace> workspaces = read("workspaces.json", new TypeReference<>() {});
            if (workspaces.stream().noneMatch(value ->
                    value.organizationId().equals(project.organizationId())
                            && value.workspaceId().equals(project.workspaceId()))) {
                throw new IllegalArgumentException("UNKNOWN_OR_CROSS_TENANT_WORKSPACE");
            }
            List<Project> values = read("projects.json", new TypeReference<>() {});
            ensureUnique(values.stream()
                    .map(value -> value.organizationId() + ":" + value.projectId()).toList(),
                    project.organizationId() + ":" + project.projectId(), "PROJECT_EXISTS");
            values.add(project);
            write("projects.json", values);
            return null;
        });
    }

    public void registerTarget(RegisteredTarget target) throws Exception {
        withLock(() -> {
            List<Project> projects = read("projects.json", new TypeReference<>() {});
            if (projects.stream().noneMatch(value ->
                    value.organizationId().equals(target.organizationId())
                            && value.projectId().equals(target.projectId()))) {
                throw new IllegalArgumentException("UNKNOWN_OR_CROSS_TENANT_PROJECT");
            }
            List<RegisteredTarget> values = read("targets.json", new TypeReference<>() {});
            ensureUnique(values.stream()
                    .map(value -> value.organizationId() + ":" + value.target().targetId()).toList(),
                    target.organizationId() + ":" + target.target().targetId(), "TARGET_EXISTS");
            values.add(target);
            write("targets.json", values);
            return null;
        });
    }

    public ValidationTarget requireTarget(String targetId) throws Exception {
        return requireTarget(LOCAL_ORGANIZATION, targetId);
    }

    public ValidationTarget requireTarget(String organizationId, String targetId) throws Exception {
        requireId(organizationId, "organizationId");
        requireId(targetId, "targetId");
        return withLock(() -> read("targets.json",
                new TypeReference<List<RegisteredTarget>>() {}).stream()
                .filter(value -> value.organizationId().equals(organizationId))
                .map(RegisteredTarget::target)
                .filter(value -> value.targetId().equals(targetId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("UNKNOWN_TARGET")));
    }

    public List<RegisteredTarget> targets(String projectId) throws Exception {
        return targets(LOCAL_ORGANIZATION, projectId);
    }

    public List<RegisteredTarget> targets(String organizationId, String projectId) throws Exception {
        requireId(organizationId, "organizationId");
        requireId(projectId, "projectId");
        return withLock(() -> read("targets.json",
                new TypeReference<List<RegisteredTarget>>() {}).stream()
                .filter(value -> value.organizationId().equals(organizationId))
                .filter(value -> value.projectId().equals(projectId)).toList());
    }

    private <T> List<T> read(String name, TypeReference<List<T>> type) throws Exception {
        Path file = root.resolve(name);
        if (!Files.exists(file)) return new ArrayList<>();
        return new ArrayList<>(mapper.readValue(file.toFile(), type));
    }

    private void write(String name, Object value) throws Exception {
        Files.createDirectories(root);
        Path file = root.resolve(name);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        byte[] content = mapper.writeValueAsBytes(value);
        try (FileChannel channel = FileChannel.open(temporary,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(content));
            channel.force(true);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private <T> T withLock(CheckedSupplier<T> action) throws Exception {
        Files.createDirectories(root);
        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            return action.get();
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static void ensureUnique(List<String> existing, String candidate, String code) {
        if (existing.contains(candidate)) throw new IllegalArgumentException(code);
    }

    private static void requireId(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException(name);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
    }
}
