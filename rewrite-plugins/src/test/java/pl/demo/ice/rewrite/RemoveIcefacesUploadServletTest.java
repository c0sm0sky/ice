package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;
import static org.openrewrite.xml.Assertions.xml;

class RemoveIcefacesUploadServletTest implements RewriteTest {
    @Override public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new RemoveIcefacesUploadServlet());
    }

    @Test void removesOnlyMatchingServletAndAllItsMappingsWithArbitraryNames() {
        rewriteRun(xml("""
          <web-app xmlns="http://java.sun.com/xml/ns/javaee">
            <servlet><servlet-class>com.icesoft.faces.component.inputfile.FileUploadServlet</servlet-class><servlet-name>legacy-upload</servlet-name></servlet>
            <servlet><servlet-name>other</servlet-name><servlet-class>example.Upload</servlet-class></servlet>
            <servlet-mapping><servlet-name>legacy-upload</servlet-name><url-pattern>/one</url-pattern></servlet-mapping>
            <servlet-mapping><url-pattern>/two</url-pattern><servlet-name>legacy-upload</servlet-name></servlet-mapping>
            <servlet-mapping><servlet-name>other</servlet-name><url-pattern>/keep</url-pattern></servlet-mapping>
          </web-app>
          """, """
          <web-app xmlns="http://java.sun.com/xml/ns/javaee">
            <servlet><servlet-name>other</servlet-name><servlet-class>example.Upload</servlet-class></servlet>
            <servlet-mapping><servlet-name>other</servlet-name><url-pattern>/keep</url-pattern></servlet-mapping>
          </web-app>
          """, spec -> spec.path("other/WEB-INF/web.xml")));
    }

    @Test void supportsPrefixedJakartaDescriptor() {
        rewriteRun(xml("""
          <j:web-app xmlns:j="https://jakarta.ee/xml/ns/jakartaee">
            <j:servlet><j:servlet-name>upload</j:servlet-name><j:servlet-class>com.icesoft.faces.component.inputfile.FileUploadServlet</j:servlet-class></j:servlet>
            <j:servlet-mapping><j:servlet-name>upload</j:servlet-name><j:url-pattern>/file</j:url-pattern></j:servlet-mapping>
          </j:web-app>
          """, """
          <j:web-app xmlns:j="https://jakarta.ee/xml/ns/jakartaee">
          </j:web-app>
          """, spec -> spec.path("src/main/webapp/WEB-INF/web.xml")));
    }

    @Test void leavesUnrelatedServletAndNonWebXmlUnchanged() {
        rewriteRun(xml("""
          <web-app><servlet><servlet-name>keep</servlet-name><servlet-class>example.Upload</servlet-class></servlet></web-app>
          """, spec -> spec.path("WEB-INF/web.xml")), xml("""
          <web-app><servlet><servlet-name>x</servlet-name><servlet-class>com.icesoft.faces.component.inputfile.FileUploadServlet</servlet-class></servlet></web-app>
          """, spec -> spec.path("other.xml")));
    }
}
