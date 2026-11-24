package pl.demo.ice.rewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Przenosi sortowanie z nagłówka ICEfaces do sortBy kolumny PrimeFaces. */
public final class MigrateIcefacesTableSorting extends Recipe {
    @Override
    public List<Recipe> getRecipeList() {
        return List.of(new CachePrimeFacesSortableCollection());
    }

    @Override
    public String getDisplayName() {
        return "Migrate ICEfaces data table sorting";
    }

    @Override
    public String getDescription() {
        return "Moves commandSortHeader column metadata to PrimeFaces column sortBy and keeps the header contents.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag updated = super.visitTag(tag, ctx);
                String name = localName(updated.getName());
                if ("dataTable".equals(name)) {
                    return withoutAttributes(updated, "sortColumn", "sortAscending");
                }
                if (!"column".equals(name)) return updated;

                Xml.Tag sortHeader = findSortHeader(updated);
                if (sortHeader == null) return updated;
                Object parentValue = getCursor().getParentTreeCursor().getValue();
                Xml.Tag table = parentValue instanceof Xml.Tag parent && "dataTable".equals(localName(parent.getName()))
                        ? parent : null;
                String variable = table == null ? null : attribute(table, "var");
                String column = attribute(sortHeader, "columnName");
                if (variable == null || variable.isBlank() || column == null || column.isBlank()) return updated;

                String property = column.startsWith("#{") && column.endsWith("}")
                        ? column.substring(2, column.length() - 1) : variable + "." + column;
                updated = withAttribute(updated, "sortBy", "#{" + property + "}");
                return unwrapSortHeader(updated);
            }
        };
    }

    private static Xml.Tag withoutAttributes(Xml.Tag tag, String... names) {
        List<Xml.Attribute> attributes = new ArrayList<>(tag.getAttributes().size());
        boolean changed = false;
        for (Xml.Attribute attribute : tag.getAttributes()) {
            if (contains(names, localName(attribute.getKeyAsString()))) changed = true;
            else attributes.add(attribute);
        }
        return changed ? tag.withAttributes(attributes) : tag;
    }

    private static Xml.Tag withAttribute(Xml.Tag tag, String name, String value) {
        List<Xml.Attribute> attributes = new ArrayList<>(tag.getAttributes().size() + 1);
        Xml.Attribute existing = null;
        for (Xml.Attribute attribute : tag.getAttributes()) {
            if (name.equals(localName(attribute.getKeyAsString()))) existing = attribute;
            else attributes.add(attribute);
        }
        Xml.Attribute replacement = Xml.Tag.build("<column " + name + "=\"" + escape(value) + "\"/>")
                .getAttributes().get(0).withPrefix(" ");
        attributes.add(existing == null ? replacement : existing.withValue(existing.getValue().withValue(value)));
        return tag.withAttributes(attributes);
    }

    private static Xml.Tag unwrapSortHeader(Xml.Tag tag) {
        if (tag.getContent() == null) return tag;
        List<Content> content = new ArrayList<>(tag.getContent().size());
        boolean changed = false;
        for (Content child : tag.getContent()) {
            if (child instanceof Xml.Tag childTag && "commandSortHeader".equals(localName(childTag.getName()))) {
                if (childTag.getContent() != null) {
                    boolean firstContent = true;
                    for (Content headerContent : childTag.getContent()) {
                        if (!(headerContent instanceof Xml.CharData whitespace) || !whitespace.getText().isBlank()) {
                            if (firstContent && headerContent instanceof Xml.Tag firstTag) {
                                content.add(firstTag.withPrefix(childTag.getPrefix()));
                            } else {
                                content.add(headerContent);
                            }
                            firstContent = false;
                        }
                    }
                }
                changed = true;
            } else if (child instanceof Xml.Tag childTag) {
                Xml.Tag migrated = unwrapSortHeader(childTag);
                content.add(migrated);
                changed |= migrated != childTag;
            } else {
                content.add(child);
            }
        }
        return changed ? tag.withContent(content) : tag;
    }

    private static Xml.Tag findSortHeader(Xml.Tag tag) {
        if ("commandSortHeader".equals(localName(tag.getName()))) return tag;
        if (tag.getContent() != null) {
            for (Content child : tag.getContent()) {
                if (child instanceof Xml.Tag childTag) {
                    Xml.Tag found = findSortHeader(childTag);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private static String attribute(Xml.Tag tag, String name) {
        return tag.getAttributes().stream()
                .filter(attribute -> name.equals(localName(attribute.getKeyAsString())))
                .map(Xml.Attribute::getValueAsString).findFirst().orElse(null);
    }

    private static boolean contains(String[] values, String value) {
        for (String candidate : values) if (candidate.equals(value)) return true;
        return false;
    }

    private static String localName(String name) {
        int separator = name.indexOf(':');
        return separator < 0 ? name : name.substring(separator + 1);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    /** Dodaje stabilny model tylko dla list wyliczanych przez getter tabeli; nazwy pobiera z konfiguracji i XHTML. */
    private static final class CachePrimeFacesSortableCollection extends ScanningRecipe<BindingIndex> {
        private static final Pattern EL = Pattern.compile("#\\{\\s*([\\w$]+)\\.([\\w$]+)(?:\\s*\\([^}]*\\))?\\s*}");

        @Override
        public BindingIndex getInitialValue(ExecutionContext ctx) {
            return new BindingIndex();
        }

        @Override
        public String getDisplayName() {
            return "Keep computed PrimeFaces table models stable";
        }

        @Override
        public String getDescription() {
            return "Derives the table model, bean class and invalidating actions from Spring XML and XHTML bindings.";
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getScanner(BindingIndex index) {
            return new XmlIsoVisitor<>() {
                @Override
                public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                    Xml.Tag updated = super.visitTag(tag, ctx);
                    if ("bean".equals(localName(updated.getName()))) {
                        String id = attribute(updated, "id");
                        String className = attribute(updated, "class");
                        if (id != null && className != null) index.beanClasses.put(id, className);
                    }
                    if (!"dataTable".equals(localName(updated.getName()))) return updated;

                    Matcher model = EL.matcher(attribute(updated, "value") == null ? "" : attribute(updated, "value"));
                    if (!model.matches()) return updated;
                    String bean = model.group(1);
                    String property = model.group(2);
                    Set<String> invalidators = new LinkedHashSet<>();
                    invalidators.add("set" + capitalize(property));
                    Object parent = getCursor().getParentTreeCursor().getValue();
                    collectBindings(parent instanceof Xml.Tag parentTag ? parentTag : updated, bean, invalidators);
                    index.tables.add(new TableBinding(bean, property, invalidators));
                    return updated;
                }

                private void collectBindings(Xml.Tag tag, String bean, Set<String> invalidators) {
                    for (Xml.Attribute attr : tag.getAttributes()) {
                        String attributeName = localName(attr.getKeyAsString());
                        Matcher expression = EL.matcher(attr.getValueAsString());
                        while (expression.find()) {
                            if (!bean.equals(expression.group(1))) continue;
                            String name = expression.group(2);
                            if (Set.of("action", "actionListener", "listener").contains(attributeName)) {
                                invalidators.add(name);
                            } else if (attributeName.equals("value")) {
                                invalidators.add("set" + capitalize(name));
                            }
                        }
                    }
                    if (tag.getContent() != null) {
                        for (Content child : tag.getContent()) {
                            if (child instanceof Xml.Tag childTag) collectBindings(childTag, bean, invalidators);
                        }
                    }
                }
            };
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor(BindingIndex index) {
            return new JavaIsoVisitor<>() {
                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, ExecutionContext ctx) {
                    J.ClassDeclaration updated = declaration;
                    for (TableBinding binding : index.tables) {
                        if (!beanClass(binding, index).equals(updated.getSimpleName())) continue;
                        J.MethodDeclaration getter = findMethod(updated, getterName(binding));
                        if (getter == null || getter.getBody() == null || getter.getReturnTypeExpression() == null
                                || !returnsComputedLocal(getter)) continue;
                        String cacheName = cacheName(binding);
                        if (hasField(updated, cacheName)) continue;
                        String type = getter.getReturnTypeExpression().printTrimmed(getCursor());
                        updated = (J.ClassDeclaration) JavaTemplate.builder("private " + type + " " + cacheName + ";")
                                .contextSensitive().build().apply(getCursor(), updated.getBody().getCoordinates().firstStatement());
                    }
                    return super.visitClassDeclaration(updated, ctx);
                }

                @Override
                public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations variables, ExecutionContext ctx) {
                    J.VariableDeclarations updated = super.visitVariableDeclarations(variables, ctx);
                    J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    if (owner == null) return updated;
                    boolean cacheField = index.tables.stream().anyMatch(binding ->
                            beanClass(binding, index).equals(owner.getSimpleName())
                                    && updated.getVariables().stream().anyMatch(variable ->
                                    cacheName(binding).equals(variable.getSimpleName())));
                    if (!cacheField || hasComment(updated.getPrefix(), "Cache stabilnej listy tabeli")) return updated;
                    return updated.withPrefix(commentedPrefix(updated.getPrefix(),
                            "Cache stabilnej listy tabeli dodany podczas migracji ICEfaces -> PrimeFaces."));
                }

                @Override
                public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                    J.MethodDeclaration updated = super.visitMethodDeclaration(method, ctx);
                    if (updated.getBody() == null) return updated;
                    J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    if (owner == null) return updated;
                    for (TableBinding binding : index.tables) {
                        if (!beanClass(binding, index).equals(owner.getSimpleName())
                                || !binding.invalidators().contains(updated.getSimpleName())) continue;
                        J.MethodDeclaration getter = findMethod(owner, getterName(binding));
                        if (getter == null || !returnsComputedLocal(getter)) continue;
                        String cacheName = cacheName(binding);
                        if (updated.getBody().printTrimmed(getCursor()).contains(cacheName)) continue;
                        updated = (J.MethodDeclaration) JavaTemplate.builder(cacheName + " = null;")
                                .contextSensitive().build().apply(getCursor(), updated.getBody().getCoordinates().firstStatement());
                        updated = commentFirstStatement(updated,
                                "Unieważnia cache po zmianie filtrów lub danych tabeli.");
                    }
                    return updated;
                }

                @Override
                public J.Return visitReturn(J.Return returnTree, ExecutionContext ctx) {
                    J.Return updated = super.visitReturn(returnTree, ctx);
                    J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                    J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    if (enclosing == null || owner == null || !(updated.getExpression() instanceof J.Identifier identifier)
                            || !returnsComputedLocal(enclosing)) return updated;
                    for (TableBinding binding : index.tables) {
                        if (!beanClass(binding, index).equals(owner.getSimpleName())
                                || !getterName(binding).equals(enclosing.getSimpleName())) continue;
                        String cacheName = cacheName(binding);
                        J.Return cached = (J.Return) JavaTemplate.builder("return " + cacheName + " == null ? ("
                                        + cacheName + " = " + identifier.getSimpleName() + ") : " + cacheName + ";")
                                .contextSensitive().build().apply(getCursor(), updated.getCoordinates().replace());
                        return cached.withPrefix(commentedPrefix(cached.getPrefix(),
                                "Zachowuje tę samą listę, aby sortowanie PrimeFaces pozostało w modelu."));
                    }
                    return updated;
                }

                private J.MethodDeclaration findMethod(J.ClassDeclaration declaration, String name) {
                    return declaration.getBody().getStatements().stream().filter(J.MethodDeclaration.class::isInstance)
                            .map(J.MethodDeclaration.class::cast).filter(method -> name.equals(method.getSimpleName()))
                            .findFirst().orElse(null);
                }

                private boolean returnsComputedLocal(J.MethodDeclaration method) {
                    if (method.getBody() == null) return false;
                    Set<String> locals = new LinkedHashSet<>();
                    method.getBody().getStatements().stream().filter(J.VariableDeclarations.class::isInstance)
                            .map(J.VariableDeclarations.class::cast).flatMap(vars -> vars.getVariables().stream())
                            .forEach(variable -> locals.add(variable.getSimpleName()));
                    return method.getBody().getStatements().stream().filter(J.Return.class::isInstance)
                            .map(J.Return.class::cast).map(J.Return::getExpression).filter(J.Identifier.class::isInstance)
                            .map(J.Identifier.class::cast).anyMatch(identifier -> locals.contains(identifier.getSimpleName()));
                }

                private boolean hasField(J.ClassDeclaration declaration, String name) {
                    return declaration.getBody().getStatements().stream().filter(J.VariableDeclarations.class::isInstance)
                            .map(J.VariableDeclarations.class::cast).flatMap(vars -> vars.getVariables().stream())
                            .anyMatch(variable -> name.equals(variable.getSimpleName()));
                }

                private J.MethodDeclaration commentFirstStatement(J.MethodDeclaration method, String comment) {
                    List<Statement> statements = new ArrayList<>(method.getBody().getStatements());
                    if (!statements.isEmpty() && !hasComment(statements.get(0).getPrefix(), comment)) {
                        Statement first = statements.get(0);
                        statements.set(0, first.withPrefix(commentedPrefix(first.getPrefix(), comment)));
                    }
                    return method.withBody(method.getBody().withStatements(statements));
                }

                private Space commentedPrefix(Space prefix, String comment) {
                    List<Comment> comments = new ArrayList<>(prefix.getComments());
                    String whitespace = prefix.getWhitespace();
                    int lineBreak = whitespace.lastIndexOf('\n');
                    String indent = lineBreak < 0 ? "" : whitespace.substring(lineBreak + 1);
                    comments.add(new TextComment(false, " " + comment, "\n" + indent, Markers.EMPTY));
                    return prefix.withComments(comments);
                }

                private boolean hasComment(Space prefix, String text) {
                    return prefix.getComments().stream().filter(TextComment.class::isInstance)
                            .map(TextComment.class::cast).anyMatch(comment -> comment.getText().contains(text));
                }
            };
        }

        private static String beanClass(TableBinding binding, BindingIndex index) {
            String className = index.beanClasses.get(binding.bean());
            return className == null ? capitalize(binding.bean())
                    : className.substring(className.lastIndexOf('.') + 1);
        }

        private static String getterName(TableBinding binding) {
            return "get" + capitalize(binding.property());
        }

        private static String cacheName(TableBinding binding) {
            return binding.property() + "Cache";
        }

        private static String capitalize(String value) {
            return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
        }
    }

    private static final class BindingIndex {
        private final Set<TableBinding> tables = new LinkedHashSet<>();
        private final Map<String, String> beanClasses = new LinkedHashMap<>();
    }

    private record TableBinding(String bean, String property, Set<String> invalidators) {
        private TableBinding {
            invalidators = Set.copyOf(invalidators);
        }
    }

}
