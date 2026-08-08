package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Install/update/rollback orchestration on top of {@link DeploymentPackageBuilder}'s signed
 * packages (DEPLOYMENT: NO_UPDATE_OR_ROLLBACK_ORCHESTRATION_ACROSS_PACKAGE_VERSIONS). Every
 * installed version is kept as its own verified copy under {@code <installRoot>/versions/<id>/};
 * "active version" is just the last entry of an append-only ledger, so rollback re-verifies and
 * reactivates a prior version's exact archived files by hash rather than trying to reconstruct
 * them from a diff.
 */
public final class DeploymentInstallationService {
    public static final String LEDGER_CONTRACT = "ONSURE_DEPLOYMENT_INSTALLATION_LEDGER_V1";

    public record InstalledVersion(String version, String manifestSha256, boolean signed, String at) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path installRoot;
    private final Path versionsRoot;
    private final Path ledgerFile;

    public DeploymentInstallationService(Path installRoot) {
        this.installRoot = Objects.requireNonNull(installRoot, "installRoot").toAbsolutePath().normalize();
        requireNoSymlink(this.installRoot, "DEPLOYMENT_INSTALL_ROOT_SYMLINK_PROHIBITED");
        this.versionsRoot = this.installRoot.resolve("versions");
        this.ledgerFile = this.installRoot.resolve("ledger.jsonl");
    }

    public InstalledVersion install(Path packageDir, String version, PublicKey verificationKey) throws Exception {
        requireVersionId(version);
        DeploymentPackageBuilder.VerifyResult verifyResult = DeploymentPackageBuilder.verify(packageDir, verificationKey);
        if (!verifyResult.integrityValid()) {
            throw new IllegalStateException("DEPLOYMENT_INSTALL_PACKAGE_INTEGRITY_INVALID:" + verifyResult.violations());
        }
        if (verifyResult.signed() && !verifyResult.signatureValid()) {
            throw new IllegalStateException("DEPLOYMENT_INSTALL_PACKAGE_SIGNATURE_INVALID:" + version);
        }

        Path versionDir = versionsRoot.resolve(version);
        if (Files.exists(versionDir, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("DEPLOYMENT_VERSION_ALREADY_INSTALLED:" + version);
        }
        copyTree(packageDir, versionDir);

        InstalledVersion installed = new InstalledVersion(
                version, Hashing.file(versionDir.resolve("deployment-package-manifest.json")),
                verifyResult.signed(), Instant.now().toString());
        appendLedgerEntry("INSTALL", installed);
        return installed;
    }

    public InstalledVersion rollback(String targetVersion, PublicKey verificationKey) throws Exception {
        requireVersionId(targetVersion);
        Path versionDir = versionsRoot.resolve(targetVersion);
        if (!Files.isDirectory(versionDir, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("DEPLOYMENT_ROLLBACK_VERSION_NOT_FOUND:" + targetVersion);
        }
        DeploymentPackageBuilder.VerifyResult verifyResult = DeploymentPackageBuilder.verify(versionDir, verificationKey);
        if (!verifyResult.integrityValid()) {
            throw new IllegalStateException("DEPLOYMENT_ROLLBACK_INTEGRITY_INVALID:" + verifyResult.violations());
        }
        if (verifyResult.signed() && !verifyResult.signatureValid()) {
            throw new IllegalStateException("DEPLOYMENT_ROLLBACK_SIGNATURE_INVALID:" + targetVersion);
        }

        InstalledVersion rolledBack = new InstalledVersion(
                targetVersion, Hashing.file(versionDir.resolve("deployment-package-manifest.json")),
                verifyResult.signed(), Instant.now().toString());
        appendLedgerEntry("ROLLBACK", rolledBack);
        return rolledBack;
    }

    public String activeVersion() throws Exception {
        List<Map<String, Object>> entries = history();
        if (entries.isEmpty()) throw new IllegalStateException("DEPLOYMENT_NO_VERSION_INSTALLED");
        return String.valueOf(entries.get(entries.size() - 1).get("version"));
    }

    public Path activeInstallationPath() throws Exception {
        return versionsRoot.resolve(activeVersion());
    }

    public List<Map<String, Object>> history() throws Exception {
        if (!Files.isRegularFile(ledgerFile)) return List.of();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String line : Files.readAllLines(ledgerFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode node = MAPPER.readTree(line);
            Map<String, Object> entry = new LinkedHashMap<>();
            node.fields().forEachRemaining(field -> entry.put(field.getKey(), field.getValue().asText()));
            entries.add(entry);
        }
        return List.copyOf(entries);
    }

    private void appendLedgerEntry(String event, InstalledVersion version) throws Exception {
        Files.createDirectories(installRoot);
        requireNoSymlink(ledgerFile, "DEPLOYMENT_LEDGER_SYMLINK_PROHIBITED");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("contract", LEDGER_CONTRACT);
        entry.put("event", event);
        entry.put("version", version.version());
        entry.put("manifest_sha256", version.manifestSha256());
        entry.put("signed", version.signed());
        entry.put("at", version.at());
        String line = MAPPER.writeValueAsString(entry);
        Files.writeString(ledgerFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void copyTree(Path source, Path destination) throws Exception {
        Path normalizedSource = source.toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(normalizedSource)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(path -> Hashing.relative(normalizedSource, path)))
                    .forEach(files::add);
        }
        for (Path file : files) {
            Path target = destination.resolve(Hashing.relative(normalizedSource, file));
            Files.createDirectories(target.getParent());
            Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void requireVersionId(String version) {
        if (version == null || !version.matches("[A-Za-z0-9._-]{1,80}")) {
            throw new IllegalArgumentException("DEPLOYMENT_VERSION_ID_INVALID");
        }
    }

    private static void requireNoSymlink(Path path, String code) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(code);
            }
        }
    }
}
