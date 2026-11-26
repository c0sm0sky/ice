package pl.demo.ice.rewrite;

import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;

import java.util.List;

/** Runs the official Java 25 migration on the staged project. */
public final class MigrateJava25 extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate the staged project to Java 25";
    }

    @Override
    public String getDescription() {
        return "Runs the official Java 25 migration on the project copy used for PrimeFaces migration.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(Environment.builder()
                .scanRuntimeClasspath("org.openrewrite.java.migrate")
                .build()
                .activateRecipes("org.openrewrite.java.migrate.UpgradeToJava25"));
    }
}
