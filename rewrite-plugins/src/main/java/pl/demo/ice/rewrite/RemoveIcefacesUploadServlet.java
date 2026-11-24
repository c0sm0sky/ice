package pl.demo.ice.rewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Set;
import java.util.stream.Collectors;

/** usuwa ICEfaces' upload servlet . */
public final class RemoveIcefacesUploadServlet extends Recipe {
    private static final String UPLOAD_CLASS = "com.icesoft.faces.component.inputfile.FileUploadServlet";

    @Override public String getDisplayName() { return "Remove the ICEfaces upload servlet"; }
    @Override public String getDescription() {
        return "Removes the obsolete ICEfaces upload servlet and its mappings from web.xml regardless of its configured name or URL.";
    }

    @Override public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles("**/web.xml"), new XmlIsoVisitor<ExecutionContext>() {
            @Override public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                if (!"web-app".equals(localName(tag.getName()))) return super.visitTag(tag, ctx);
                Set<String> names = tag.getChildren().stream()
                        .filter(child -> "servlet".equals(localName(child.getName())))
                        .filter(child -> UPLOAD_CLASS.equals(childValue(child, "servlet-class")))
                        .map(child -> childValue(child, "servlet-name"))
                        .filter(name -> name != null && !name.isBlank()).collect(Collectors.toSet());
                if (names.isEmpty()) return super.visitTag(tag, ctx);
                return tag.withContent(tag.getContent().stream().filter(content -> {
                    if (!(content instanceof Xml.Tag child)) return true;
                    String kind = localName(child.getName());
                    if ("servlet".equals(kind)) return !UPLOAD_CLASS.equals(childValue(child, "servlet-class"));
                    return !"servlet-mapping".equals(kind) || !names.contains(childValue(child, "servlet-name"));
                }).collect(Collectors.toList()));
            }
        });
    }

    private static String childValue(Xml.Tag tag, String name) {
        return tag.getChildren().stream().filter(child -> name.equals(localName(child.getName())))
                .map(child -> child.getValue().orElse("").trim()).findFirst().orElse(null);
    }

    private static String localName(String name) {
        int separator = name.indexOf(':');
        return separator < 0 ? name : name.substring(separator + 1);
    }
}
