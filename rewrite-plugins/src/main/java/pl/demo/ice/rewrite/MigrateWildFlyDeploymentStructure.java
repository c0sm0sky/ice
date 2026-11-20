package pl.demo.ice.rewrite;

import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Usuwa konfigurowalne, nieaktualne wykluczenia modułów i podsystemów z deskryptora WildFly. */
public final class MigrateWildFlyDeploymentStructure extends Recipe {
    @Option(displayName = "Wzorzec ścieżki deskryptora",
            description = "Wzorzec plików jboss-deployment-structure.xml objętych migracją.",
            example = "**/jboss-deployment-structure.xml")
    public String filePattern = "**/jboss-deployment-structure.xml";

    @Option(displayName = "Wykluczenia modułów do usunięcia",
            description = "Lista nazw modułów rozdzielona przecinkami; inne wpisy exclusions pozostają bez zmian.",
            example = "javax.faces.api,javax.persistence.api")
    public String modulesToUnexclude = "javax.faces.api,javax.persistence.api,org.jboss.as.jsf,com.sun.jsf-impl";

    @Option(displayName = "Wykluczenia podsystemów do usunięcia",
            description = "Lista nazw podsystemów rozdzielona przecinkami; pozostałe podsystemy pozostają bez zmian.",
            example = "jsf")
    public String subsystemsToUnexclude = "jsf";

    public MigrateWildFlyDeploymentStructure() {
    }

    @Override
    public String getDisplayName() {
        return "Migrate WildFly deployment module exclusions";
    }

    @Override
    public String getDescription() {
        return "Removes configured legacy module and subsystem exclusions from deployment and sub-deployment descriptors while preserving unrelated exclusions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        Set<String> modules = names(modulesToUnexclude);
        Set<String> subsystems = names(subsystemsToUnexclude);
        return Preconditions.check(new FindSourceFiles(filePattern), new XmlIsoVisitor<>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag updated = super.visitTag(tag, ctx);
                String name = localName(updated.getName());
                if ("module".equals(name) && hasAncestor("exclusions")
                        && isWithinDeployment() && modules.contains(attribute(updated, "name"))) {
                    return null;
                }
                if ("subsystem".equals(name) && hasAncestor("exclude-subsystems")
                        && isWithinDeployment() && subsystems.contains(attribute(updated, "name"))) {
                    return null;
                }
                if ("exclusions".equals(name) && updated.getChildren().stream()
                        .noneMatch(child -> "module".equals(localName(child.getName())))) {
                    return null;
                }
                if ("exclude-subsystems".equals(name) && updated.getChildren().stream()
                        .noneMatch(child -> "subsystem".equals(localName(child.getName())))) {
                    return null;
                }
                return updated;
            }

            private boolean hasAncestor(String name) {
                var cursor = getCursor().getParentTreeCursor();
                while (cursor != null && !cursor.isRoot()) {
                    if (cursor.getValue() instanceof Xml.Tag parent && name.equals(localName(parent.getName()))) {
                        return true;
                    }
                    cursor = cursor.getParentTreeCursor();
                }
                return false;
            }

            private boolean isWithinDeployment() {
                var cursor = getCursor().getParentTreeCursor();
                while (cursor != null && !cursor.isRoot()) {
                    if (cursor.getValue() instanceof Xml.Tag parent) {
                        String name = localName(parent.getName());
                        if ("deployment".equals(name) || "sub-deployment".equals(name)) return true;
                    }
                    cursor = cursor.getParentTreeCursor();
                }
                return false;
            }
        });
    }

    private static Set<String> names(String values) {
        if (values == null || values.isBlank()) return Set.of();
        return Arrays.stream(values.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String localName(String name) {
        int separator = name.indexOf(':');
        return separator < 0 ? name : name.substring(separator + 1);
    }

    private static String attribute(Xml.Tag tag, String name) {
        return tag.getAttributes().stream().filter(attribute -> name.equals(attribute.getKeyAsString()))
                .map(Xml.Attribute::getValueAsString).findFirst().orElse(null);
    }
}
