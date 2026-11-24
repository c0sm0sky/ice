package pl.demo.ice.rewrite;

import org.openrewrite.Recipe;
import org.openrewrite.xml.ChangeTagAttribute;

import java.util.List;

public final class MigrateWebFlowExceptionPackage extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate the Web Flow action exception package";
    }

    @Override
    public String getDescription() {
        return "Updates ActionExecutionException references to the package used by current Spring Web Flow.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(new ChangeTagAttribute(
                "//*[local-name()='transition']",
                "on-exception",
                "org.springframework.webflow.execution.ActionExecutionException",
                "org.springframework.webflow.engine.ActionExecutionException",
                false));
    }
}
