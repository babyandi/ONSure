package io.onsure.platform.oruda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.Decision;
import io.onsure.assurance.ValidationResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Imports the 87 ORUDA source documents into immutable package-oriented materialization. */
public final class OrudaDocumentMaterializer {
    public static final String CONTRACT = "ONSURE_ORUDA_DOCUMENT_MATERIALIZATION_V1";
    public static final String MANIFEST_FILE = "oruda-document-materialization.json";

    public record DocumentEntry(
            String filename,
            String relativePath,
            long sizeBytes,
            String sha256) {}

    public record PackageDocuments(String packageId, List<DocumentEntry> documents) {
        public PackageDocuments { documents = List.copyOf(documents); }
    }

    public record Manifest(
            String contract,
            String materializationId,
            Instant generatedAt,
            String catalogSha256,
            int documentCount,
            String documentSetDigest,
            List<PackageDocuments> packages) {
        public Manifest { packages = List.copyOf(packages); }
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Manifest materialize(Path sourceDirectory, Path catalogFile, Path outputDirectory) throws Exception {
        Path source = requireDirectory(sourceDirectory, "ORUDA_DOCUMENT_SOURCE_DIRECTORY_MISSING");
        Path output = outputDirectory.toAbsolutePath().normalize();
        if (Files.exists(output)) throw new IllegalArgumentException("ORUDA_MATERIALIZATION_OUTPUT_ALREADY_EXISTS");
        if (output.getParent() == null) throw new IllegalArgumentException("ORUDA_MATERIALIZATION_OUTPUT_PARENT_MISSING");
        Files.createDirectories(output.getParent());
        Path staging = output.resolveSibling(output.getFileName() + ".tmp-" + UUID.randomUUID());

        try {
            Files.createDirectories(staging);
            OrudaExecutionPackageCatalog.Catalog catalog = new OrudaExecutionPackageCatalog().load(catalogFile);
            List<PackageDocuments> packages = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (OrudaExecutionPackageCatalog.ExecutionPackage executionPackage : catalog.packages()) {
                List<String> filenames = new ArrayList<>();
                filenames.addAll(executionPackage.loopDocuments());
                filenames.addAll(executionPackage.supportingDocuments());
                filenames.sort(String::compareTo);
                List<DocumentEntry> documents = new ArrayList<>();
                for (String filename : filenames) {
                    if (!seen.add(filename)) throw new IllegalArgumentException("ORUDA_MATERIALIZATION_DUPLICATE_DOCUMENT:" + filename);
                    Path sourceFile = safeDocumentPath(source, filename);
                    if (!Files.isRegularFile(sourceFile) || Files.isSymbolicLink(sourceFile)) {
                        throw new IllegalArgumentException("ORUDA_MATERIALIZATION_SOURCE_DOCUMENT_MISSING:" + filename);
                    }
                    long size = Files.size(sourceFile);
                    if (size <= 0) throw new IllegalArgumentException("ORUDA_MATERIALIZATION_EMPTY_DOCUMENT:" + filename);
                    Path destination = staging.resolve("packages")
                            .resolve(executionPackage.packageId()).resolve("documents").resolve(filename).normalize();
                    if (!destination.startsWith(staging)) throw new IllegalArgumentException("ORUDA_MATERIALIZATION_PATH_ESCAPE");
                    Files.createDirectories(destination.getParent());
                    Files.copy(sourceFile, destination, StandardCopyOption.COPY_ATTRIBUTES);
                    documents.add(new DocumentEntry(
                            filename,
                            relative(staging, destination),
                            size,
                            sha256(destination)));
                }
                packages.add(new PackageDocuments(executionPackage.packageId(), documents));
            }
            if (seen.size() != catalog.totalDocuments()) {
                throw new IllegalArgumentException("ORUDA_MATERIALIZATION_DOCUMENT_COUNT_MISMATCH");
            }

            String setDigest = documentSetDigest(packages);
            Manifest manifest = new Manifest(
                    CONTRACT,
                    "ORUDA-MATERIALIZATION-" + setDigest.substring(0, 24),
                    Instant.now(),
                    sha256(catalogFile),
                    seen.size(),
                    setDigest,
                    packages);
            mapper.writeValue(staging.resolve(MANIFEST_FILE).toFile(), manifest);
            moveDirectory(staging, output);
            ValidationResult verification = verify(output, catalogFile);
            if (verification.decision() != Decision.PASS) {
                deleteRecursively(output);
                throw new IllegalStateException("ORUDA_MATERIALIZATION_VERIFY_FAIL " + verification.violations());
            }
            return manifest;
        } catch (Exception e) {
            deleteRecursively(staging);
            throw e;
        }
    }

    public ValidationResult verify(Path outputDirectory, Path catalogFile) {
        List<String> violations = new ArrayList<>();
        try {
            Path output = requireDirectory(outputDirectory, "ORUDA_MATERIALIZATION_DIRECTORY_MISSING");
            Path manifestFile = output.resolve(MANIFEST_FILE);
            if (!Files.isRegularFile(manifestFile)) {
                return ValidationResult.fail(List.of("ORUDA_MATERIALIZATION_MANIFEST_MISSING"));
            }
            Manifest manifest = mapper.readValue(manifestFile.toFile(), Manifest.class);
            OrudaExecutionPackageCatalog.Catalog catalog = new OrudaExecutionPackageCatalog().load(catalogFile);
            if (!CONTRACT.equals(manifest.contract())) violations.add("ORUDA_MATERIALIZATION_CONTRACT_MISMATCH");
            if (!Objects.equals(sha256(catalogFile), manifest.catalogSha256())) {
                violations.add("ORUDA_MATERIALIZATION_CATALOG_HASH_MISMATCH");
            }
            if (manifest.documentCount() != catalog.totalDocuments()) {
                violations.add("ORUDA_MATERIALIZATION_DOCUMENT_COUNT_MISMATCH");
            }

            Map<String, OrudaExecutionPackageCatalog.ExecutionPackage> expectedPackages = new LinkedHashMap<>();
            for (var value : catalog.packages()) expectedPackages.put(value.packageId(), value);
            Map<String, PackageDocuments> actualPackages = new LinkedHashMap<>();
            for (PackageDocuments value : manifest.packages()) {
                if (actualPackages.put(value.packageId(), value) != null) {
                    violations.add("ORUDA_MATERIALIZATION_DUPLICATE_PACKAGE:" + value.packageId());
                }
            }
            if (!actualPackages.keySet().equals(expectedPackages.keySet())) {
                violations.add("ORUDA_MATERIALIZATION_PACKAGE_SET_MISMATCH");
            }

            Set<String> actualDocumentPaths = new HashSet<>();
            for (Map.Entry<String, OrudaExecutionPackageCatalog.ExecutionPackage> expected : expectedPackages.entrySet()) {
                PackageDocuments actual = actualPackages.get(expected.getKey());
                if (actual == null) continue;
                Set<String> expectedNames = new HashSet<>();
                expectedNames.addAll(expected.getValue().loopDocuments());
                expectedNames.addAll(expected.getValue().supportingDocuments());
                Map<String, DocumentEntry> entries = new LinkedHashMap<>();
                for (DocumentEntry entry : actual.documents()) {
                    if (entries.put(entry.filename(), entry) != null) {
                        violations.add("ORUDA_MATERIALIZATION_DUPLICATE_DOCUMENT:" + entry.filename());
                    }
                    Path expectedPath = output.resolve("packages").resolve(expected.getKey())
                            .resolve("documents").resolve(entry.filename()).normalize();
                    Path declared = output.resolve(entry.relativePath()).normalize();
                    if (!declared.equals(expectedPath) || !declared.startsWith(output)) {
                        violations.add("ORUDA_MATERIALIZATION_DOCUMENT_PATH_MISMATCH:" + entry.filename());
                        continue;
                    }
                    actualDocumentPaths.add(relative(output, declared));
                    if (!Files.isRegularFile(declared) || Files.isSymbolicLink(declared)) {
                        violations.add("ORUDA_MATERIALIZATION_DOCUMENT_MISSING:" + entry.filename());
                    } else {
                        if (entry.sizeBytes() != Files.size(declared)) {
                            violations.add("ORUDA_MATERIALIZATION_SIZE_MISMATCH:" + entry.filename());
                        }
                        if (!Objects.equals(entry.sha256(), sha256(declared))) {
                            violations.add("ORUDA_MATERIALIZATION_HASH_MISMATCH:" + entry.filename());
                        }
                    }
                }
                if (!entries.keySet().equals(expectedNames)) {
                    violations.add("ORUDA_MATERIALIZATION_DOCUMENT_SET_MISMATCH:" + expected.getKey());
                }
            }
            Set<String> physicalDocumentPaths = new HashSet<>();
            try (var stream = Files.walk(output.resolve("packages"))) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".md"))
                        .map(path -> relative(output, path))
                        .forEach(physicalDocumentPaths::add);
            }
            if (!physicalDocumentPaths.equals(actualDocumentPaths)) {
                violations.add("ORUDA_MATERIALIZATION_PHYSICAL_FILE_SET_MISMATCH");
            }
            String expectedSetDigest = documentSetDigest(manifest.packages());
            if (!Objects.equals(expectedSetDigest, manifest.documentSetDigest())) {
                violations.add("ORUDA_MATERIALIZATION_SET_DIGEST_MISMATCH");
            }
            if (!Objects.equals("ORUDA-MATERIALIZATION-" + manifest.documentSetDigest().substring(0, 24),
                    manifest.materializationId())) {
                violations.add("ORUDA_MATERIALIZATION_ID_MISMATCH");
            }
        } catch (Exception e) {
            violations.add("ORUDA_MATERIALIZATION_UNREADABLE:" + e.getClass().getSimpleName());
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private static String documentSetDigest(List<PackageDocuments> packages) throws Exception {
        StringBuilder canonical = new StringBuilder();
        packages.stream().sorted(Comparator.comparing(PackageDocuments::packageId)).forEach(value ->
                value.documents().stream().sorted(Comparator.comparing(DocumentEntry::filename)).forEach(document ->
                        canonical.append(value.packageId()).append('|').append(document.filename()).append('|')
                                .append(document.sizeBytes()).append('|').append(document.sha256()).append('\n')));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Path safeDocumentPath(Path source, String filename) {
        if (filename == null || !filename.matches("oruda_[A-Za-z0-9_()-]+\\.md")) {
            throw new IllegalArgumentException("ORUDA_MATERIALIZATION_FILENAME_INVALID:" + filename);
        }
        Path file = source.resolve(filename).normalize();
        if (!file.startsWith(source)) throw new IllegalArgumentException("ORUDA_MATERIALIZATION_SOURCE_PATH_ESCAPE");
        return file;
    }

    private static Path requireDirectory(Path path, String error) {
        if (path == null) throw new IllegalArgumentException(error);
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static void moveDirectory(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String sha256(Path file) throws Exception {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
