package pl.demo.ice.rewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;

/** Przenosi integrację Spring Faces/Web Flow z generatora XSLT do recepty OpenRewrite. */
public final class UpgradeSpringFacesIntegration extends Recipe {
    private static final String WEB_FLOW_NAMESPACE = "http://www.springframework.org/schema/webflow-config";
    private static final String FACES_NAMESPACE = "http://www.springframework.org/schema/faces";
    private static final String BEANS_NAMESPACE = "http://www.springframework.org/schema/beans";

    @Option(displayName = "Id usług Spring Faces", description = "Id beanów flow-builder-services.", example = "flowBuilderServices")
    public String flowBuilderServicesId = "flowBuilderServices";

    @Option(displayName = "Ścieżka bazowa flow", description = "Domyślna ścieżka flow-registry.", example = "/WEB-INF")
    public String flowBasePath = "/WEB-INF";

    @Option(displayName = "Id beana listenera", description = "Id beana FlowFacesContextLifecycleListener.", example = "facesContextListener")
    public String listenerBeanId = "facesContextListener";

    @Option(displayName = "Klasa listenera", description = "Klasa listenera Spring Faces.", example = "org.springframework.faces.webflow.FlowFacesContextLifecycleListener")
    public String listenerClass = "org.springframework.faces.webflow.FlowFacesContextLifecycleListener";

    @Option(displayName = "Nazwa pliku głównego flow", description = "Nazwa pliku, któremu należy przypisać id głównego flow.", example = "main-flow.xml")
    public String mainFlowFileName = "main-flow.xml";

    @Option(displayName = "Id głównego flow", description = "Id przypisywane głównemu flow, gdy go brakuje.", example = "main-flow")
    public String mainFlowId = "main-flow";

    public UpgradeSpringFacesIntegration() {
    }

    @Override
    public String getDisplayName() {
        return "Configure Spring Faces integration for Web Flow";
    }

    @Override
    public String getDescription() {
        return "Updates Spring schema locations and adds missing Spring Faces builder services and lifecycle listeners to a Web Flow application context.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String builderId = valueOr(flowBuilderServicesId, "flowBuilderServices");
        String basePath = valueOr(flowBasePath, "/WEB-INF");
        String beanId = valueOr(listenerBeanId, "facesContextListener");
        String beanClass = valueOr(listenerClass,
                "org.springframework.faces.webflow.FlowFacesContextLifecycleListener");
        String flowFile = valueOr(mainFlowFileName, "main-flow.xml");
        String flowId = valueOr(mainFlowId, "main-flow");

        return new XmlIsoVisitor<>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Document updated = super.visitDocument(document, ctx);
                Xml.Tag root = updated.getRoot();
                if (!"beans".equals(localName(root.getName())) || !BEANS_NAMESPACE.equals(namespace(root, getCursor()))) {
                    return updated;
                }

                Xml.Tag originalRoot = root;
                root = updateSchemaLocation(root);
                root = addNamespace(root, "faces", FACES_NAMESPACE);
                List<Content> content = root.getContent() == null
                        ? new ArrayList<>() : new ArrayList<>(root.getContent());
                boolean addedChild = false;
                if (!hasElement(root, "flow-builder-services", FACES_NAMESPACE)) {
                    insertChild(content, Xml.Tag.build("<faces:flow-builder-services id=\""
                            + escape(builderId) + "\"/>"));
                    addedChild = true;
                }
                if (!hasBean(root, beanId)) {
                    insertChild(content, Xml.Tag.build("<bean id=\"" + escape(beanId)
                            + "\" class=\"" + escape(beanClass) + "\"/>"));
                    addedChild = true;
                }
                for (int i = 0; i < content.size(); i++) {
                    if (!(content.get(i) instanceof Xml.Tag child)
                            || !"flow-registry".equals(localName(child.getName()))
                            || !WEB_FLOW_NAMESPACE.equals(namespaceFromRoot(root, child))) {
                        continue;
                    }
                    Xml.Tag configured = withDefaults(child,
                            new String[][]{{"flow-builder-services", builderId}, {"base-path", basePath}});
                    if (!configured.equals(child)) {
                        content.set(i, configured);
                        addedChild = true;
                    }
                }
                if (!addedChild && root == originalRoot) return updated;
                return updated.withRoot(addedChild ? root.withContent(content) : root);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag updated = super.visitTag(tag, ctx);
                if (!WEB_FLOW_NAMESPACE.equals(namespace(updated, getCursor()))) return updated;

                return switch (localName(updated.getName())) {
                    case "flow-registry" -> withDefaults(updated,
                            new String[][]{{"flow-builder-services", builderId}, {"base-path", basePath}});
                    case "flow-location" -> upgradeFlowLocation(updated, flowFile, flowId);
                    case "flow-executor" -> ensureFacesListener(updated, beanId);
                    case "flow-execution-listeners" -> ensureListener(updated, beanId);
                    default -> updated;
                };
            }

            private Xml.Tag updateSchemaLocation(Xml.Tag root) {
                List<Xml.Attribute> attributes = new ArrayList<>(root.getAttributes().size());
                boolean changed = false;
                for (Xml.Attribute attribute : root.getAttributes()) {
                    if (!"schemaLocation".equals(localName(attribute.getKeyAsString()))) {
                        attributes.add(attribute);
                        continue;
                    }
                    String value = attribute.getValueAsString();
                    if (value == null) {
                        attributes.add(attribute);
                        continue;
                    }
                    String migrated = value
                            .replace("http://www.springframework.org/schema/beans/spring-beans-2.0.xsd",
                                    "https://www.springframework.org/schema/beans/spring-beans.xsd")
                            .replace("http://www.springframework.org/schema/tx/spring-tx-2.0.xsd",
                                    "https://www.springframework.org/schema/tx/spring-tx.xsd")
                            .replace("http://www.springframework.org/schema/webflow-config/spring-webflow-config-1.0.xsd",
                                    "https://www.springframework.org/schema/webflow-config/spring-webflow-config.xsd");
                    if (!migrated.contains(FACES_NAMESPACE)) {
                        migrated += " " + FACES_NAMESPACE + " https://www.springframework.org/schema/faces/spring-faces.xsd";
                    }
                    changed |= !migrated.equals(value);
                    attributes.add(migrated.equals(value) ? attribute
                            : attribute.withValue(attribute.getValue().withValue(migrated)));
                }
                return changed ? root.withAttributes(attributes) : root;
            }

            private Xml.Tag upgradeFlowLocation(Xml.Tag tag, String expectedFile, String expectedId) {
                String path = attribute(tag, "path");
                Xml.Tag updated = tag;
                if (path != null && path.startsWith("/WEB-INF/")) {
                    updated = setAttribute(updated, "path", path.substring("/WEB-INF".length()));
                }
                if (attribute(updated, "id") == null && path != null && path.contains(expectedFile)) {
                    updated = setAttribute(updated, "id", expectedId);
                }
                return updated;
            }

            private Xml.Tag ensureFacesListener(Xml.Tag tag, String ref) {
                Xml.Tag listeners = tag.getChildren().stream()
                        .filter(child -> "flow-execution-listeners".equals(localName(child.getName())))
                        .findFirst().orElse(null);
                if (listeners == null) {
                    Xml.Tag newListeners = Xml.Tag.build("<flow-execution-listeners><listener ref=\""
                            + escape(ref) + "\"/></flow-execution-listeners>");
                    newListeners = renamePrefix(newListeners, tag.getName());
                    return appendChild(tag, newListeners);
                }
                Xml.Tag updatedListeners = ensureListener(listeners, ref);
                if (updatedListeners == listeners || updatedListeners.equals(listeners)) return tag;
                return replaceChild(tag, listeners, updatedListeners);
            }

            private Xml.Tag ensureListener(Xml.Tag tag, String ref) {
                boolean present = tag.getChildren().stream()
                        .filter(child -> "listener".equals(localName(child.getName())))
                        .anyMatch(child -> ref.equals(attribute(child, "ref")));
                if (present) return tag;
                Xml.Tag listener = Xml.Tag.build("<listener ref=\"" + escape(ref) + "\"/>");
                listener = renamePrefix(listener, tag.getName());
                return appendChild(tag, listener);
            }
        };
    }

    private static Xml.Tag withDefaults(Xml.Tag tag, String[][] defaults) {
        Xml.Tag updated = tag;
        for (String[] pair : defaults) {
            if (attribute(updated, pair[0]) == null) updated = setAttribute(updated, pair[0], pair[1]);
        }
        return updated;
    }

    private static Xml.Tag setAttribute(Xml.Tag tag, String name, String value) {
        List<Xml.Attribute> attributes = new ArrayList<>(tag.getAttributes());
        for (int i = 0; i < attributes.size(); i++) {
            if (name.equals(attributes.get(i).getKeyAsString())) {
                attributes.set(i, attributes.get(i).withValue(attributes.get(i).getValue().withValue(value)));
                return tag.withAttributes(attributes);
            }
        }
        Xml.Tag holder = Xml.Tag.build("<holder " + name + "=\"" + escape(value) + "\"/>");
        attributes.add(holder.getAttributes().get(0));
        return tag.withAttributes(attributes);
    }

    private static Xml.Tag addNamespace(Xml.Tag tag, String prefix, String uri) {
        if (tag.getAttributes().stream().anyMatch(attribute -> ("xmlns:" + prefix).equals(attribute.getKeyAsString()))) {
            return tag;
        }
        List<Xml.Attribute> attributes = new ArrayList<>(tag.getAttributes());
        Xml.Tag holder = Xml.Tag.build("<holder xmlns:" + prefix + "=\"" + escape(uri) + "\"/>");
        Xml.Attribute added = holder.getAttributes().get(0);
        int schemaLocation = -1;
        for (int i = 0; i < attributes.size(); i++) {
            if ("schemaLocation".equals(localName(attributes.get(i).getKeyAsString()))) {
                schemaLocation = i;
                break;
            }
        }
        if (schemaLocation >= 0) {
            added = added.withPrefix(attributes.get(schemaLocation).getPrefix());
            attributes.add(schemaLocation, added);
        } else {
            attributes.add(added);
        }
        return tag.withAttributes(attributes);
    }

    private static void insertChild(List<Content> content, Xml.Tag child) {
        child = child.withPrefix("\n  ");
        int insertionPoint = content.size();
        if (insertionPoint > 0 && content.get(insertionPoint - 1) instanceof Xml.CharData data
                && data.getText().isBlank()) {
            insertionPoint--;
        }
        content.add(insertionPoint, child);
    }

    private static Xml.Tag appendChild(Xml.Tag parent, Xml.Tag child) {
        List<Content> content = parent.getContent() == null
                ? new ArrayList<>() : new ArrayList<>(parent.getContent());
        if (content.isEmpty()) {
            String indent = parent.getPrefix().isBlank() ? "\n  " : parent.getPrefix();
            Xml.Tag.Closing closing = parent.getClosing();
            if (closing == null) {
                closing = Xml.Tag.build("<holder></holder>").getClosing().withName(parent.getName());
            }
            closing = closing.withPrefix(indent);
            content.add(child.withPrefix(indent + "  "));
            return parent.withContent(content).withClosing(closing);
        }
        String indent = parent.getChildren().isEmpty() ? "\n    " : parent.getChildren().getLast().getPrefix();
        int insertionPoint = content.size();
        if (content.get(insertionPoint - 1) instanceof Xml.CharData data && data.getText().isBlank()) {
            insertionPoint--;
        }
        content.add(insertionPoint, child.withPrefix(indent));
        return parent.withContent(content);
    }

    private static Xml.Tag replaceChild(Xml.Tag parent, Xml.Tag oldChild, Xml.Tag newChild) {
        List<Content> content = new ArrayList<>(parent.getContent());
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i) == oldChild || content.get(i).equals(oldChild)) {
                content.set(i, newChild.withPrefix(oldChild.getPrefix()));
                break;
            }
        }
        return parent.withContent(content);
    }

    private static Xml.Tag renamePrefix(Xml.Tag tag, String exampleName) {
        int separator = exampleName.indexOf(':');
        if (separator < 0) return tag;
        String prefix = exampleName.substring(0, separator + 1);
        Xml.Tag renamed = tag.withName(prefix + localName(tag.getName()));
        if (renamed.getClosing() != null) renamed = renamed.withClosing(renamed.getClosing().withName(prefix + localName(tag.getName())));
        for (Xml.Tag child : renamed.getChildren()) {
            renamed = replaceChild(renamed, child, renamePrefix(child, exampleName));
        }
        return renamed;
    }

    private static boolean hasElement(Xml.Tag root, String expectedLocalName, String expectedNamespace) {
        return root.getChildren().stream().anyMatch(child -> expectedLocalName.equals(localName(child.getName()))
                && expectedNamespace.equals(namespaceFromRoot(root, child)));
    }

    private static String namespaceFromRoot(Xml.Tag root, Xml.Tag child) {
        String prefix = prefix(child.getName());
        String key = prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
        return child.getAttributes().stream().filter(attribute -> key.equals(attribute.getKeyAsString()))
                .map(Xml.Attribute::getValueAsString).findFirst()
                .orElseGet(() -> root.getAttributes().stream().filter(attribute -> key.equals(attribute.getKeyAsString()))
                        .map(Xml.Attribute::getValueAsString).findFirst().orElse(null));
    }

    private static boolean hasBean(Xml.Tag root, String id) {
        return root.getChildren().stream().anyMatch(child -> "bean".equals(localName(child.getName()))
                && id.equals(attribute(child, "id")));
    }

    private static String namespace(Xml.Tag tag, org.openrewrite.Cursor cursor) {
        String prefix = prefix(tag.getName());
        String namespaceAttribute = prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
        for (Xml.Tag current = tag; current != null; ) {
            String value = current.getAttributes().stream()
                    .filter(attribute -> namespaceAttribute.equals(attribute.getKeyAsString()))
                    .map(Xml.Attribute::getValueAsString).findFirst().orElse(null);
            if (value != null) return value;
            if (cursor != null && cursor.getValue() == current) {
                cursor = cursor.getParentTreeCursor();
                current = cursor != null && !cursor.isRoot() && cursor.getValue() instanceof Xml.Tag parent
                        ? parent : null;
            } else {
                current = null;
            }
        }
        return null;
    }

    private static String attribute(Xml.Tag tag, String name) {
        return tag.getAttributes().stream().filter(attribute -> name.equals(attribute.getKeyAsString()))
                .map(Xml.Attribute::getValueAsString).findFirst().orElse(null);
    }

    private static String prefix(String name) {
        int separator = name.indexOf(':');
        return separator < 0 ? "" : name.substring(0, separator);
    }

    private static String localName(String name) {
        int separator = name.indexOf(':');
        return separator < 0 ? name : name.substring(separator + 1);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
