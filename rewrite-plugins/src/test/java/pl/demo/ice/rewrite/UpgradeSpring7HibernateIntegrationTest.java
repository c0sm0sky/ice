package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class UpgradeSpring7HibernateIntegrationTest implements RewriteTest {
    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new UpgradeSpring7HibernateIntegration());
    }

    @Test
    void migratesSpringOrmHibernateClassesForSpring7() {
        rewriteRun(xml("""
          <beans xmlns="http://www.springframework.org/schema/beans"
                 xmlns:flow="http://www.springframework.org/schema/webflow-config">
            <bean class="org.springframework.orm.hibernate5.LocalSessionFactoryBean"/>
            <bean class="org.springframework.orm.hibernate5.HibernateTransactionManager"/>
            <prop key="hibernate.current_session_context_class">org.springframework.orm.hibernate5.SpringSessionContext</prop>
          </beans>
          """, """
          <beans xmlns="http://www.springframework.org/schema/beans"
                 xmlns:flow="http://www.springframework.org/schema/webflow-config">
            <bean class="org.springframework.orm.jpa.hibernate.LocalSessionFactoryBean"/>
            <bean class="org.springframework.orm.jpa.hibernate.HibernateTransactionManager"/>
            <prop key="hibernate.current_session_context_class">org.springframework.orm.jpa.hibernate.SpringSessionContext</prop>
          </beans>
          """));
    }
}
