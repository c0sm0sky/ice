package pl.demo.ice.rewrite;

import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.xml.AddTagAttribute;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.ChangeTagName;

import java.util.ArrayList;
import java.util.List;

/** Łączy oficjalną migrację Jakarta z aktualizacją web.xml do wersji Servletu z POM-a. */
public final class MigrateJakartaToConfiguredServletVersion extends Recipe {
    private static final String JAVAX_MIGRATION = "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta";
    private static final String FACES_41_MIGRATION =
            "org.openrewrite.java.migrate.jakarta.Faces4xMigrationToFaces41x";

    @Option(displayName = "Wersja Jakarta Servlet",
            description = "Docelowa wersja jakarta.servlet-api odczytana z profilu migracyjnego.",
            example = "6.1.0")
    public String servletVersion;

    @Option(displayName = "Migrate Jakarta Faces to 4.1",
            description = "Also runs OpenRewrite's official Faces 4.0-to-4.1 migration chain.",
            example = "true")
    public boolean migrateFaces41;

    @Override
    public String getDisplayName() {
        return "Migrate Java EE to Jakarta and use the configured Servlet version";
    }

    @Override
    public String getDescription() {
        return "Runs OpenRewrite's official javax-to-Jakarta migration and optionally the Faces 4.1 migration, then applies compatibility fixes and aligns web.xml with the Servlet version from the migration POM properties.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("org.openrewrite.java.migrate.jakarta")
                .build();
        List<Recipe> recipes = new ArrayList<>();
        // MyFaces 1.1 imports may have no attributed type, so update the package in the Java LST first.
        recipes.add(new ChangePackage("javax.faces", "jakarta.faces", true));
        recipes.add(environment.activateRecipes(JAVAX_MIGRATION));
        if (migrateFaces41) {
            recipes.add(environment.activateRecipes(FACES_41_MIGRATION));
        }
        recipes.addAll(List.of(
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
                new ChangeTagName("//*[local-name()='head']", "h:head"),
                new ChangeTagName("//*[local-name()='body']", "h:body"),
                new UpgradeWebXmlToServletVersion(servletVersion)));
        return recipes;
    }
}
