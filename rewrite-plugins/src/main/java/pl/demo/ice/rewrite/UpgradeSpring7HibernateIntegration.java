package pl.demo.ice.rewrite;

import org.openrewrite.Recipe;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.ChangeTagValue;

import java.util.List;

/** Migruje integrację Spring ORM z Hibernate 5 do pakietu wprowadzonego w Spring Framework 7. */
public final class UpgradeSpring7HibernateIntegration extends Recipe {
    private static final String OLD_PACKAGE = "org.springframework.orm.hibernate5.";
    private static final String NEW_PACKAGE = "org.springframework.orm.jpa.hibernate.";

    @Override
    public String getDisplayName() {
        return "Upgrade Spring ORM Hibernate XML for Spring 7";
    }

    @Override
    public String getDescription() {
        return "Moves Spring ORM Hibernate bean classes and SpringSessionContext to the Spring Framework 7 package.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                new ChangeTagAttribute("//*[local-name()='bean'][@class='" + OLD_PACKAGE
                        + "LocalSessionFactoryBean']", "class", NEW_PACKAGE + "LocalSessionFactoryBean",
                        OLD_PACKAGE + "LocalSessionFactoryBean", false),
                new ChangeTagAttribute("//*[local-name()='bean'][@class='" + OLD_PACKAGE
                        + "HibernateTransactionManager']", "class", NEW_PACKAGE + "HibernateTransactionManager",
                        OLD_PACKAGE + "HibernateTransactionManager", false),
                new ChangeTagValue("//*[local-name()='prop'][@key='hibernate.current_session_context_class']",
                        OLD_PACKAGE + "SpringSessionContext", NEW_PACKAGE + "SpringSessionContext", false)
        );
    }
}
