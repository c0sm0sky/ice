package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.ChangeTagAttributeKey;
import org.openrewrite.xml.ChangeTagName;

import java.util.List;
import java.util.Set;

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

        assertEquals(58, mappings.size());
        assertEquals(69, recipeSteps.size());
        assertTrue(recipeSteps.stream().anyMatch(ChangeTagAttributeKey.class::isInstance));
        assertEquals(2, recipeSteps.stream().filter(ChangeTagAttribute.class::isInstance).count());
        ChangeTagAttribute primefacesNamespace = recipeSteps.stream()
                .filter(ChangeTagAttribute.class::isInstance)
                .map(ChangeTagAttribute.class::cast)
                .findFirst().orElseThrow();
        assertEquals(null, primefacesNamespace.getOldValue());
        assertEquals("http://primefaces.org/ui", primefacesNamespace.getNewValue());
        assertMapping(mappings, "//ice:commandLink", "p:commandLink");
        assertMapping(mappings, "//ice:commandSortHeader", "h:panelGroup");
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
        assertEquals(21, manual.size());
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
                <ui:composition xmlns:p="http://primefaces.org/ui" xmlns:ui="jakarta.faces.facelets" xmlns:h="jakarta.faces.html">
                  <p:commandLink ajax="true"/>
                </ui:composition>
                """
        ));
    }

    @Test
    void migratesIceTagsAcrossFilesWhoseNamespaceWasAlreadyChangedByJakarta() {
        rewriteRun(xml(
                """
                <ui:composition xmlns:ice="primefaces" xmlns:ui="jakarta.faces.facelets">
                  <ice:outputText value="Raport"/>
                  <ice:dataTable value="#{users}" var="user">
                    <ice:column><ice:outputText value="#{user.login}"/></ice:column>
                  </ice:dataTable>
                  <ice:commandLink action="finish" value="Wróć"/>
                </ui:composition>
                """,
                """
                <ui:composition xmlns:p="http://primefaces.org/ui" xmlns:ui="jakarta.faces.facelets" xmlns:h="jakarta.faces.html">
                  <h:outputText value="Raport"/>
                  <p:dataTable value="#{users}" var="user">
                    <p:column><h:outputText value="#{user.login}"/></p:column>
                  </p:dataTable>
                  <p:commandLink action="finish" value="Wróć" ajax="true"/>
                </ui:composition>
                """
        ));
    }

    @Test
    void migratesTagsDeclaredWithTheRealIcefacesNamespace() {
        rewriteRun(xml(
                """
                <ui:composition xmlns:ice="http://www.icesoft.com/icefaces/component" xmlns:ui="jakarta.faces.facelets">
                  <ice:commandLink id="loginAction" actionListener="#{userBean.loginAction}"/>
                  <ice:outputText value="Login"/>
                </ui:composition>
                """,
                """
                <ui:composition xmlns:p="http://primefaces.org/ui" xmlns:ui="jakarta.faces.facelets" xmlns:h="jakarta.faces.html">
                  <p:commandLink id="loginAction" actionListener="#{userBean.loginAction}" ajax="true"/>
                  <h:outputText value="Login"/>
                </ui:composition>
                """
        ));
    }

    @Test
    void migratesActionMenuItemsAsAjaxPrimeFacesMenuItems() {
        rewriteRun(xml(
                """
                <ui:composition xmlns:ice="http://www.icesoft.com/icefaces/component" xmlns:ui="jakarta.faces.facelets">
                  <ice:menuBar>
                    <ice:menuItem value="Użytkownicy" action="#{userBean.returnUsers}"/>
                  </ice:menuBar>
                </ui:composition>
                """,
                """
                <ui:composition xmlns:p="http://primefaces.org/ui" xmlns:ui="jakarta.faces.facelets" xmlns:h="jakarta.faces.html">
                  <p:menubar>
                    <p:menuitem value="Użytkownicy" action="#{userBean.returnUsers}" ajax="true"/>
                  </p:menubar>
                </ui:composition>
                """
        ));
    }

    @Test
    void preservesPanelTabLabelsAsPrimeFacesTabTitles() {
        rewriteRun(xml(
                """
                <ui:composition xmlns:ice="http://www.icesoft.com/icefaces/component" xmlns:ui="jakarta.faces.facelets">
                  <ice:panelTabSet>
                    <ice:panelTab label="Użytkownicy">Lista</ice:panelTab>
                    <ice:panelTab label="Profil">Dane</ice:panelTab>
                  </ice:panelTabSet>
                </ui:composition>
                """,
                """
                <ui:composition xmlns:p="http://primefaces.org/ui" xmlns:ui="jakarta.faces.facelets" xmlns:h="jakarta.faces.html">
                  <p:tabView>
                    <p:tab title="Użytkownicy">Lista</p:tab>
                    <p:tab title="Profil">Dane</p:tab>
                  </p:tabView>
                </ui:composition>
                """
        ));
    }

    @Test
    void convertsNestedRelativeFacesResourcesToLibraryReferences() {
        rewriteRun(xml(
                """
                <ui:composition xmlns:ice="http://www.icesoft.com/icefaces/component" xmlns:ui="jakarta.faces.facelets">
                  <ice:outputStyle href="../resources/css/ice.css"/>
                </ui:composition>
                """,
                """
                <ui:composition xmlns:p="http://primefaces.org/ui" xmlns:ui="jakarta.faces.facelets" xmlns:h="jakarta.faces.html">
                  <h:outputStylesheet name="ice.css" library="css"/>
                </ui:composition>
                """
        ));
    }

    @Test
    void allowsASeparateRecipeToHandleAClassifiedStructuralMigration() {
        rewriteRun(spec -> spec.recipe(new IcefacesTagsToPrimeFaces(Set.of("outputResource"))), xml(
                """
                <ui:composition xmlns:ice="primefaces" xmlns:ui="jakarta.faces.facelets">
                  <ice:outputResource id="downloadReport"/>
                  <ice:dataTable value="#{users}" var="user">
                    <ice:column><ice:outputText value="#{user.login}"/></ice:column>
                  </ice:dataTable>
                </ui:composition>
                """,
                """
                <ui:composition xmlns:p="http://primefaces.org/ui" xmlns:ui="jakarta.faces.facelets" xmlns:h="jakarta.faces.html">
                  <ice:outputResource id="downloadReport"/>
                  <p:dataTable value="#{users}" var="user">
                    <p:column><h:outputText value="#{user.login}"/></p:column>
                  </p:dataTable>
                </ui:composition>
                """
        ));
    }

    private static void assertMapping(List<ChangeTagName> mappings, String oldName, String newName) {
        assertTrue(mappings.stream().anyMatch(mapping -> oldName.equals(mapping.getElementName())
                && newName.equals(mapping.getNewName())), oldName + " -> " + newName);
    }
}
