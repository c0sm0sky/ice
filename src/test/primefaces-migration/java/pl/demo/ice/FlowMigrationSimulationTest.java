package pl.demo.ice;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import pl.demo.ice.db.User;
import pl.demo.ice.service.UserOperations;
import pl.demo.ice.service.UserSummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowMigrationSimulationTest {
    private static final String FLOW_NS = "http://www.springframework.org/schema/webflow";

    @Test
    void flowContainsEveryPageAndActionTransition() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(
                new File("src/main/webapp/WEB-INF/flows/main-flow.xml"));
        Element flow = document.getDocumentElement();

        assertEquals("entry", flow.getAttribute("start-state"));
        Set<String> stateIds = new HashSet<String>();
        NodeList children = flow.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && ((Element) child).hasAttribute("id")) {
                stateIds.add(((Element) child).getAttribute("id"));
            }
        }
        assertEquals(new HashSet<String>(Arrays.asList(
                "entry", "pageDecision", "login", "loginDecision", "users", "editUser")), stateIds);

        assertDecision(flow, "entry", "userBean.currentPageView == '/strona2.xhtml'", "editUser", "pageDecision");
        assertDecision(flow, "pageDecision", "userBean.currentPageView == '/strona1.xhtml'", "users", "loginDecision");
        assertDecision(flow, "loginDecision", "userBean.loggedIn", "users", "login");
        assertTransition(flow, "login", "login", "loginDecision");
        assertTransition(flow, "users", "editUser", "editUser");
        assertTransition(flow, "users", "logout", "login");
        assertTransition(flow, "editUser", "saveUser", "users");
        assertTransition(flow, "editUser", "saveUserError", "editUser");
        assertTransition(flow, "editUser", "cancelUserEdit", "users");
    }

    @Test
    void loginAndLogoutEventsMatchFlow() {
        FakeUserOperations service = new FakeUserOperations();
        UserBean bean = new UserBean();
        bean.setUserService(service);

        bean.setLogin("wrong");
        bean.setHaslo("wrong");
        assertEquals("login", bean.login());
        assertFalse(bean.isLoggedIn());
        assertNotNull(bean.getMessage());
        assertNull(bean.getHaslo());

        bean.setLogin("admin");
        bean.setHaslo("admin");
        assertEquals("login", bean.login());
        assertTrue(bean.isLoggedIn());
        assertEquals("admin", bean.getLoggedInName());

        assertEquals("logout", bean.logout());
        assertFalse(bean.isLoggedIn());
        assertNull(bean.getLoggedInName());
    }

    @Test
    void invalidPasswordReturnsSaveErrorAndCancelReturnsToUsers() throws Exception {
        FakeUserOperations service = new FakeUserOperations();
        UserBean bean = new UserBean();
        bean.setUserService(service);
        setEditLogin(bean, "admin");
        bean.setEditUserPassword(" ");

        assertEquals("saveUserError", bean.saveUser());
        assertEquals("Podaj hasło.", bean.getMessage());
        assertEquals("cancelUserEdit", bean.cancelUserEdit());
        assertNull(bean.getEditUserPassword());
        assertNull(bean.getMessage());
    }

    @Test
    void validPasswordSaveReturnsToUsers() throws Exception {
        FakeUserOperations service = new FakeUserOperations();
        UserBean bean = new UserBean();
        bean.setUserService(service);
        setEditLogin(bean, "admin");
        bean.setEditUserPassword("admin");

        assertEquals("saveUser", bean.saveUser());
        assertEquals("admin", service.updatedLogin);
        assertEquals("admin", service.updatedPassword);
        assertEquals("Zapisano użytkownika.", bean.getMessage());
        assertNull(bean.getEditUserPassword());
    }

    private static void setEditLogin(UserBean bean, String login) throws Exception {
        Field field = UserBean.class.getDeclaredField("editUserOriginalLogin");
        field.setAccessible(true);
        field.set(bean, login);
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

    private static void assertDecision(Element flow, String stateId, String test, String thenState, String elseState) {
        NodeList conditions = state(flow, stateId).getElementsByTagNameNS(FLOW_NS, "if");
        assertEquals(1, conditions.getLength());
        Element condition = (Element) conditions.item(0);
        assertEquals(test, condition.getAttribute("test"));
        assertEquals(thenState, condition.getAttribute("then"));
        assertEquals(elseState, condition.getAttribute("else"));
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

    private static final class FakeUserOperations implements UserOperations {
        private String updatedLogin;
        private String updatedPassword;

        @Override
        public User authenticate(String login, String password) {
            if (!"admin".equals(login) || !"admin".equals(password)) return null;
            User user = new User();
            user.setLogin("admin");
            return user;
        }

        @Override
        public java.util.List<UserSummary> findAll() {
            return java.util.Collections.emptyList();
        }

        @Override
        public void updatePassword(String login, String password) {
            if (password == null || password.trim().isEmpty()) {
                throw new IllegalArgumentException("Podaj hasło.");
            }
            updatedLogin = login;
            updatedPassword = password.trim();
        }
    }
}