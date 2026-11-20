package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class MigrateWildFlyDeploymentStructureTest implements RewriteTest {
    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        MigrateWildFlyDeploymentStructure recipe = new MigrateWildFlyDeploymentStructure();
        recipe.filePattern = "**/jboss-deployment-structure.xml";
        spec.recipe(recipe);
    }

    @Test
    void removesConfiguredLegacyExclusionsAndPreservesOtherModuleAndSubsystemEntries() {
        rewriteRun(xml("""
          <jboss-deployment-structure xmlns="urn:jboss:deployment-structure:1.2">
            <deployment>
              <exclude-subsystems>
                <subsystem name="jsf"/>
                <subsystem name="ejb"/>
              </exclude-subsystems>
              <exclusions>
                <module name="javax.faces.api"/>
                <module name="javax.persistence.api"/>
                <module name="org.jboss.as.jsf"/>
                <module name="com.sun.jsf-impl"/>
                <module name="org.hibernate"/>
                <module name="org.example.keep"/>
              </exclusions>
            </deployment>
          </jboss-deployment-structure>
          """, """
          <jboss-deployment-structure xmlns="urn:jboss:deployment-structure:1.2">
            <deployment>
              <exclude-subsystems>
                <subsystem name="ejb"/>
              </exclude-subsystems>
              <exclusions>
                <module name="org.hibernate"/>
                <module name="org.example.keep"/>
              </exclusions>
            </deployment>
          </jboss-deployment-structure>
          """, spec -> spec.path("src/main/webapp/WEB-INF/jboss-deployment-structure.xml")));
    }

    @Test
    void removesEmptySubsystemExclusionContainer() {
        rewriteRun(xml("""
          <jboss-deployment-structure xmlns="urn:jboss:deployment-structure:1.2">
            <deployment>
              <exclude-subsystems><subsystem name="jsf"/></exclude-subsystems>
            </deployment>
          </jboss-deployment-structure>
          """, """
          <jboss-deployment-structure xmlns="urn:jboss:deployment-structure:1.2">
            <deployment>
            </deployment>
          </jboss-deployment-structure>
          """, spec -> spec.path("src/main/webapp/WEB-INF/jboss-deployment-structure.xml")));
    }

    @Test
    void supportsPerProjectModuleAndSubsystemNames() {
        MigrateWildFlyDeploymentStructure recipe = new MigrateWildFlyDeploymentStructure();
        recipe.filePattern = "**/*.xml";
        recipe.modulesToUnexclude = "old.module";
        recipe.subsystemsToUnexclude = "oldSubsystem";
        rewriteRun(spec -> spec.recipe(recipe), xml("""
          <jboss-deployment-structure xmlns="urn:jboss:deployment-structure:1.2">
            <deployment>
              <exclude-subsystems>
                <subsystem name="oldSubsystem"/>
                <subsystem name="keepSubsystem"/>
              </exclude-subsystems>
              <exclusions>
                <module name="old.module"/>
                <module name="keep.module"/>
              </exclusions>
            </deployment>
          </jboss-deployment-structure>
          """, """
          <jboss-deployment-structure xmlns="urn:jboss:deployment-structure:1.2">
            <deployment>
              <exclude-subsystems>
                <subsystem name="keepSubsystem"/>
              </exclude-subsystems>
              <exclusions>
                <module name="keep.module"/>
              </exclusions>
            </deployment>
          </jboss-deployment-structure>
          """, spec -> spec.path("somewhere/jboss-deployment-structure.xml")));
    }
}
