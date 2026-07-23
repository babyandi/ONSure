package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** File-backed commercial product catalog for workspaces, projects and targets. */
public final class ProductCatalog {
    public record Workspace(String workspaceId, String name, Instant createdAt) {
        public Workspace {
            requireId(workspaceId, "workspaceId");
            requireText(name, "name");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record Project(String projectId, String workspaceId, String name, Instant createdAt) {
        public Project {
            requireId(projectId, "projectId");
            requireId(workspaceId, "workspaceId");
            requireText(name, "name");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record RegisteredTarget(String projectId, ValidationTarget target, Instant registeredAt) {
        public RegisteredTarget {
            requireId(projectId, "projectId");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(registeredAt, "registeredAt");
        }
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public ProductCatalog(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public synchronized void registerWorkspace(Workspace workspace) throws Exception {
        List<Workspace> values = read("workspaces.json", new TypeReference<>() {});
        ensureUnique(values.stream().map(Workspace::workspaceId).toList(), workspace.workspaceId(), "WORKSPACE_EXISTS");
        values.add(workspace);
        write("workspaces.json", values);
    }

    public synchronized void registerProject(Project project) throws Exception {
        List<Workspace> workspaces = read("workspaces.json", new TypeReference<>() {});
        if (workspaces.stream().noneMatch(value -> value.workspaceId().equals(project.workspaceId()))) {
            throw new IllegalArgumentException("UNKNOWN_WORKSPACE");
        }
        List<Project> values = read("projects.json", new TypeReference<>() {});
        ensureUnique(values.stream().map(Project::projectId).toList(), project.projectId(), "PROJECT_EXISTS");
        values.add(project);
        write("projects.json", values);
    }

    public synchronized void registerTarget(RegisteredTarget target) throws Exception {
        List<Project> projects = read("projects.json", new TypeReference<>() {});
        if (projects.stream().noneMatch(value -> value.projectId().equals(target.projectId()))) {
            throw new IllegalArgumentException("UNKNOWN_PROJECT");
        }
        List<RegisteredTarget> values = read("targets.json", new TypeReference<>() {});
        ensureUnique(values.stream().map(value -> value.target().targetId()).toList(),
                target.target().targetId(), "TARGET_EXISTS");
        values.add(target);
        write("targets.json", values);
    }

    public synchronized ValidationTarget requireTarget(String targetId) throws Exception {
        return read("targets.json", new TypeReference<List<RegisteredTarget>>() {}).stream()
                .map(RegisteredTarget::target)
                .filter(value -> value.targetId().equals(targetId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("UNKNOWN_TARGET"));
    }

    public synchronized List<RegisteredTarget> targets(String projectId) throws Exception {
        return read("targets.json", new TypeReference<List<RegisteredTarget>>() {}).stream()
                .filter(value -> value.projectId().equals(projectId)).toList();
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
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureUnique(List<String> existing, String candidate, String code) {
        if (existing.contains(candidate)) throw new IllegalArgumentException(code);
    }

    private static void requireId(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) throw new IllegalArgumentException(name);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
    }
}
