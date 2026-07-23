package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Path;

/** Minimal product CLI for target validation and report generation. */
public final class ONSureCli {
    private ONSureCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 10 || !"validate".equals(args[0])) {
            System.err.println("usage: ONSureCli validate <source-root> <target-id> <target-name> "
                    + "<GENERAL_SOFTWARE|AI_APPLICATION|AI_AGENTIC_PLATFORM> <adapter-id> "
                    + "<immutable-source-ref> <policy-profile> <execution-profile> <store-root>");
            System.exit(64);
        }
        Path sourceRoot = Path.of(args[1]).toAbsolutePath().normalize();
        ValidationTarget target = new ValidationTarget(
                args[2], args[3], TargetType.valueOf(args[4]), sourceRoot,
                args[6], args[5], args[7], args[8]);
        ValidationEngine.RunResult result = ValidationEngine.defaultEngine(Path.of(args[9])).run(target);
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
        System.out.println(mapper.writeValueAsString(result.report()));
        System.out.println("ONSURE_VALIDATION_COMPLETE " + result.runRoot());
    }
}
