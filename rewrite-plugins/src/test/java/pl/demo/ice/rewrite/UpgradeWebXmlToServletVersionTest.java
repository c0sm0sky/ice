package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class UpgradeWebXmlToServletVersionTest implements RewriteTest {
    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(recipe("6.1.0"));
    }

    @Test
    void derivesDescriptorAndSchemaVersionsFromServletApiVersion() {
        rewriteRun(xml("""
          <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_5_0.xsd"
                   version="5.0"/>
          """, """
          <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_1.xsd"
                   version="6.1"/>
          """));
    }

    @Test
    void supportsOtherConfiguredServletMinorVersions() {
        rewriteRun(spec -> spec.recipe(recipe("6.2.4")), xml("""
          <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_5_0.xsd"
                   version="5.0"/>
          """, """
          <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_2.xsd"
                   version="6.2"/>
          """));
    }

    @Test
    void doesNotChangeNonJakartaWebDescriptors() {
        rewriteRun(xml("""
          <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee" version="3.1"/>
          """));
    }

    private static MigrateServletWebXml recipe(String version) {
        MigrateServletWebXml recipe = new MigrateServletWebXml();
        recipe.servletVersion = version;
        return recipe;
    }
}
