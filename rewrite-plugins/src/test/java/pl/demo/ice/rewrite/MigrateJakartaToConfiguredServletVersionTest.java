package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.xml.AddTagAttribute;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.ChangeTagName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MigrateJakartaToConfiguredServletVersionTest {
    @Test
    void composesOfficialJakartaMigrationBeforeDescriptorAlignment() {
        MigrateJakartaToConfiguredServletVersion recipe = new MigrateJakartaToConfiguredServletVersion();
        recipe.servletVersion = "6.1.0";

        var steps = recipe.getRecipeList();
        assertEquals("org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta", steps.get(0).getName());
        assertEquals(10, steps.size());
        assertInstanceOf(AddTagAttribute.class, steps.get(1));
        assertInstanceOf(ChangeTagAttribute.class, steps.get(3));
        ChangeTagAttribute faceletsNamespace = (ChangeTagAttribute) steps.get(3);
        assertEquals("http://java.sun.com/jsf/facelets", faceletsNamespace.getOldValue());
        assertEquals("jakarta.faces.facelets", faceletsNamespace.getNewValue());
        assertInstanceOf(ChangeTagName.class, steps.get(7));
        assertInstanceOf(ChangeTagName.class, steps.get(8));
        assertInstanceOf(UpgradeWebXmlToServletVersion.class, steps.get(9));
        assertEquals("6.1.0", ((UpgradeWebXmlToServletVersion) steps.get(9)).servletVersion);
    }
}
