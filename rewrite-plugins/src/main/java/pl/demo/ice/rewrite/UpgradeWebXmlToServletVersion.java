package pl.demo.ice.rewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dostosowuje wersję deskryptora Jakarta web.xml do wersji Servletu z POM-a. */
public final class UpgradeWebXmlToServletVersion extends Recipe {
    private static final String JAKARTA_NAMESPACE = "https://jakarta.ee/xml/ns/jakartaee";
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\..*)?$");

    @Option(displayName = "Wersja Jakarta Servlet",
            description = "Docelowa wersja jakarta.servlet-api odczytana z profilu migracyjnego.",
            example = "6.1.0")
    public String servletVersion;

    public UpgradeWebXmlToServletVersion() {
    }

    public UpgradeWebXmlToServletVersion(String servletVersion) {
        this.servletVersion = servletVersion;
    }

    @Override
    public String getDisplayName() {
        return "Upgrade Jakarta web.xml to the configured Servlet version";
    }

    @Override
    public String getDescription() {
        return "Sets the Jakarta web.xml descriptor version and schema from the configured jakarta.servlet-api version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
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
