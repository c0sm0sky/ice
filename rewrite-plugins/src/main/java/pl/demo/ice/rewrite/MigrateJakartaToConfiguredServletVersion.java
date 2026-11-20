package pl.demo.ice.rewrite;

import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.xml.AddTagAttribute;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.ChangeTagName;

import java.util.List;

/** Łączy oficjalną migrację Jakarta z aktualizacją web.xml do wersji Servletu z POM-a. */
public final class MigrateJakartaToConfiguredServletVersion extends Recipe {
    private static final String JAVAX_MIGRATION = "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta";

    @Option(displayName = "Wersja Jakarta Servlet",
            description = "Docelowa wersja jakarta.servlet-api odczytana z profilu migracyjnego.",
            example = "6.1.0")
    public String servletVersion;

    @Override
    public String getDisplayName() {
        return "Migrate Java EE to Jakarta and use the configured Servlet version";
    }

    @Override
    public String getDescription() {
        return "Runs OpenRewrite's official javax-to-Jakarta migration, then aligns web.xml with the Servlet version from the migration POM properties.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        Recipe javaxMigration = Environment.builder()
                .scanRuntimeClasspath("org.openrewrite.java.migrate.jakarta")
                .build()
                .activateRecipes(JAVAX_MIGRATION);
        return List.of(
                javaxMigration,
                new AddTagAttribute("//f:view", "xmlns:h", "jakarta.faces.html"),
                new AddTagAttribute("//ui:composition", "xmlns:h", "jakarta.faces.html"),
                new ChangeTagAttribute("//f:view", "xmlns:ui", "jakarta.faces.facelets",
                        "http://java.sun.com/jsf/facelets", false),
                new ChangeTagAttribute("//ui:composition", "xmlns:ui", "jakarta.faces.facelets",
                        "http://java.sun.com/jsf/facelets", false),
                new ChangeTagAttribute("//f:view", "xmlns:f", "jakarta.faces.core",
                        "http://java.sun.com/jsf/core", false),
                new ChangeTagAttribute("//ui:composition", "xmlns:f", "jakarta.faces.core",
                        "http://java.sun.com/jsf/core", false),
                new ChangeTagName("//head", "h:head"),
                new ChangeTagName("//body", "h:body"),
                new UpgradeWebXmlToServletVersion(servletVersion));
    }
}
