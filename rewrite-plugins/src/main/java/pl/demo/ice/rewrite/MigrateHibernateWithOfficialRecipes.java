package pl.demo.ice.rewrite;

import org.openrewrite.Recipe;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Uruchamia oficjalne recepty Hibernate dostępne z repozytorium Maven Central. */
public final class MigrateHibernateWithOfficialRecipes extends Recipe {
    private static final String OFFICIAL_RECIPE = "org.openrewrite.hibernate.MigrateToHibernate71";
    private static final Map<String, String> QUERY_METHOD_RENAMES = Map.ofEntries(
            Map.entry("setBigDecimal", "setParameter"),
            Map.entry("setBigInteger", "setParameter"),
            Map.entry("setBinary", "setParameter"),
            Map.entry("setBoolean", "setParameter"),
            Map.entry("setByte", "setParameter"),
            Map.entry("setCharacter", "setParameter"),
            Map.entry("setDouble", "setParameter"),
            Map.entry("setEntity", "setParameter"),
            Map.entry("setFloat", "setParameter"),
            Map.entry("setInteger", "setParameter"),
            Map.entry("setLocale", "setParameter"),
            Map.entry("setLong", "setParameter"),
            Map.entry("setSerializable", "setParameter"),
            Map.entry("setShort", "setParameter"),
            Map.entry("setText", "setParameter"));
    private static final Map<String, String> TEMPORAL_TYPES = Map.of(
            "setDate", "DATE",
            "setCalendarDate", "DATE",
            "setTime", "TIME",
            "setTimestamp", "TIMESTAMP",
            "setCalendar", "TIMESTAMP");
    private static final Pattern FROM_ENTITY = Pattern.compile(
            "(?is)^\\s*from\\s+([\\w.$]+)(?:\\s+(?:as\\s+)?([\\w$]+))?");
    private static final Pattern SELECT_ENTITY = Pattern.compile(
            "(?is)^\\s*select\\s+(?:distinct\\s+)?([\\w$]+)\\s+from\\s+([\\w.$]+)(?:\\s+(?:as\\s+)?([\\w$]+))?");
    private static final Pattern MUTATION = Pattern.compile("(?is)^\\s*(insert|update|delete)\\b");

    @Override
    public String getDisplayName() {
        return "Apply official Hibernate migration recipes";
    }

    @Override
    public String getDescription() {
        return "Bridges legacy Hibernate APIs not covered by the official 6.0-to-7.1 migration chain, then runs that chain.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        Recipe migration = Environment.builder()
                .scanRuntimeClasspath("org.openrewrite.hibernate")
                .build()
                .activateRecipes(OFFICIAL_RECIPE);
        List<Recipe> recipes = new ArrayList<>();
        addRenames(recipes, "org.hibernate.Query", Map.of("setString", "setParameter"));
        addRenames(recipes, "org.hibernate.query.Query", Map.of("setString", "setParameter"));
        addRenames(recipes, "org.hibernate.Query", QUERY_METHOD_RENAMES);
        addRenames(recipes, "org.hibernate.query.Query", QUERY_METHOD_RENAMES);
        addRenames(recipes, "org.hibernate.Session", Map.of(
                "createSQLQuery", "createNativeQuery"));
        addRenames(recipes, "org.hibernate.StatelessSession", Map.of(
                "createSQLQuery", "createNativeQuery"));
        addRename(recipes, "org.hibernate.Session#setFlushMode(org.hibernate.FlushMode)", "setHibernateFlushMode");
        addRename(recipes, "org.hibernate.Session#getFlushMode()", "getHibernateFlushMode");
        addRename(recipes, "org.hibernate.Query#setFlushMode(org.hibernate.FlushMode)", "setHibernateFlushMode");
        addRename(recipes, "org.hibernate.Query#getFlushMode()", "getHibernateFlushMode");
        addRename(recipes, "org.hibernate.query.Query#setFlushMode(org.hibernate.FlushMode)", "setHibernateFlushMode");
        addRename(recipes, "org.hibernate.query.Query#getFlushMode()", "getHibernateFlushMode");
        recipes.add(new HibernateQueryMigrations());
        recipes.add(migration);
        return recipes;
    }

    /** Łączy migrację setterów temporalnych i tworzenia zapytań HQL w jednej recepcie. */
    private static final class HibernateQueryMigrations extends Recipe {
        @Override
        public String getDisplayName() {
            return "Migrate legacy Hibernate query APIs";
        }

        @Override
        public String getDescription() {
            return "Uses Hibernate 7 temporal parameters and typed query creation when the HQL form is clear.";
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return new JavaIsoVisitor<>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                    J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                    String temporalType = TEMPORAL_TYPES.get(visited.getSimpleName());
                    if (temporalType != null && visited.getArguments().size() == 2
                            && isHibernateQuery(visited.getMethodType())) {
                        return migrateTemporalParameter(visited, temporalType);
                    }
                    if (!"createQuery".equals(visited.getSimpleName()) || visited.getArguments().size() != 1
                            || !isHibernateQueryProducer(visited.getMethodType())
                            || !(visited.getArguments().getFirst() instanceof J.Literal literal)
                            || !(literal.getValue() instanceof String hql)) {
                        return visited;
                    }

                    if (MUTATION.matcher(hql).find()) {
                        return replaceInvocation(visited, "createMutationQuery", null);
                    }
                    String entityType = selectedEntityType(hql);
                    return entityType == null ? visited : replaceInvocation(visited, "createSelectionQuery", entityType);
                }

                private J.MethodInvocation migrateTemporalParameter(J.MethodInvocation method, String temporalType) {
                    String receiver = method.getSelect() == null ? "" : "#{any()}.";
                    String template = receiver + "setParameter(#{any()}, #{any()}, jakarta.persistence.TemporalType."
                            + temporalType + ")";
                    Object[] parameters = method.getSelect() == null
                            ? new Object[]{method.getArguments().get(0), method.getArguments().get(1)}
                            : new Object[]{method.getSelect(), method.getArguments().get(0), method.getArguments().get(1)};
                    return (J.MethodInvocation) JavaTemplate.builder(template)
                            .build().apply(getCursor(), method.getCoordinates().replace(), parameters);
                }

                private J.MethodInvocation replaceInvocation(J.MethodInvocation method, String newName, String resultType) {
                    String receiver = method.getSelect() == null ? "" : "#{any()}.";
                    String template = receiver + newName + "(#{any(java.lang.String)}"
                            + (resultType == null ? "" : ", " + resultType + ".class") + ")";
                    Object[] parameters = method.getSelect() == null
                            ? new Object[]{method.getArguments().getFirst()}
                            : new Object[]{method.getSelect(), method.getArguments().getFirst()};
                    return (J.MethodInvocation) JavaTemplate.builder(template)
                            .build().apply(getCursor(), method.getCoordinates().replace(), parameters);
                }

                private String selectedEntityType(String hql) {
                    Matcher select = SELECT_ENTITY.matcher(hql);
                    if (select.find()) {
                        String selected = select.group(1);
                        String entity = select.group(2);
                        String alias = select.group(3);
                        if (alias == null || !alias.equals(selected)) {
                            return null;
                        }
                        return resolveEntityType(entity);
                    }
                    Matcher from = FROM_ENTITY.matcher(hql);
                    return from.find() ? resolveEntityType(from.group(1)) : null;
                }

                private String resolveEntityType(String entityName) {
                    if (entityName.contains(".")) {
                        return entityName;
                    }
                    J.CompilationUnit compilationUnit = getCursor().firstEnclosing(J.CompilationUnit.class);
                    if (compilationUnit == null) {
                        return null;
                    }
                    for (J.Import anImport : compilationUnit.getImports()) {
                        String importedType = anImport.getQualid().printTrimmed(getCursor());
                        if (importedType.endsWith("." + entityName)) {
                            return importedType;
                        }
                    }
                    if (compilationUnit.getPackageDeclaration() == null) {
                        return entityName;
                    }
                    return compilationUnit.getPackageDeclaration().getExpression().printTrimmed(getCursor())
                            + "." + entityName;
                }
            };
        }
    }

    private static boolean isHibernateQuery(JavaType.Method method) {
        if (method == null || method.getDeclaringType() == null) {
            return false;
        }
        String owner = method.getDeclaringType().getFullyQualifiedName();
        return List.of("org.hibernate.Query", "org.hibernate.query.Query").contains(owner);
    }

    private static boolean isHibernateQueryProducer(JavaType.Method method) {
        if (method == null || method.getDeclaringType() == null) {
            return false;
        }
        String owner = method.getDeclaringType().getFullyQualifiedName();
        return List.of("org.hibernate.Session", "org.hibernate.StatelessSession", "org.hibernate.QueryProducer")
                .contains(owner);
    }

    private static void addRenames(List<Recipe> recipes, String owner, Map<String, String> renames) {
        renames.forEach((oldName, newName) -> recipes.add(new ChangeMethodName(
                owner + "#" + oldName + "(..)", newName, false, false)));
    }

    private static void addRename(List<Recipe> recipes, String methodPattern, String newName) {
        recipes.add(new ChangeMethodName(methodPattern, newName, false, false));
    }
}
