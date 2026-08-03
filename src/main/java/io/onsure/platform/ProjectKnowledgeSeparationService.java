package io.onsure.platform;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Deterministically anonymizes project facts and keeps reusable knowledge opt-in and review-gated. */
final class ProjectKnowledgeSeparationService {
    static final String CONTRACT = "ONSURE_PROJECT_KNOWLEDGE_SEPARATION_V1";
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(?:api[_-]?key|access[_-]?token|token|secret|password)\\s*[:=]\\s*[^\\s,;]+", Pattern.UNICODE_CASE);
    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final String IPV4_OCTET = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![0-9])" + IPV4_OCTET + "(?:\\." + IPV4_OCTET + "){3}(?![0-9])");
    private static final Pattern UNIX_PATH = Pattern.compile("(?<![A-Za-z0-9_.-])/(?:[^\\s/,;]+/)*[^\\s,;]+");
    private static final Pattern WINDOWS_PATH = Pattern.compile("(?i)[A-Z]:\\\\(?:[^\\s\\\\]+\\\\)*[^\\s,;]+");

    record Result(
            String contract,
            String projectIdToken,
            String sourceDigest,
            Map<String, String> anonymizedProjectKnowledge,
            Map<String, String> commonKnowledgeCandidates,
            List<String> redactionCategories,
            boolean automatedCommonPromotionAllowed,
            boolean humanReviewRequired,
            boolean finalClaimAllowed) {}

    Result separate(String projectId, Map<String, String> knowledge, byte[] workspaceSalt)
            throws GeneralSecurityException {
        if (projectId == null || projectId.isBlank() || projectId.length() > 256) throw new IllegalArgumentException("projectId");
        if (knowledge == null || knowledge.isEmpty() || knowledge.size() > 1000) throw new IllegalArgumentException("knowledge");
        if (workspaceSalt == null || workspaceSalt.length < 32) throw new IllegalArgumentException("workspaceSalt");
        Map<String, String> anonymized = new LinkedHashMap<>();
        Map<String, String> common = new LinkedHashMap<>();
        Set<String> categories = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : knowledge.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getKey().length() > 256
                    || entry.getValue() == null || entry.getValue().length() > 1_000_000) {
                throw new IllegalArgumentException("knowledge entry");
            }
            String value = entry.getValue();
            value = replace(value, SECRET, "SECRET", workspaceSalt, categories);
            value = replace(value, EMAIL, "EMAIL", workspaceSalt, categories);
            value = replace(value, IPV4, "IP", workspaceSalt, categories);
            value = replace(value, WINDOWS_PATH, "PATH", workspaceSalt, categories);
            value = replace(value, UNIX_PATH, "PATH", workspaceSalt, categories);
            value = replace(value, Pattern.compile(Pattern.quote(projectId), Pattern.CASE_INSENSITIVE),
                    "PROJECT", workspaceSalt, categories);
            anonymized.put(entry.getKey(), value);
            if (entry.getKey().startsWith("common.")) common.put(entry.getKey().substring(7), value);
        }
        String sourceDigest = Hashing.sha256(new TreeMap<>(knowledge).toString().getBytes(StandardCharsets.UTF_8));
        return new Result(
                CONTRACT, token("PROJECT", projectId, workspaceSalt), sourceDigest,
                Map.copyOf(anonymized), Map.copyOf(common), List.copyOf(categories),
                false, true, false);
    }

    private static String replace(String input, Pattern pattern, String category, byte[] salt, Set<String> categories)
            throws GeneralSecurityException {
        Matcher matcher = pattern.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            categories.add(category);
            matcher.appendReplacement(output, Matcher.quoteReplacement(token(category, matcher.group(), salt)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String token(String category, String value, byte[] salt) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        String digest = java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        return category + "_" + digest.substring(0, 16);
    }
}
