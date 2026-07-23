package io.onsure.assurance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalAgentMain {
    public static final String CONTRACT = "ONSURE_LOCAL_AGENT_RECEIPT_V1";

    private LocalAgentMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 9) {
            System.err.println("usage: LocalAgentMain <OTESTER|OAUDIT> <agent-run-id> <input-digest> <key-id> <private-key-file> <output-file> <role-policy> <evidence-scope> <run-context-file>");
            System.exit(64);
        }
        String authority = args[0];
        if (!authority.equals("OTESTER") && !authority.equals("OAUDIT")) {
            System.err.println("unsupported authority");
            System.exit(65);
        }
        if (!args[2].matches("[0-9a-f]{64}")) {
            System.err.println("invalid input digest");
            System.exit(66);
        }
        if (args[3].isBlank()) {
            System.err.println("missing key id");
            System.exit(67);
        }
        if (!LocalRolePolicy.expectedPolicy(authority).equals(args[6])
                || !LocalRolePolicy.expectedScope(authority).equals(args[7])) {
            System.err.println("authority policy or scope mismatch");
            System.exit(68);
        }
        Path runContextFile = Path.of(args[8]).toAbsolutePath().normalize();
        LocalRunContext contextReader = new LocalRunContext();
        ValidationResult contextValidation = contextReader.verify(runContextFile);
        if (contextValidation.decision() != Decision.PASS) {
            System.err.println("invalid run context " + contextValidation.violations());
            System.exit(69);
        }
        LocalRunContext.Context context = LocalRunContext.read(runContextFile);
        Instant createdAt = Instant.now();
        if (createdAt.isBefore(context.startedAt())) {
            System.err.println("agent time precedes run start");
            System.exit(70);
        }

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", CONTRACT);
        receipt.put("authority", authority);
        receipt.put("run_id", args[1]);
        receipt.put("assurance_run_id", context.runId());
        receipt.put("run_started_at", context.startedAt().toString());
        receipt.put("input_digest", args[2]);
        receipt.put("decision", "PASS");
        receipt.put("created_at", createdAt.toString());
        receipt.put("execution_mode", "LOCAL_SEPARATE_JVM");
        receipt.put("role_policy", args[6]);
        receipt.put("evidence_scope", args[7]);
        receipt.put("key_id", args[3]);
        receipt.put("signature_algorithm", "Ed25519");
        receipt.put("signature", LocalReceiptCrypto.sign(receipt,
                LocalReceiptCrypto.readPrivateKey(Path.of(args[4]))));

        Path output = Path.of(args[5]);
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), receipt);
        try {
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
