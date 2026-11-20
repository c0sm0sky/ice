package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class UpgradeSpringFacesIntegrationTest implements RewriteTest {
    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new UpgradeSpringFacesIntegration());
    }

    @Test
    void addsSpringFacesIntegrationAndNormalizesFlowLocations() {
        rewriteRun(xml("""
          <?xml version="1.0" encoding="UTF-8"?>
          <beans xmlns="http://www.springframework.org/schema/beans"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xmlns:flow="http://www.springframework.org/schema/webflow-config"
                 xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.0.xsd http://www.springframework.org/schema/webflow-config http://www.springframework.org/schema/webflow-config/spring-webflow-config-1.0.xsd">
            <flow:flow-executor id="flowExecutor"/>
            <flow:flow-registry id="flowRegistry" flow-builder-services="flowBuilderServices" base-path="/WEB-INF">
              <flow:flow-location path="/WEB-INF/flows/main-flow.xml"/>
            </flow:flow-registry>
          </beans>
          """, """
          <?xml version="1.0" encoding="UTF-8"?>
          <beans xmlns="http://www.springframework.org/schema/beans"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xmlns:flow="http://www.springframework.org/schema/webflow-config"
                 xmlns:faces="http://www.springframework.org/schema/faces"
                 xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd http://www.springframework.org/schema/webflow-config https://www.springframework.org/schema/webflow-config/spring-webflow-config.xsd http://www.springframework.org/schema/faces https://www.springframework.org/schema/faces/spring-faces.xsd">
            <flow:flow-executor id="flowExecutor">
              <flow:flow-execution-listeners><flow:listener ref="facesContextListener"/></flow:flow-execution-listeners>
            </flow:flow-executor>
            <flow:flow-registry id="flowRegistry" flow-builder-services="flowBuilderServices" base-path="/WEB-INF">
              <flow:flow-location path="/flows/main-flow.xml" id="main-flow"/>
            </flow:flow-registry>
            <faces:flow-builder-services id="flowBuilderServices"/>
            <bean id="facesContextListener" class="org.springframework.faces.webflow.FlowFacesContextLifecycleListener"/>
          </beans>
          """));
    }

    @Test
    void doesNotDuplicateExistingFacesIntegration() {
        rewriteRun(xml("""
          <beans xmlns="http://www.springframework.org/schema/beans"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xmlns:faces="http://www.springframework.org/schema/faces"
                 xmlns:flow="http://www.springframework.org/schema/webflow-config"
                 xsi:schemaLocation="http://www.springframework.org/schema/faces https://www.springframework.org/schema/faces/spring-faces.xsd">
            <faces:flow-builder-services id="flowBuilderServices"/>
            <bean id="facesContextListener" class="org.springframework.faces.webflow.FlowFacesContextLifecycleListener"/>
            <flow:flow-executor id="flowExecutor">
              <flow:flow-execution-listeners><flow:listener ref="facesContextListener"/></flow:flow-execution-listeners>
            </flow:flow-executor>
          </beans>
          """));
    }

    @Test
    void addsBuilderServicesToARegistryThatWasAlreadyMigrated() {
        rewriteRun(xml("""
          <beans xmlns="http://www.springframework.org/schema/beans"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xmlns:flow="http://www.springframework.org/schema/webflow-config"
                 xsi:schemaLocation="http://www.springframework.org/schema/webflow-config http://www.springframework.org/schema/webflow-config/spring-webflow-config-1.0.xsd">
            <flow:flow-executor id="flowExecutor" flow-registry="flowRegistry"/>
            <flow:flow-registry id="flowRegistry">
              <flow:flow-location path="/flows/main-flow.xml" id="main-flow"/>
            </flow:flow-registry>
          </beans>
          """, """
          <beans xmlns="http://www.springframework.org/schema/beans"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xmlns:flow="http://www.springframework.org/schema/webflow-config"
                 xmlns:faces="http://www.springframework.org/schema/faces"
                 xsi:schemaLocation="http://www.springframework.org/schema/webflow-config https://www.springframework.org/schema/webflow-config/spring-webflow-config.xsd http://www.springframework.org/schema/faces https://www.springframework.org/schema/faces/spring-faces.xsd">
            <flow:flow-executor id="flowExecutor" flow-registry="flowRegistry">
              <flow:flow-execution-listeners><flow:listener ref="facesContextListener"/></flow:flow-execution-listeners>
            </flow:flow-executor>
            <flow:flow-registry id="flowRegistry" flow-builder-services="flowBuilderServices" base-path="/WEB-INF">
              <flow:flow-location path="/flows/main-flow.xml" id="main-flow"/>
            </flow:flow-registry>
            <faces:flow-builder-services id="flowBuilderServices"/>
            <bean id="facesContextListener" class="org.springframework.faces.webflow.FlowFacesContextLifecycleListener"/>
          </beans>
          """));
    }
}
