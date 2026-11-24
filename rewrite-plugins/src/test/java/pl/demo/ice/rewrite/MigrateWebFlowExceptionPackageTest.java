package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class MigrateWebFlowExceptionPackageTest implements RewriteTest {
    @Test
    void updatesExceptionClassInFlowTransition() {
        rewriteRun(spec -> spec.recipe(new MigrateWebFlowExceptionPackage()), xml(
                """
                <flow xmlns="http://www.springframework.org/schema/webflow">
                  <transition on-exception="org.springframework.webflow.engine.ActionExecutionException" to="error"/>
                </flow>
                """,
                """
                <flow xmlns="http://www.springframework.org/schema/webflow">
                  <transition on-exception="org.springframework.webflow.execution.ActionExecutionException" to="error"/>
                </flow>
                """
        ));
    }
}
