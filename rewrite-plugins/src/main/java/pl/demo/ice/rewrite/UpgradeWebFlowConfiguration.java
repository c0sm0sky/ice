package pl.demo.ice.rewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Cursor;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Mapuje ogólne elementy konfiguracji Spring Web Flow 1.x na Web Flow 4.x. */
public final class UpgradeWebFlowConfiguration extends Recipe {
    private static final String WEB_FLOW_NAMESPACE = "http://www.springframework.org/schema/webflow-config";
    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";
    private static final String SOURCE_SCHEMA = "/META-INF/webflow-xsd/source/org/springframework/webflow/config/spring-webflow-config-1.0.xsd";
    private static final String TARGET_SCHEMA = "/META-INF/webflow-xsd/target/org/springframework/webflow/config/spring-webflow-config.xsd";
    private static final Map<String, Set<String>> ELEMENT_MAPPINGS = Map.ofEntries(
            Map.entry("registry", Set.of("flow-registry")),
            Map.entry("location", Set.of("flow-location", "flow-location-pattern")),
            Map.entry("executor", Set.of("flow-executor")),
            Map.entry("repository", Set.of("flow-execution-repository")),
            Map.entry("execution-attributes", Set.of("flow-execution-attributes")),
            Map.entry("execution-listeners", Set.of("flow-execution-listeners")),
            Map.entry("alwaysRedirectOnPause", Set.of("always-redirect-on-pause")),
            Map.entry("listener", Set.of("listener")),
            Map.entry("attribute", Set.of("attribute")));
    private static final Map<String, Set<String>> ATTRIBUTE_MAPPINGS = Map.ofEntries(
            Map.entry("conversation-manager-ref", Set.of("conversation-manager")),
            Map.entry("criteria", Set.of("criteria")),
            Map.entry("max-continuations", Set.of("max-execution-snapshots")),
            Map.entry("max-conversations", Set.of("max-executions")),
            Map.entry("name", Set.of("name")),
            Map.entry("path", Set.of("path", "value")),
            Map.entry("ref", Set.of("ref")),
            Map.entry("registry-ref", Set.of("flow-registry")),
            Map.entry("repository-type", Set.of("max-execution-snapshots")),
            Map.entry("type", Set.of("type")),
            Map.entry("value", Set.of("value")));
    private static final Set<String> SUPPORTED_REPOSITORY_TYPES = Set.of("simple", "continuation");

    @Override
    public String getDisplayName() {
        return "Upgrade Spring Web Flow XML configuration";
    }

    @Override
    public String getDescription() {
        return "Maps the generic Spring Web Flow 1.x application-context elements and attributes to Web Flow 4.x.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        SchemaCatalog schemas = readAndValidateSchemas();
        return new XmlIsoVisitor<>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag updated = super.visitTag(tag, ctx);
                if (!isWebFlowTag(tag, getCursor())) return updated;
                String name = localName(tag);
                if (!ELEMENT_MAPPINGS.containsKey(name)) {
                    if (schemas.targetElements().contains(name)) return updated;
                    if (!schemas.sourceElements().contains(name)) {
                        throw new IllegalStateException("Element " + name + " nie występuje w źródłowym XSD Web Flow.");
                    }
                    throw new IllegalStateException("Element " + name + " występuje w źródłowym XSD, ale nie ma mapowania do Web Flow 4.");
                }
                return switch (name) {
                    case "registry" -> upgradeRegistry(updated);
                    case "location" -> upgradeLocation(updated);
                    case "executor" -> upgradeExecutor(updated);
                    case "repository" -> upgradeRepository(updated);
                    case "execution-attributes" -> renameWithoutAttributes(updated, "flow-execution-attributes");
                    case "execution-listeners" -> renameWithoutAttributes(updated, "flow-execution-listeners");
                    case "alwaysRedirectOnPause" -> keepAttributes(rename(updated, "always-redirect-on-pause"), "value");
                    default -> updated;
                };
            }
        };
    }

    private static SchemaCatalog readAndValidateSchemas() {
        Document source = loadSchema(SOURCE_SCHEMA);
        Document target = loadSchema(TARGET_SCHEMA);
        String sourceNamespace = source.getDocumentElement().getAttribute("targetNamespace");
        String targetNamespace = target.getDocumentElement().getAttribute("targetNamespace");
        if (!WEB_FLOW_NAMESPACE.equals(sourceNamespace) || !WEB_FLOW_NAMESPACE.equals(targetNamespace)) {
            throw new IllegalStateException("XSD Web Flow źródłowy i docelowy muszą używać przestrzeni "
                    + WEB_FLOW_NAMESPACE + "; otrzymano " + sourceNamespace + " i " + targetNamespace);
        }

        Set<String> sourceElements = schemaNames(source, "element");
        Set<String> targetElements = schemaNames(target, "element");
        Set<String> sourceAttributes = schemaNames(source, "attribute");
        Set<String> targetAttributes = schemaNames(target, "attribute");
        requireEqual("elementy źródłowego XSD i zestaw jawnych mapowań",
                ELEMENT_MAPPINGS.keySet(), sourceElements);
        requireContains("docelowy XSD nie deklaruje elementów wymaganych przez mapowania",
                flatten(ELEMENT_MAPPINGS), targetElements);
        requireEqual("atrybuty źródłowego XSD i zestaw jawnych mapowań",
                ATTRIBUTE_MAPPINGS.keySet(), sourceAttributes);
        requireContains("docelowy XSD nie deklaruje atrybutów wymaganych przez mapowania",
                flatten(ATTRIBUTE_MAPPINGS), targetAttributes);
        requireContains("źródłowy XSD nie deklaruje obsługiwanych typów repozytorium",
                SUPPORTED_REPOSITORY_TYPES, repositoryTypes(source, "repositoryTypeAttribute"));
        return new SchemaCatalog(sourceElements, targetElements, sourceAttributes, targetAttributes);
    }

    private static Set<String> flatten(Map<String, Set<String>> mappings) {
        Set<String> values = new HashSet<>();
        mappings.values().forEach(values::addAll);
        return values;
    }

    private static Document loadSchema(String resource) {
        try (InputStream input = UpgradeWebFlowConfiguration.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Nie znaleziono schematu Web Flow " + resource
                        + "; sprawdź wersje zależności źródłowej i docelowej w profilu migracji.");
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(input);
        } catch (Exception e) {
            throw new IllegalStateException("Nie udało się wczytać schematu Web Flow " + resource, e);
        }
    }

    private static Set<String> schemaNames(Document schema, String elementName) {
        Set<String> names = new HashSet<>();
        NodeList nodes = schema.getElementsByTagNameNS(XSD_NAMESPACE, elementName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (element.hasAttribute("name")) names.add(element.getAttribute("name"));
        }
        return names;
    }

    private static Set<String> repositoryTypes(Document schema, String typeName) {
        NodeList types = schema.getElementsByTagNameNS(XSD_NAMESPACE, "simpleType");
        for (int i = 0; i < types.getLength(); i++) {
            Element type = (Element) types.item(i);
            if (!typeName.equals(type.getAttribute("name"))) continue;
            Set<String> values = new HashSet<>();
            NodeList enumerations = type.getElementsByTagNameNS(XSD_NAMESPACE, "enumeration");
            for (int j = 0; j < enumerations.getLength(); j++) {
                values.add(((Element) enumerations.item(j)).getAttribute("value"));
            }
            return values;
        }
        throw new IllegalStateException("W źródłowym XSD Web Flow brakuje typu " + typeName);
    }

    private static void requireEqual(String description, Set<String> expected, Set<String> actual) {
        if (!expected.equals(actual)) {
            Set<String> differences = new HashSet<>(expected);
            differences.addAll(actual);
            Set<String> common = new HashSet<>(expected);
            common.retainAll(actual);
            differences.removeAll(common);
            throw new IllegalStateException("Zmieniły się " + description + " Web Flow; sprawdź mapowania: " + differences);
        }
    }

    private static void requireContains(String description, Set<String> expected, Set<String> actual) {
        if (!actual.containsAll(expected)) {
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(actual);
            throw new IllegalStateException("Docelowy XSD Web Flow nie zawiera wymaganych " + description + ": " + missing);
        }
    }

    private record SchemaCatalog(Set<String> sourceElements, Set<String> targetElements,
                                 Set<String> sourceAttributes, Set<String> targetAttributes) {
    }

    private static Xml.Tag upgradeLocation(Xml.Tag tag) {
        String path = attribute(tag, "path");
        boolean pattern = path != null && (path.contains("*") || path.contains("?"));
        Xml.Tag renamed = rename(tag, pattern ? "flow-location-pattern" : "flow-location");
        return setOnlyAttributes(renamed, pattern ? new String[][]{{"value", path}} : new String[][]{{"path", path}});
    }

    private static Xml.Tag upgradeRegistry(Xml.Tag tag) {
        return setOnlyAttributes(rename(tag, "flow-registry"), new String[][]{
                {"id", attribute(tag, "id")},
                {"flow-builder-services", valueOr(attribute(tag, "flow-builder-services"), "flowBuilderServices")},
                {"base-path", valueOr(attribute(tag, "base-path"), "/WEB-INF")}
        });
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Xml.Tag upgradeExecutor(Xml.Tag tag) {
        String repositoryType = attribute(tag, "repository-type");
        Xml.Tag repository = firstChild(tag, "flow-execution-repository");
        String repositoryTypeFromChild = repository == null ? null : attribute(repository, "type");
        if (repositoryType != null && repository != null) {
            throw new IllegalStateException("Nie można łączyć repository-type z elementem repository.");
        }
        String type = repositoryType != null ? repositoryType : repositoryTypeFromChild;
        if (type != null && !type.equals("simple") && !type.equals("continuation")) {
            throw new IllegalStateException("Typy client i singlekey nie mają bezpośredniego odpowiednika w Web Flow 4: " + type);
        }
        if (repository != null && attribute(repository, "max-continuations") != null && !"continuation".equals(repositoryTypeFromChild)) {
            throw new IllegalStateException("max-continuations można ustawić tylko dla repozytorium continuation.");
        }
        if (repository != null && attribute(repository, "max-conversations") != null
                && attribute(repository, "conversation-manager-ref") != null) {
            throw new IllegalStateException("Nie można łączyć max-conversations z conversation-manager-ref.");
        }

        String registryRef = attribute(tag, "registry-ref");
        String snapshots = "simple".equals(type) ? "0" : "30";
        Xml.Tag upgraded = setOnlyAttributes(rename(tag, "flow-executor"),
                new String[][]{{"id", attribute(tag, "id")}, {"flow-registry", registryRef}});

        if (repository == null) {
            Xml.Tag synthesized = Xml.Tag.build("<flow-execution-repository max-execution-snapshots=\""
                    + snapshots + "\"/>");
            synthesized = rename(synthesized, qualifiedName(tag, "flow-execution-repository"));
            List<Content> content = upgraded.getContent() == null
                    ? new ArrayList<>() : new ArrayList<>(upgraded.getContent());
            int insertionPoint = 0;
            while (insertionPoint < content.size() && !(content.get(insertionPoint) instanceof Xml.Tag)) {
                insertionPoint++;
            }
            if (content.isEmpty()) {
                content.add(charData("\n    "));
                content.add(synthesized);
                content.add(charData("\n  "));
                Xml.Tag.Closing closing = upgraded.getClosing();
                if (closing == null) {
                    closing = Xml.Tag.build("<" + upgraded.getName() + "></" + upgraded.getName() + ">").getClosing();
                }
                upgraded = upgraded.withClosing(closing);
            } else {
                String indent = insertionPoint < content.size()
                        ? ((Xml.Tag) content.get(insertionPoint)).getPrefix() : "\n    ";
                synthesized = synthesized.withPrefix(indent);
                content.add(insertionPoint, synthesized);
            }
            upgraded = upgraded.withContent(content);
        }
        return upgraded;
    }

    private static Xml.Tag upgradeRepository(Xml.Tag tag) {
        String type = attribute(tag, "type");
        if (type != null && !type.equals("simple") && !type.equals("continuation")) {
            throw new IllegalStateException("Typy client i singlekey nie mają bezpośredniego odpowiednika w Web Flow 4: " + type);
        }
        if (attribute(tag, "max-continuations") != null && !"continuation".equals(type)) {
            throw new IllegalStateException("max-continuations można ustawić tylko dla repozytorium continuation.");
        }
        String snapshots = attribute(tag, "max-continuations");
        if (snapshots == null) snapshots = "simple".equals(type) ? "0" : "30";
        String maxExecutions = attribute(tag, "max-conversations");
        String manager = attribute(tag, "conversation-manager-ref");
        if (maxExecutions != null && manager != null) {
            throw new IllegalStateException("Nie można łączyć max-conversations z conversation-manager-ref.");
        }
        return setOnlyAttributes(rename(tag, "flow-execution-repository"),
                new String[][]{{"max-execution-snapshots", snapshots},
                        {"max-executions", maxExecutions}, {"conversation-manager", manager}});
    }

    private static Xml.Tag firstChild(Xml.Tag tag, String name) {
        return tag.getChildren().stream().filter(child -> name.equals(localName(child))).findFirst().orElse(null);
    }

    private static String localName(Xml.Tag tag) {
        String name = tag.getName();
        int separator = name.indexOf(':');
        return separator < 0 ? name : name.substring(separator + 1);
    }

    private static boolean isWebFlowTag(Xml.Tag tag, Cursor cursor) {
        String name = tag.getName();
        int separator = name.indexOf(':');
        String namespaceAttribute = separator < 0 ? "xmlns" : "xmlns:" + name.substring(0, separator);
        while (cursor != null && !cursor.isRoot()) {
            if (cursor.getValue() instanceof Xml.Tag ancestor) {
                String namespace = ancestor.getAttributes().stream()
                        .filter(attribute -> namespaceAttribute.equals(attribute.getKeyAsString()))
                        .map(Xml.Attribute::getValueAsString).findFirst().orElse(null);
                if (namespace != null) return WEB_FLOW_NAMESPACE.equals(namespace);
            }
            cursor = cursor.getParentTreeCursor();
        }
        return false;
    }

    private static String qualifiedName(Xml.Tag tag, String localName) {
        String name = tag.getName();
        int separator = name.indexOf(':');
        return separator < 0 ? localName : name.substring(0, separator + 1) + localName;
    }

    private static String attribute(Xml.Tag tag, String name) {
        return tag.getAttributes().stream().filter(attribute -> name.equals(attribute.getKeyAsString()))
                .map(Xml.Attribute::getValueAsString).findFirst().orElse(null);
    }

    private static Xml.Tag renameWithoutAttributes(Xml.Tag tag, String name) {
        return setOnlyAttributes(rename(tag, name), new String[0][]);
    }

    private static Xml.Tag keepAttributes(Xml.Tag tag, String... names) {
        List<Xml.Attribute> attributes = tag.getAttributes().stream()
                .filter(attribute -> List.of(names).contains(attribute.getKeyAsString())).toList();
        return tag.withAttributes(attributes);
    }

    private static Xml.Tag setOnlyAttributes(Xml.Tag tag, String[][] attributes) {
        List<Xml.Attribute> result = new ArrayList<>();
        for (String[] pair : attributes) {
            if (pair[1] != null) result.add(attributeNode(pair[0], pair[1]));
        }
        return tag.withAttributes(result);
    }

    private static Xml.Attribute attributeNode(String name, String value) {
        Xml.Tag holder = Xml.Tag.build("<holder " + name + "=\"" + escape(value) + "\"/>");
        return holder.getAttributes().get(0);
    }

    private static Xml.CharData charData(String text) {
        return new Xml.CharData(UUID.randomUUID(), "", Markers.EMPTY, false, text, "");
    }

    private static Xml.Tag rename(Xml.Tag tag, String name) {
        String qualifiedName = name.contains(":") ? name : qualifiedName(tag, name);
        Xml.Tag renamed = tag.withName(qualifiedName);
        if (renamed.getClosing() != null) renamed = renamed.withClosing(renamed.getClosing().withName(qualifiedName));
        return renamed;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
