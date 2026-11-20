package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.xml.tree.Xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeMainFlowWithWebFlowUpgraderTest {
    private final UpgradeMainFlowWithWebFlowUpgrader recipe = new UpgradeMainFlowWithWebFlowUpgrader();

    @Test
    void usesSpringUpgraderForLegacyFlowXmlAndIsIdempotent() {
        Xml.Document source = parse("""
          <flow xmlns="http://www.springframework.org/schema/webflow"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.springframework.org/schema/webflow http://www.springframework.org/schema/webflow/spring-webflow-1.0.xsd">
            <start-actions>
              <bean-action bean="bean" method="call">
                <method-arguments><argument expression="value"/></method-arguments>
              </bean-action>
            </start-actions>
            <start-state idref="home"/>
            <view-state id="home" view="/home.xhtml"/>
          </flow>
          """);

        Xml.Document upgraded = (Xml.Document) recipe.getVisitor().visit(source, new InMemoryExecutionContext());
        String result = upgraded.printAll();
        assertTrue(result.contains("https://www.springframework.org/schema/webflow/spring-webflow.xsd"));
        assertTrue(result.contains("<webflow:view-state id=\"home\""));
        assertTrue(result.contains("start-state=\"home\""));
        assertTrue(result.contains("expression=\"bean.call(value)\""));

        Xml.Document secondPass = (Xml.Document) recipe.getVisitor().visit(upgraded, new InMemoryExecutionContext());
        assertEquals(result, secondPass.printAll());
    }

    @Test
    void leavesNonWebFlowXmlUnchanged() {
        Xml.Document source = parse("<flow xmlns=\"urn:unrelated\"><start-state idref=\"home\"/></flow>");
        Xml.Document result = (Xml.Document) recipe.getVisitor().visit(source, new InMemoryExecutionContext());
        assertEquals(source.printAll(), result.printAll());
    }

    private static Xml.Document parse(String source) {
        return (Xml.Document) new XmlParser().parse(source).map(SourceFile.class::cast).findFirst().orElseThrow();
    }
}
