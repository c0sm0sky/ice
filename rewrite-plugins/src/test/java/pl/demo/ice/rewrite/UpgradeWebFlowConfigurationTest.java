package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class UpgradeWebFlowConfigurationTest implements RewriteTest {
    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new UpgradeWebFlowConfiguration());
    }

    @Test
    void upgradesGenericExecutorRegistryAndLocations() {
        rewriteRun(xml("""
          <beans xmlns:flow="http://www.springframework.org/schema/webflow-config">
            <flow:executor id="flowExecutor" registry-ref="flowRegistry" repository-type="simple">
              <flow:execution-attributes>
                <flow:alwaysRedirectOnPause value="false"/>
              </flow:execution-attributes>
            </flow:executor>
            <flow:registry id="flowRegistry">
              <flow:location path="/WEB-INF/flows/main-flow.xml"/>
              <flow:location path="/WEB-INF/flows/*.xml"/>
            </flow:registry>
          </beans>
          """, """
          <beans xmlns:flow="http://www.springframework.org/schema/webflow-config">
            <flow:flow-executor id="flowExecutor" flow-registry="flowRegistry">
              <flow:flow-execution-repository max-execution-snapshots="30"/>
              <flow:flow-execution-attributes>
                <flow:always-redirect-on-pause value="false"/>
              </flow:flow-execution-attributes>
            </flow:flow-executor>
            <flow:flow-registry id="flowRegistry" flow-builder-services="flowBuilderServices" base-path="/WEB-INF">
              <flow:flow-location path="/WEB-INF/flows/main-flow.xml"/>
              <flow:flow-location-pattern value="/WEB-INF/flows/*.xml"/>
            </flow:flow-registry>
          </beans>
          """));
    }

    @Test
    void upgradesRepositoryAttributes() {
        rewriteRun(xml("""
          <beans xmlns:flow="http://www.springframework.org/schema/webflow-config">
            <flow:executor id="flowExecutor" registry-ref="flowRegistry">
              <flow:repository type="continuation" max-continuations="12" max-conversations="5"/>
            </flow:executor>
          </beans>
          """, """
          <beans xmlns:flow="http://www.springframework.org/schema/webflow-config">
            <flow:flow-executor id="flowExecutor" flow-registry="flowRegistry">
              <flow:flow-execution-repository max-execution-snapshots="12" max-executions="5"/>
            </flow:flow-executor>
          </beans>
          """));
    }

    @Test
    void scopesChangesToTheWebFlowNamespace() {
        rewriteRun(xml("""
          <beans xmlns:swf="http://www.springframework.org/schema/webflow-config">
            <swf:registry id="flowRegistry">
              <swf:location path="/flows/*.xml"/>
            </swf:registry>
            <other:location xmlns:other="urn:unrelated" path="unchanged"/>
          </beans>
          """, """
          <beans xmlns:swf="http://www.springframework.org/schema/webflow-config">
            <swf:flow-registry id="flowRegistry" flow-builder-services="flowBuilderServices" base-path="/WEB-INF">
              <swf:flow-location-pattern value="/flows/*.xml"/>
            </swf:flow-registry>
            <other:location xmlns:other="urn:unrelated" path="unchanged"/>
          </beans>
          """));
    }
}
