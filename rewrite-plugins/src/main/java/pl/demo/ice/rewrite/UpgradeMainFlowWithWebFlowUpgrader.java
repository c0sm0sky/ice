package pl.demo.ice.rewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.xml.tree.Xml;
import org.springframework.webflow.upgrade.WebFlowUpgrader;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

/** Uruchamia oficjalny WebFlowUpgrader dla definicji Web Flow 1.x. */
public final class UpgradeMainFlowWithWebFlowUpgrader extends Recipe {
    private static final String SOURCE_NAMESPACE = "http://www.springframework.org/schema/webflow";

    @Override
    public String getDisplayName() {
        return "Upgrade legacy Spring Web Flow definition";
    }

    @Override
    public String getDescription() {
        return "Runs Spring's WebFlowUpgrader on legacy flow XML before the Web Flow 4 normalization recipe.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                if (!isLegacyFlow(document.getRoot())) return super.visitDocument(document, ctx);
                try {
                    StringWriter result = new StringWriter();
                    new WebFlowUpgrader().transform(
                            new StreamSource(new StringReader(document.printAll())),
                            new StreamResult(result));
                    Xml.Document upgraded = (Xml.Document) new XmlParser().parse(result.toString())
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("WebFlowUpgrader nie zwrócił XML-a."));
                    return upgraded.withId(document.getId())
                            .withSourcePath(document.getSourcePath())
                            .withCharset(document.getCharset())
                            .withCharsetBomMarked(document.isCharsetBomMarked())
                            .withMarkers(document.getMarkers())
                            .withFileAttributes(document.getFileAttributes());
                } catch (Exception e) {
                    throw new IllegalStateException("WebFlowUpgrader nie zaktualizował definicji flow.", e);
                }
            }
        };
    }

    private static boolean isLegacyFlow(Xml.Tag root) {
        if (root == null) return false;
        if (!"flow".equals(localName(root.getName()))) return false;
        if (root.getAttributes().stream().anyMatch(attribute -> "start-state".equals(attribute.getKeyAsString()))) {
            return false;
        }
        String prefix = prefix(root.getName());
        String namespaceAttribute = prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
        return root.getAttributes().stream()
                .filter(attribute -> namespaceAttribute.equals(attribute.getKeyAsString()))
                .anyMatch(attribute -> SOURCE_NAMESPACE.equals(attribute.getValueAsString()));
    }

    private static String localName(String name) {
        int separator = name.indexOf(':');
        return separator < 0 ? name : name.substring(separator + 1);
    }

    private static String prefix(String name) {
        int separator = name.indexOf(':');
        return separator < 0 ? "" : name.substring(0, separator);
    }
}
