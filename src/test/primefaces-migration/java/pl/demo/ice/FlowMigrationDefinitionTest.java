package pl.demo.ice;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Weryfikuje wygenerowaną definicję przepływu Web Flow. */
class FlowMigrationDefinitionTest {
    private static final String FLOW_NS = "http://www.springframework.org/schema/webflow";
    private static final File MIGRATED_FLOW = new File(
            "primefaces-migrated/src/main/webapp/WEB-INF/flows/main-flow.xml");

    @Test
    void flowStartsAtLoginDecisionAndContainsExpectedStates() throws Exception {
        Element flow = readFlow();
        assertEquals("loginDecision", flow.getAttribute("start-state"));

        Set<String> states = new HashSet<>();
        NodeList children = flow.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && ((Element) child).hasAttribute("id")) {
                states.add(((Element) child).getAttributes().getNamedItem("id").getNodeValue());
            }
        }
        assertEquals(new HashSet<>(Arrays.asList("login", "loginDecision", "users", "editUser")), states);
    }

    @Test
    void loginDecisionRoutesToUsersOrLogin() throws Exception {
        Element flow = readFlow();
        Element decision = state(flow, "loginDecision");
        NodeList conditions = decision.getElementsByTagNameNS(FLOW_NS, "if");
        assertEquals(1, conditions.getLength());
        Element condition = (Element) conditions.item(0);
        assertEquals("userBean.loggedIn", condition.getAttribute("test"));
        assertEquals("users", condition.getAttribute("then"));
        assertEquals("login", condition.getAttribute("else"));
        assertTransition(flow, "login", "login", "loginDecision");
    }

    @Test
    void userAndEditStatesContainAllActionTransitions() throws Exception {
        Element flow = readFlow();
        assertTransition(flow, "users", "editUser", "editUser");
        assertTransition(flow, "users", "logout", "login");
        assertTransition(flow, "editUser", "saveUser", "users");
        assertTransition(flow, "editUser", "saveUserError", "editUser");
        assertTransition(flow, "editUser", "cancelUserEdit", "users");
    }

    @Test
    void upgradedFlowStartsAtLoginDecisionAndContainsAllPageViews() throws Exception {
        Element flow = readFlow();
        assertEquals("loginDecision", flow.getAttribute("start-state"));
        assertTrue(MIGRATED_FLOW.isFile(), "Migrated flow must exist in generated project");
        assertView(flow, "login", "/login.xhtml");
        assertView(flow, "users", "/strona1.xhtml");
        assertView(flow, "editUser", "/strona2.xhtml");
    }

    private static Element readFlow() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(MIGRATED_FLOW);
        return document.getDocumentElement();
    }

    private static Element state(Element flow, String id) {
        NodeList children = flow.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && id.equals(((Element) child).getAttribute("id"))) {
                return (Element) child;
            }
        }
        throw new AssertionError("Missing flow state: " + id);
    }

    private static void assertTransition(Element flow, String stateId, String event, String target) {
        NodeList transitions = state(flow, stateId).getElementsByTagNameNS(FLOW_NS, "transition");
        for (int i = 0; i < transitions.getLength(); i++) {
            Element transition = (Element) transitions.item(i);
            if (event.equals(transition.getAttribute("on")) && target.equals(transition.getAttribute("to"))) {
                return;
            }
        }
        throw new AssertionError("Missing transition " + stateId + " --" + event + "--> " + target);
    }

    private static void assertView(Element flow, String stateId, String view) {
        assertEquals(view, state(flow, stateId).getAttribute("view"));
    }
}
