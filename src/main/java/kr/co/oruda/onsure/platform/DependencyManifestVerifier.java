package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Build dependency verification: parses a Maven pom.xml's direct dependencies and fails closed
 * if any dependency is not present, at the expected version, in the checked-in approved
 * dependency manifest (contracts/approved-dependency-manifest.v1.json). Every dependency added to
 * a POM must first be reviewed into that manifest -- an unreviewed or version-drifted dependency
 * is a build-time verification failure, not a silent pass.
 */
public final class DependencyManifestVerifier {

    public record DeclaredDependency(String groupId, String artifactId, String version, String scope) {
        public DeclaredDependency {
            if (groupId == null || groupId.isBlank()) throw new IllegalArgumentException("groupId");
            if (artifactId == null || artifactId.isBlank()) throw new IllegalArgumentException("artifactId");
            if (version == null || version.isBlank()) throw new IllegalArgumentException("version");
            scope = scope == null || scope.isBlank() ? "compile" : scope;
        }

        public String coordinate() { return groupId + ":" + artifactId; }
    }

    public record Violation(String code, String coordinate, String detail) {}

    public record VerificationResult(boolean passed, List<DeclaredDependency> declared, List<Violation> violations) {
        public VerificationResult {
            declared = List.copyOf(declared);
            violations = List.copyOf(violations);
        }
    }

    private DependencyManifestVerifier() {}

    public static List<DeclaredDependency> parsePom(Path pomXmlPath) throws Exception {
        Objects.requireNonNull(pomXmlPath, "pomXmlPath");
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomXmlPath.toFile());
        Map<String, String> properties = readProperties(doc);

        List<DeclaredDependency> result = new ArrayList<>();
        Element dependencies = firstChildElement(doc.getDocumentElement(), "dependencies");
        if (dependencies == null) return result;
        for (Element dependency : childElements(dependencies, "dependency")) {
            String groupId = textOf(dependency, "groupId");
            String artifactId = textOf(dependency, "artifactId");
            String version = resolveProperty(textOf(dependency, "version"), properties);
            String scope = textOf(dependency, "scope");
            if (groupId == null || artifactId == null || version == null) continue;
            result.add(new DeclaredDependency(groupId, artifactId, version, scope));
        }
        return result;
    }

    public static VerificationResult verify(Path pomXmlPath, Path approvedManifestPath) throws Exception {
        List<DeclaredDependency> declared = parsePom(pomXmlPath);
        Map<String, String> approvedVersionByCoordinate = readApprovedManifest(approvedManifestPath);

        List<Violation> violations = new ArrayList<>();
        for (DeclaredDependency dependency : declared) {
            String approvedVersion = approvedVersionByCoordinate.get(dependency.coordinate());
            if (approvedVersion == null) {
                violations.add(new Violation("UNAPPROVED_DEPENDENCY", dependency.coordinate(),
                        "not present in approved dependency manifest"));
            } else if (!approvedVersion.equals(dependency.version())) {
                violations.add(new Violation("DEPENDENCY_VERSION_DRIFT", dependency.coordinate(),
                        "pom declares " + dependency.version() + " but manifest approves " + approvedVersion));
            }
        }
        return new VerificationResult(violations.isEmpty(), declared, violations);
    }

    private static Map<String, String> readApprovedManifest(Path approvedManifestPath) throws IOException {
        JsonNode root = new ObjectMapper().readTree(approvedManifestPath.toFile());
        if (!"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1".equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("APPROVED_DEPENDENCY_MANIFEST_CONTRACT_INVALID");
        }
        Map<String, String> byCoordinate = new LinkedHashMap<>();
        for (JsonNode entry : root.path("approved_dependencies")) {
            String coordinate = entry.path("groupId").asText() + ":" + entry.path("artifactId").asText();
            byCoordinate.put(coordinate, entry.path("version").asText());
        }
        return byCoordinate;
    }

    private static Map<String, String> readProperties(Document doc) {
        Map<String, String> properties = new LinkedHashMap<>();
        Element propertiesElement = firstChildElement(doc.getDocumentElement(), "properties");
        if (propertiesElement == null) return properties;
        NodeList children = propertiesElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element) {
                properties.put(element.getTagName(), element.getTextContent().trim());
            }
        }
        return properties;
    }

    private static String resolveProperty(String rawVersion, Map<String, String> properties) {
        if (rawVersion == null) return null;
        if (rawVersion.startsWith("${") && rawVersion.endsWith("}")) {
            String key = rawVersion.substring(2, rawVersion.length() - 1);
            return properties.get(key);
        }
        return rawVersion;
    }

    private static String textOf(Element parent, String tagName) {
        Element child = firstChildElement(parent, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element firstChildElement(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && element.getTagName().equals(tagName)) return element;
        }
        return null;
    }

    private static List<Element> childElements(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && element.getTagName().equals(tagName)) result.add(element);
        }
        return result;
    }
}
