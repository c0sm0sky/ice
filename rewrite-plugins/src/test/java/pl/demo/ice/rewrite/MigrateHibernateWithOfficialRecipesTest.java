package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeMethodName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrateHibernateWithOfficialRecipesTest {
    @Test
    void upgradesLegacyQueryMethodNamesForAnyJavaSource() {
        MigrateHibernateWithOfficialRecipes recipe = new MigrateHibernateWithOfficialRecipes();
        List<Recipe> recipes = recipe.getRecipeList();

        assertRename(recipes, "org.hibernate.Query#setBigDecimal(..)", "setParameter");
        assertRename(recipes, "org.hibernate.Query#setString(..)", "setParameter");
        assertRename(recipes, "org.hibernate.Query#setEntity(..)", "setParameter");
        assertRename(recipes, "org.hibernate.query.Query#setText(..)", "setParameter");
        assertRename(recipes, "org.hibernate.Session#createSQLQuery(..)", "createNativeQuery");
        assertRename(recipes, "org.hibernate.StatelessSession#createSQLQuery(..)", "createNativeQuery");
        assertNoRename(recipes, "org.hibernate.Query#setDate(..)");
        assertRename(recipes, "org.hibernate.Session#setFlushMode(org.hibernate.FlushMode)", "setHibernateFlushMode");
        assertRename(recipes, "org.hibernate.Session#getFlushMode()", "getHibernateFlushMode");
        assertRename(recipes, "org.hibernate.Query#setFlushMode(org.hibernate.FlushMode)", "setHibernateFlushMode");
        assertTrue(recipes.stream().anyMatch(nestedRecipe ->
                "Migrate legacy Hibernate query APIs".equals(nestedRecipe.getDisplayName())));
        assertEquals("org.openrewrite.hibernate.MigrateToHibernate71",
                recipes.get(recipes.size() - 1).getName());
    }

    private static void assertRename(List<Recipe> recipes, String methodPattern, String newMethodName) {
        ChangeMethodName rename = recipes.stream()
                .filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .filter(recipe -> methodPattern.equals(recipe.getMethodPattern()))
                .findFirst()
                .orElse(null);
        assertNotNull(rename, methodPattern);
        assertEquals(newMethodName, rename.getNewMethodName());
    }

    private static void assertNoRename(List<Recipe> recipes, String methodPattern) {
        assertTrue(recipes.stream()
                .filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .noneMatch(recipe -> methodPattern.equals(recipe.getMethodPattern())), methodPattern);
    }
}
