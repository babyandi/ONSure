package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Local CLI for validation compatibility and the complete nonfinal workflow dispatcher. */
public final class ONSureCli {
    private ONSureCli() {}

    public static void main(String[] args) throws Exception {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) throws Exception {
        if (args.length == 4 && "workflow".equals(args[0])) {
            Path workspace = Path.of(args[1]).toAbsolutePath().normalize();
            String operation = args[2];
            Path requestFile = Path.of(args[3]).toAbsolutePath().normalize();
            if (!Files.isRegularFile(requestFile, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(requestFile)) {
                err.println("ONSURE_CLI_REQUEST_FILE_INVALID");
                return 64;
            }
            ObjectMapper mapper = mapper();
            JsonNode request = mapper.readTree(requestFile.toFile());
            var result = new PostFinalTargetWorkflowDispatcher(
                            workspace, AuthenticatedWorkflowIdentity.localAdministrator())
                    .dispatch(operation, request);
            out.println(mapper.writeValueAsString(result));
            out.println("ONSURE_WORKFLOW_COMPLETE_NONFINAL " + operation);
            return 0;
        }
        if (args.length == 10 && "validate".equals(args[0])) {
            return legacyValidate(args, out);
        }
        err.println("usage:");
        err.println("  ONSureCli workflow <workspace-root> <operation> <request-json-file>");
        err.println("  ONSureCli validate <source-root> <target-id> <target-name> "
                + "<GENERAL_SOFTWARE|AI_APPLICATION|AI_AGENTIC_PLATFORM> <adapter-id> "
                + "<immutable-source-ref> <policy-profile> <execution-profile> <store-root>");
        return 64;
    }

    private static int legacyValidate(String[] args, PrintStream out) throws Exception {
        Path sourceRoot = Path.of(args[1]).toAbsolutePath().normalize();
        ValidationTarget target = new ValidationTarget(
                args[2], args[3], TargetType.valueOf(args[4]), sourceRoot,
                args[6], args[5], args[7], args[8]);
        ValidationEngine.RunResult result = ValidationEngine.defaultEngine(Path.of(args[9])).run(target);
        out.println(mapper().writeValueAsString(result.report()));
        out.println("ONSURE_VALIDATION_COMPLETE_NONFINAL " + result.runRoot());
        return result.report().decision() == kr.co.oruda.onsure.assurance.Decision.PASS ? 0 : 2;
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }
}
