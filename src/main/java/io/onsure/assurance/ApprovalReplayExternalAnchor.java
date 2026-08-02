package io.onsure.assurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Append-only replay-ledger head history stored outside the mutable approval authority root. */
final class ApprovalReplayExternalAnchor {
    static final String CONTRACT = "ONSURE_APPROVAL_REPLAY_EXTERNAL_ANCHOR_V1";
    private static final String GENESIS = "0".repeat(64);

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Path anchorFile;
    private final Path anchorLock;

    ApprovalReplayExternalAnchor(Path anchorFile) {
        this.anchorFile = anchorFile.toAbsolutePath().normalize();
        this.anchorLock = this.anchorFile.resolveSibling(this.anchorFile.getFileName() + ".lock");
        requireNoSymlink(this.anchorFile, "APPROVAL_REPLAY_EXTERNAL_ANCHOR_SYMLINK_PROHIBITED");
    }

    static Path derive(Path replayLedger) {
        Path ledger = replayLedger.toAbsolutePath().normalize();
        Path authorityRoot = ledger.getParent();
        if (authorityRoot == null || authorityRoot.getParent() == null) {
            throw new IllegalArgumentException("APPROVAL_REPLAY_AUTHORITY_ROOT_INVALID");
        }
        Path externalRoot = authorityRoot.getParent().resolve("approval-replay-external-anchors");
        return externalRoot.resolve(sha256(authorityRoot.toString()) + ".jsonl");
    }

    void verify(long ledgerSequence, String ledgerHead, String ledgerSha256) throws Exception {
        ExclusiveFileLock.run(anchorLock,
                () -> verifyUnlocked(ledgerSequence, ledgerHead, ledgerSha256));
    }

    void append(long ledgerSequence, String ledgerHead, String ledgerSha256) throws Exception {
        ExclusiveFileLock.run(anchorLock, () -> {
            AnchorState previous = readAndVerifyAnchorChain();
            if (previous.ledgerSequence() != ledgerSequence - 1) {
                throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_SEQUENCE_STALE");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("contract", CONTRACT);
            entry.put("anchor_sequence", previous.anchorSequence() + 1);
            entry.put("ledger_sequence", ledgerSequence);
            entry.put("ledger_head", ledgerHead);
            entry.put("ledger_sha256", ledgerSha256);
            entry.put("anchored_at", Instant.now().toString());
            entry.put("previous_anchor_hash", previous.anchorHash());
            entry.put("anchor_hash", canonicalHash(entry));
            requireNoSymlink(anchorFile, "APPROVAL_REPLAY_EXTERNAL_ANCHOR_SYMLINK_PROHIBITED");
            Files.createDirectories(anchorFile.getParent());
            Files.writeString(anchorFile, mapper.writeValueAsString(entry) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        });
    }

    private void verifyUnlocked(long ledgerSequence, String ledgerHead, String ledgerSha256)
            throws Exception {
        AnchorState state = readAndVerifyAnchorChain();
        if (ledgerSequence == 0 && state.anchorSequence() == 0) return;
        if (state.anchorSequence() == 0) {
            throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_MISSING");
        }
        if (state.ledgerSequence() != ledgerSequence) {
            throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_SEQUENCE_MISMATCH");
        }
        if (!state.ledgerHead().equals(ledgerHead)) {
            throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_HEAD_MISMATCH");
        }
        if (!state.ledgerSha256().equals(ledgerSha256)) {
            throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_LEDGER_MISMATCH");
        }
    }

    private AnchorState readAndVerifyAnchorChain() throws Exception {
        requireNoSymlink(anchorFile, "APPROVAL_REPLAY_EXTERNAL_ANCHOR_SYMLINK_PROHIBITED");
        if (!Files.exists(anchorFile, LinkOption.NOFOLLOW_LINKS)) {
            return new AnchorState(0, 0, GENESIS, GENESIS, GENESIS);
        }
        if (!Files.isRegularFile(anchorFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_INVALID");
        }
        List<String> lines = Files.readAllLines(anchorFile, StandardCharsets.UTF_8);
        String previous = GENESIS;
        long anchorSequence = 0;
        long ledgerSequence = 0;
        String ledgerHead = GENESIS;
        String ledgerSha = GENESIS;
        for (String line : lines) {
            if (line.isBlank()) {
                throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_BLANK");
            }
            JsonNode node = mapper.readTree(line);
            if (node == null || !node.isObject() || !CONTRACT.equals(node.path("contract").asText())) {
                throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_CONTRACT_INVALID");
            }
            if (node.path("anchor_sequence").asLong(-1) != ++anchorSequence) {
                throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_CHAIN_BROKEN");
            }
            if (!previous.equals(node.path("previous_anchor_hash").asText())) {
                throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_CHAIN_BROKEN");
            }
            Map<String, Object> entry = mapper.convertValue(node, Map.class);
            String declared = String.valueOf(entry.get("anchor_hash"));
            if (!declared.equals(canonicalHash(entry))) {
                throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_TAMPERED");
            }
            long nextLedgerSequence = node.path("ledger_sequence").asLong(-1);
            if (nextLedgerSequence != ledgerSequence + 1) {
                throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_LEDGER_SEQUENCE_BROKEN");
            }
            ledgerSequence = nextLedgerSequence;
            ledgerHead = requireDigest(node.path("ledger_head").asText(),
                    "APPROVAL_REPLAY_EXTERNAL_ANCHOR_HEAD_INVALID");
            ledgerSha = requireDigest(node.path("ledger_sha256").asText(),
                    "APPROVAL_REPLAY_EXTERNAL_ANCHOR_LEDGER_DIGEST_INVALID");
            previous = declared;
        }
        return new AnchorState(anchorSequence, ledgerSequence, ledgerHead, ledgerSha, previous);
    }

    private String canonicalHash(Map<String, Object> entry) throws Exception {
        Map<String, Object> canonical = new TreeMap<>(entry);
        canonical.remove("anchor_hash");
        return sha256(mapper.writeValueAsBytes(canonical));
    }

    private static String requireDigest(String value, String code) {
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalStateException(code);
        return value;
    }

    private static void requireNoSymlink(Path path, String code) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(code);
            }
        }
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value));
        } catch (Exception failure) {
            throw new IllegalStateException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_HASH_FAILED", failure);
        }
    }

    private record AnchorState(
            long anchorSequence, long ledgerSequence, String ledgerHead,
            String ledgerSha256, String anchorHash) {}
}
