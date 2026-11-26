package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class MigrateWebFlowExceptionPackageTest implements RewriteTest {
    @Test
    void updatesExceptionClassInFlowTransition() {
        rewriteRun(spec -> spec.recipe(new UpgradeMainFlowWithWebFlowUpgrader()), xml(
                """
                <flow xmlns="http://www.springframework.org/schema/webflow" start-state="view">
                  <transition on-exception="org.springframework.webflow.engine.ActionExecutionException" to="error"/>
                </flow>
                """,
                """
                <flow xmlns="http://www.springframework.org/schema/webflow" start-state="view">
                  <transition on-exception="org.springframework.webflow.execution.ActionExecutionException" to="error"/>
                </flow>
                """
        ));
    }
}
