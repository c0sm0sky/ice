package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.ChangeTagAttributeKey;
import org.openrewrite.xml.ChangeTagName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.xml.Assertions.xml;

class IcefacesTagsToPrimeFacesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new IcefacesTagsToPrimeFaces());
    }

    @Test
    void buildsMappingsFromBothTagLibrariesAndUsesExplicitAliases() {
        assertEquals(79, IcefacesTagsToPrimeFaces.getIcefacesTagNames().size());
        assertEquals(176, IcefacesTagsToPrimeFaces.getPrimefacesTagNames().size());

        List<Recipe> recipeSteps = new IcefacesTagsToPrimeFaces().getRecipeList();
        List<ChangeTagName> mappings = recipeSteps.stream()
                .filter(ChangeTagName.class::isInstance)
                .map(ChangeTagName.class::cast)
                .toList();

        assertEquals(57, mappings.size());
        assertEquals(61, recipeSteps.size());
        assertTrue(recipeSteps.stream().anyMatch(ChangeTagAttributeKey.class::isInstance));
        assertEquals(2, recipeSteps.stream().filter(ChangeTagAttribute.class::isInstance).count());
        ChangeTagAttribute primefacesNamespace = recipeSteps.stream()
                .filter(ChangeTagAttribute.class::isInstance)
                .map(ChangeTagAttribute.class::cast)
                .findFirst().orElseThrow();
        assertEquals(null, primefacesNamespace.getOldValue());
        assertEquals("http://primefaces.org/ui", primefacesNamespace.getNewValue());
        assertMapping(mappings, "//ice:commandLink", "p:commandLink");
        assertMapping(mappings, "//ice:form", "h:form");
        assertMapping(mappings, "//ice:inputSecret", "p:password");
        assertMapping(mappings, "//ice:outputText", "h:outputText");
        assertMapping(mappings, "//ice:outputStyle", "h:outputStylesheet");
        assertMapping(mappings, "//ice:outputMedia", "p:media");
        assertMapping(mappings, "//ice:menuPopup", "p:contextMenu");
        assertMapping(mappings, "//ice:panelPopup", "p:dialog");
        assertMapping(mappings, "//ice:panelTooltip", "p:tooltip");
        assertMapping(mappings, "//ice:panelConfirmation", "p:confirmDialog");
        assertMapping(mappings, "//ice:menuItemSeparator", "p:divider");
        assertTrue(mappings.stream().anyMatch(mapping -> "//ice:tree".equals(mapping.getElementName())));

        var migrated = mappings.stream()
                .map(mapping -> mapping.getElementName().substring("//ice:".length()))
                .collect(java.util.stream.Collectors.toSet());
        var manual = IcefacesTagsToPrimeFaces.getManualMigrations().keySet();
        assertEquals(22, manual.size());
        assertTrue(java.util.Collections.disjoint(migrated, manual));
        var classified = new java.util.HashSet<>(migrated);
        classified.addAll(manual);
        assertEquals(IcefacesTagsToPrimeFaces.getIcefacesTagNames(), classified);
    }

    @Test
    void normalizesPrimefacesNamespaceAfterJakartaMigration() {
        rewriteRun(xml(
                """
                <ui:composition xmlns:p="primefaces" xmlns:ui="jakarta.faces.facelets">
                  <p:commandLink/>
                </ui:composition>
                """,
                """
                <ui:composition xmlns:p="http://primefaces.org/ui" xmlns:ui="jakarta.faces.facelets">
                  <p:commandLink/>
                </ui:composition>
                """
        ));
    }

    private static void assertMapping(List<ChangeTagName> mappings, String oldName, String newName) {
        assertTrue(mappings.stream().anyMatch(mapping -> oldName.equals(mapping.getElementName())
                && newName.equals(mapping.getNewName())), oldName + " -> " + newName);
    }
}
