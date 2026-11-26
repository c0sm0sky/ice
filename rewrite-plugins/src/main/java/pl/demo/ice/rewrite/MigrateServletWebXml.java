package pl.demo.ice.rewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dostosowuje wersję deskryptora Jakarta web.xml do wersji Servletu z POM-a. */
public final class MigrateServletWebXml extends Recipe {
    private static final String UPLOAD_CLASS = "com.icesoft.faces.component.inputfile.FileUploadServlet";
    @Option(displayName = "Migration phase", description = "Use version to align the descriptor or upload to remove the ICEfaces upload servlet.", example = "version", required = false)
    public String phase = "version";
    private static final String JAKARTA_NAMESPACE = "https://jakarta.ee/xml/ns/jakartaee";
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\..*)?$");

    @Option(displayName = "Wersja Jakarta Servlet",
            description = "Docelowa wersja jakarta.servlet-api odczytana z profilu migracyjnego.",
            example = "6.1.0", required = false)
    public String servletVersion;

    public MigrateServletWebXml() {
    }

    public MigrateServletWebXml(String servletVersion) {
        this.servletVersion = servletVersion;
    }

    @Override
    public String getDisplayName() {
        return "Migrate the Servlet web.xml descriptor";
    }

    @Override
    public String getDescription() {
        return "Aligns the Jakarta descriptor version or removes the ICEfaces upload servlet and its mappings.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        if ("upload".equals(phase)) return removeUploadServlet();
        if (!"version".equals(phase)) throw new IllegalArgumentException("Unknown servlet migration phase: " + phase);
        Matcher matcher = VERSION.matcher(servletVersion == null ? "" : servletVersion);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Nieprawidłowa wersja jakarta.servlet-api: " + servletVersion);
        }
        String descriptorVersion = matcher.group(1) + "." + matcher.group(2);
        String schemaVersion = matcher.group(1) + "_" + matcher.group(2);
        return new XmlIsoVisitor<>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag updated = super.visitTag(tag, ctx);
                if (!"web-app".equals(localName(updated.getName())) || !isJakartaWebApp(updated)) {
                    return updated;
                }

                List<Xml.Attribute> attributes = new ArrayList<>(updated.getAttributes().size());
                boolean changed = false;
                for (Xml.Attribute attribute : updated.getAttributes()) {
                    String key = localName(attribute.getKeyAsString());
                    String value = attribute.getValueAsString();
                    if ("version".equals(key) && !descriptorVersion.equals(value)) {
                        changed = true;
                        attributes.add(attribute.withValue(attribute.getValue().withValue(descriptorVersion)));
                    } else if ("schemaLocation".equals(key) && value != null) {
                        String schema = value.replaceAll("web-app_\\d+_\\d+\\.xsd",
                                "web-app_" + schemaVersion + ".xsd");
                        changed |= !schema.equals(value);
                        attributes.add(changed && !schema.equals(value) ?
                                attribute.withValue(attribute.getValue().withValue(schema)) : attribute);
                    } else {
                        attributes.add(attribute);
                    }
                }
                return changed ? updated.withAttributes(attributes) : updated;
            }
        };
    }

    private TreeVisitor<?, ExecutionContext> removeUploadServlet() {
        return Preconditions.check(new FindSourceFiles("**/web.xml"), new XmlIsoVisitor<ExecutionContext>() {
            @Override public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                if (!"web-app".equals(localName(tag.getName()))) return super.visitTag(tag, ctx);
                Set<String> names = tag.getChildren().stream()
                        .filter(child -> "servlet".equals(localName(child.getName())))
                        .filter(child -> UPLOAD_CLASS.equals(childValue(child, "servlet-class")))
                        .map(child -> childValue(child, "servlet-name"))
                        .filter(name -> name != null && !name.isBlank()).collect(Collectors.toSet());
                if (names.isEmpty()) return super.visitTag(tag, ctx);
                return tag.withContent(tag.getContent().stream().filter(content -> {
                    if (!(content instanceof Xml.Tag child)) return true;
                    String kind = localName(child.getName());
                    if ("servlet".equals(kind)) return !UPLOAD_CLASS.equals(childValue(child, "servlet-class"));
                    return !"servlet-mapping".equals(kind) || !names.contains(childValue(child, "servlet-name"));
                }).collect(Collectors.toList()));
            }
        });
    }

    private static String childValue(Xml.Tag tag, String name) {
        return tag.getChildren().stream().filter(child -> name.equals(localName(child.getName())))
                .map(child -> child.getValue().orElse("").trim()).findFirst().orElse(null);
    }

    private static boolean isJakartaWebApp(Xml.Tag tag) {
        String prefix = prefix(tag.getName());
        String namespaceAttribute = prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
        return tag.getAttributes().stream()
                .filter(attribute -> namespaceAttribute.equals(attribute.getKeyAsString()))
                .anyMatch(attribute -> JAKARTA_NAMESPACE.equals(attribute.getValueAsString()));
    }

    private static String localName(String name) {
        int separator = name.indexOf(':');
        return separator < 0 ? name : name.substring(separator + 1);
    }

    private static String prefix(String name) {
        int separator = name.indexOf(':');
        return separator < 0 ? "" : name.substring(0, separator);
    }
}
