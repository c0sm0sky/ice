package pl.demo.ice.rewrite;

import org.openrewrite.Recipe;
import org.openrewrite.Option;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.ChangeTagValue;
import org.openrewrite.xml.RemoveXmlTag;

import java.util.List;

/** Migruje deklaracje Spring 2 i Hibernate 3 do konfiguracji zgodnej ze Spring Framework 5. */
public final class MigrateSpringInfrastructure extends Recipe {
    @Option(displayName = "Migration phase", description = "Use legacy before the Spring upgrade and spring7 afterwards.", example = "legacy")
    public String phase = "legacy";

    public MigrateSpringInfrastructure() {}

    public MigrateSpringInfrastructure(String phase) { this.phase = phase; }
    private static final String OLD_DBCP = "org.apache.commons.dbcp.BasicDataSource";
    private static final String NEW_DBCP = "org.apache.commons.dbcp2.BasicDataSource";
    private static final String OLD_SESSION_FACTORY = "org.springframework.orm.hibernate3.LocalSessionFactoryBean";
    private static final String NEW_SESSION_FACTORY = "org.springframework.orm.hibernate5.LocalSessionFactoryBean";
    private static final String OLD_TRANSACTION_MANAGER = "org.springframework.orm.hibernate3.HibernateTransactionManager";
    private static final String NEW_TRANSACTION_MANAGER = "org.springframework.orm.hibernate5.HibernateTransactionManager";
    private static final String OLD_PLACEHOLDER_CONFIGURER =
            "org.springframework.beans.factory.config.PropertyPlaceholderConfigurer";
    private static final String NEW_PLACEHOLDER_CONFIGURER =
            "org.springframework.context.support.PropertySourcesPlaceholderConfigurer";

    @Override
    public String getDisplayName() {
        return "Migrate Spring infrastructure XML";
    }

    @Override
    public String getDescription() {
        return "Migrates legacy Spring infrastructure and Spring ORM Hibernate integration in ordered phases.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        if ("spring7".equals(phase)) return spring7();
        if (!"legacy".equals(phase)) throw new IllegalArgumentException("Unknown Spring migration phase: " + phase);
        return List.of(
                new ChangeTagAttribute(beanClass(OLD_DBCP), "class", NEW_DBCP, OLD_DBCP, false),
                new ChangeTagAttribute(beanClass(OLD_SESSION_FACTORY), "class", NEW_SESSION_FACTORY,
                        OLD_SESSION_FACTORY, false),
                new ChangeTagAttribute(beanClass(OLD_TRANSACTION_MANAGER), "class", NEW_TRANSACTION_MANAGER,
                        OLD_TRANSACTION_MANAGER, false),
                new ChangeTagAttribute("//*[local-name()='bean'][@class='" + NEW_DBCP
                        + "']/*[local-name()='property'][@name='maxActive']", "name", "maxTotal", "maxActive", false),
                new ChangeTagValue("//*[local-name()='prop'][@key='hibernate.current_session_context_class']",
                        "org.springframework.orm.hibernate3.SpringSessionContext",
                        "org.springframework.orm.hibernate5.SpringSessionContext", false),
                new ChangeTagAttribute(beanClass(OLD_PLACEHOLDER_CONFIGURER), "class",
                        NEW_PLACEHOLDER_CONFIGURER, OLD_PLACEHOLDER_CONFIGURER, false),
                new RemoveXmlTag("//*[local-name()='bean'][@class='" + NEW_PLACEHOLDER_CONFIGURER
                        + "']/*[local-name()='property'][@name='systemPropertiesModeName']", "**/*.xml")
        );
    }

    private List<Recipe> spring7() {
        return List.of(
                new ChangeTagAttribute("//*[local-name()='bean'][@class='" + "org.springframework.orm.hibernate5."
                        + "LocalSessionFactoryBean']", "class", "org.springframework.orm.jpa.hibernate." + "LocalSessionFactoryBean",
                        "org.springframework.orm.hibernate5." + "LocalSessionFactoryBean", false),
                new ChangeTagAttribute("//*[local-name()='bean'][@class='" + "org.springframework.orm.hibernate5."
                        + "HibernateTransactionManager']", "class", "org.springframework.orm.jpa.hibernate." + "HibernateTransactionManager",
                        "org.springframework.orm.hibernate5." + "HibernateTransactionManager", false),
                new ChangeTagValue("//*[local-name()='prop'][@key='hibernate.current_session_context_class']",
                        "org.springframework.orm.hibernate5." + "SpringSessionContext", "org.springframework.orm.jpa.hibernate." + "SpringSessionContext", false)
        );
    }

    private static String beanClass(String className) {
        return "//*[local-name()='bean'][@class='" + className + "']";
    }
}
