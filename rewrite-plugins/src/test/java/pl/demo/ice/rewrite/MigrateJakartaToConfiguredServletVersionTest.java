package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.xml.AddTagAttribute;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.ChangeTagName;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class MigrateJakartaToConfiguredServletVersionTest implements org.openrewrite.test.RewriteTest {
    private static final String SCRIPT_PATH = "src/main/webapp/resources/js/primefaces-partial-submit.js";
    private static final String TEMPLATE_PATH = "src/main/webapp/WEB-INF/layouts/mainTemplate.xhtml";

    @Test
    void finalFacesPhaseRunsOfficialMigrationWithoutServletVersion() {
        MigrateJakartaToConfiguredServletVersion recipe = new MigrateJakartaToConfiguredServletVersion();
        recipe.phase = "faces-final";
        assertEquals(1, recipe.getRecipeList().size());
        assertEquals("org.openrewrite.java.migrate.jakarta.Faces4xMigrationToFaces41x", recipe.getRecipeList().get(0).getName());
    }

    @Test
    void composesOfficialJakartaMigrationBeforeDescriptorAlignment() {
        MigrateJakartaToConfiguredServletVersion recipe = new MigrateJakartaToConfiguredServletVersion();
        recipe.servletVersion = "6.1.0";

        var steps = recipe.getRecipeList();
        assertInstanceOf(ChangePackage.class, steps.get(0));
        assertEquals("org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta", steps.get(1).getName());
        assertEquals(11, steps.size());
        assertInstanceOf(AddTagAttribute.class, steps.get(2));
        assertInstanceOf(ChangeTagAttribute.class, steps.get(4));
        ChangeTagAttribute faceletsNamespace = (ChangeTagAttribute) steps.get(4);
        assertEquals("http://java.sun.com/jsf/facelets", faceletsNamespace.getOldValue());
        assertEquals("jakarta.faces.facelets", faceletsNamespace.getNewValue());
        assertInstanceOf(ChangeTagName.class, steps.get(8));
        assertInstanceOf(ChangeTagName.class, steps.get(9));
        assertInstanceOf(MigrateServletWebXml.class, steps.get(10));
        assertEquals("6.1.0", ((MigrateServletWebXml) steps.get(10)).servletVersion);
    }

    @Test
    void optionallyComposesOfficialFaces41Migration() {
        MigrateJakartaToConfiguredServletVersion recipe = new MigrateJakartaToConfiguredServletVersion();
        recipe.servletVersion = "6.1.0";
        recipe.migrateFaces41 = true;

        var steps = recipe.getRecipeList();
        assertInstanceOf(ChangePackage.class, steps.get(0));
        assertEquals("org.openrewrite.java.migrate.jakarta.Faces4xMigrationToFaces41x", steps.get(2).getName());
        assertEquals(12, steps.size());
        assertInstanceOf(MigrateServletWebXml.class, steps.get(11));
    }

    @Test
    void mapsIceTagsAfterJakartaAndFacesNamespaceChanges() {
        MigrateJakartaToConfiguredServletVersion jakarta = new MigrateJakartaToConfiguredServletVersion();
        jakarta.servletVersion = "6.1.0";
        jakarta.migrateFaces41 = true;

        rewriteRun(spec -> spec.recipe(new CompositeRecipe(List.of(
                jakarta,
                new IcefacesTagsToPrimeFaces(false)
        ))), xml(
                """
                <ui:composition xmlns:ice="http://www.icesoft.com/icefaces/component"
                                xmlns:f="http://java.sun.com/jsf/core"
                                xmlns:ui="http://java.sun.com/jsf/facelets">
                  <ice:commandLink actionListener="#{userBean.loginAction}"/>
                  <ice:outputText value="Login"/>
                </ui:composition>
                """,
                """
                <ui:composition xmlns:p="http://primefaces.org/ui"
                                xmlns:f="jakarta.faces.core"
                                xmlns:ui="jakarta.faces.facelets" xmlns:h="jakarta.faces.html">
                  <p:commandLink actionListener="#{userBean.loginAction}" ajax="true"/>
                  <h:outputText value="Login"/>
                </ui:composition>
                """
        ));
    }

    @Test
    void migratesPartialSubmitAfterJakartaWithinMigrationChain() throws Exception {
        MigrateJakartaToConfiguredServletVersion jakarta = new MigrateJakartaToConfiguredServletVersion();
        jakarta.servletVersion = "6.1.0";
        String script;
        try (InputStream resource = getClass().getResourceAsStream("/primefaces-partial-submit.js")) {
            if (resource == null) throw new IllegalStateException("Missing packaged partial submit script");
            script = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }

        rewriteRun(spec -> spec.recipe(new CompositeRecipe(List.of(
                jakarta,
                new IcefacesTagsToPrimeFaces()
        ))),
                xml(
                        """
                        <ui:composition xmlns:p="http://primefaces.org/ui" xmlns:ui="jakarta.faces.facelets" xmlns:f="jakarta.faces.core">
                          <p:selectOneMenu id="role" value="#{bean.role}" partialSubmit="true">
                            <f:selectItems value="#{bean.roles}" />
                          </p:selectOneMenu>
                        </ui:composition>
                        """,
                        """
                        <ui:composition xmlns:p="http://primefaces.org/ui" xmlns:ui="jakarta.faces.facelets" xmlns:f="jakarta.faces.core" xmlns:h="jakarta.faces.html">
                          <p:selectOneMenu id="role" value="#{bean.role}">
                            <f:selectItems value="#{bean.roles}" />
                            <p:ajax process="@this" update="@form" onstart="PrimeFacesPartialSubmit.capture(cfg.source)" oncomplete="PrimeFacesPartialSubmit.restore()" onerror="PrimeFacesPartialSubmit.clear()" />
                          </p:selectOneMenu>
                        </ui:composition>
                        """
                ),
                text("<h:head>\n</h:head>", "<h:head>\n<h:outputScript library=\"js\" name=\"primefaces-partial-submit.js\" target=\"head\" />\n            </h:head>",
                        spec -> spec.path(TEMPLATE_PATH)),
                text(null, script, spec -> spec.path(SCRIPT_PATH))
        );
    }

    @Test
    void migratesFacesImportsEvenWhenMyFacesTypesAreUnattributed() {
        MigrateJakartaToConfiguredServletVersion recipe = new MigrateJakartaToConfiguredServletVersion();
        recipe.servletVersion = "6.1.0";

        rewriteRun(spec -> spec.recipe(recipe), org.openrewrite.java.Assertions.java(
                """
                package pl.demo;
                import javax.faces.context.FacesContext;
                public class LegacyFacesBean {
                }
                """,
                """
                package pl.demo;
                import jakarta.faces.context.FacesContext;
                public class LegacyFacesBean {
                }
                """
        ));
    }


    @Test
    void convertsDefaultXhtmlHeadAndBodyToFacesComponents() {
        MigrateJakartaToConfiguredServletVersion recipe = new MigrateJakartaToConfiguredServletVersion();
        recipe.servletVersion = "6.1.0";

        rewriteRun(spec -> spec.recipe(recipe), xml(
                """
                <f:view xmlns:f="jakarta.faces.core" xmlns:h="jakarta.faces.html"
                        xmlns="http://www.w3.org/1999/xhtml">
                  <html><head><title>Fixture</title></head><body><h:outputStylesheet library="css" name="app.css"/></body></html>
                </f:view>
                """,
                """
                <f:view xmlns:f="jakarta.faces.core" xmlns:h="jakarta.faces.html"
                        xmlns="http://www.w3.org/1999/xhtml">
                  <html><h:head><title>Fixture</title></h:head><h:body><h:outputStylesheet library="css" name="app.css"/></h:body></html>
                </f:view>
                """
        ));
    }
}
