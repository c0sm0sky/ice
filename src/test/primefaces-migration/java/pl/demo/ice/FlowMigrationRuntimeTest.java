package pl.demo.ice;

import org.springframework.webflow.config.FlowDefinitionResource;
import org.springframework.webflow.config.FlowDefinitionResourceFactory;
import org.springframework.webflow.test.MockExternalContext;
import org.springframework.webflow.test.MockFlowBuilderContext;
import org.springframework.webflow.test.execution.AbstractXmlFlowExecutionTests;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import pl.demo.ice.db.User;
import pl.demo.ice.service.UserOperations;
import pl.demo.ice.service.UserSummary;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Executes the migrated flow definition through Spring Web Flow, without starting a server. */
public class FlowMigrationRuntimeTest extends AbstractXmlFlowExecutionTests {
    private static final String FLOW_NS = "http://www.springframework.org/schema/webflow";
    private final UserBean userBean = new UserBean();

    @Override
    protected FlowDefinitionResource getResource(FlowDefinitionResourceFactory factory) {
        return factory.createFileResource("src/main/webapp/WEB-INF/flows/main-flow.xml");
    }

    @Override
    protected void configureFlowBuilderContext(MockFlowBuilderContext builderContext) {
        builderContext.registerBean("userBean", userBean);
    }

    public void testFlowContainsEveryPageAndActionTransition() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(
                new File("src/main/webapp/WEB-INF/flows/main-flow.xml"));
        Element flow = document.getDocumentElement();

        assertEquals("loginDecision", flow.getAttribute("start-state"));
        Set<String> stateIds = new HashSet<String>();
        NodeList children = flow.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && ((Element) child).hasAttribute("id")) {
                stateIds.add(((Element) child).getAttribute("id"));
            }
        }
        assertEquals(new HashSet<String>(Arrays.asList(
                "login", "loginDecision", "users", "editUser")), stateIds);

        assertDecision(flow, "loginDecision", "userBean.loggedIn", "users", "login");
        assertTransition(flow, "login", "login", "loginDecision");
        assertTransition(flow, "users", "editUser", "editUser");
        assertTransition(flow, "users", "logout", "login");
        assertTransition(flow, "editUser", "saveUser", "users");
        assertTransition(flow, "editUser", "saveUserError", "editUser");
        assertTransition(flow, "editUser", "cancelUserEdit", "users");
    }

    public void testLoginAndLogoutEventsMatchFlow() throws Exception {
        MockExternalContext context = new MockExternalContext();
        startFlow(context);
        assertCurrentStateEquals("login");
        userBean.setUserService(new FakeUserOperations());

        userBean.setLogin("wrong");
        userBean.setHaslo("wrong");
        assertEquals("login", userBean.login());
        assertFalse(userBean.isLoggedIn());
        assertNotNull(userBean.getMessage());
        assertNull(userBean.getHaslo());
        context = event(context, "login");
        assertCurrentStateEquals("login");

        userBean.setLogin("admin");
        userBean.setHaslo("admin");
        assertEquals("login", userBean.login());
        assertTrue(userBean.isLoggedIn());
        assertEquals("admin", userBean.getLoggedInName());
        context = event(context, "login");
        assertCurrentStateEquals("users");

        assertEquals("logout", userBean.logout());
        assertFalse(userBean.isLoggedIn());
        assertNull(userBean.getLoggedInName());
        event(context, "logout");
        assertCurrentStateEquals("login");
    }

    public void testInvalidPasswordReturnsSaveErrorAndCancelReturnsToUsers() throws Exception {
        FakeUserOperations service = new FakeUserOperations();
        MockExternalContext context = startLoggedInFlow(service);
        context = event(context, "editUser");
        assertCurrentStateEquals("editUser");
        setEditLogin(userBean, "admin");
        userBean.setEditUserPassword(" ");

        assertEquals("saveUserError", userBean.saveUser());
        assertEquals("Podaj hasło.", userBean.getMessage());
        context = event(context, "saveUserError");
        assertCurrentStateEquals("editUser");

        assertEquals("cancelUserEdit", userBean.cancelUserEdit());
        assertNull(userBean.getEditUserPassword());
        assertNull(userBean.getMessage());
        event(context, "cancelUserEdit");
        assertCurrentStateEquals("users");
    }

    public void testValidPasswordSaveReturnsToUsers() throws Exception {
        FakeUserOperations service = new FakeUserOperations();
        MockExternalContext context = startLoggedInFlow(service);
        context = event(context, "editUser");
        assertCurrentStateEquals("editUser");
        setEditLogin(userBean, "admin");
        userBean.setEditUserPassword("admin");

        assertEquals("saveUser", userBean.saveUser());
        assertEquals("admin", service.updatedLogin);
        assertEquals("admin", service.updatedPassword);
        assertEquals("Zapisano użytkownika.", userBean.getMessage());
        assertNull(userBean.getEditUserPassword());
        event(context, "saveUser");
        assertCurrentStateEquals("users");
    }

    public void testLoginAndAllUserPageTransitions() throws Exception {
        MockExternalContext context = new MockExternalContext();
        startFlow(context);
        assertCurrentStateEquals("login");

        userBean.setUserService(new FakeUserOperations());
        userBean.setLogin("admin");
        userBean.setHaslo("wrong");
        assertEquals("login", userBean.login());
        context = event(context, "login");
        assertCurrentStateEquals("login");

        userBean.setLogin("admin");
        userBean.setHaslo("admin");
        assertEquals("login", userBean.login());
        context = event(context, "login");
        assertCurrentStateEquals("users");

        context = event(context, "editUser");
        assertCurrentStateEquals("editUser");
        setEditLogin(userBean, "admin");
        userBean.setEditUserPassword(" ");
        assertEquals("saveUserError", userBean.saveUser());
        context = event(context, "saveUserError");
        assertCurrentStateEquals("editUser");
        assertEquals("cancelUserEdit", userBean.cancelUserEdit());
        context = event(context, "cancelUserEdit");
        assertCurrentStateEquals("users");
        context = event(context, "editUser");
        assertCurrentStateEquals("editUser");
        setEditLogin(userBean, "admin");
        userBean.setEditUserPassword("admin");
        assertEquals("saveUser", userBean.saveUser());
        context = event(context, "saveUser");
        assertCurrentStateEquals("users");
        assertEquals("logout", userBean.logout());
        context = event(context, "logout");
        assertCurrentStateEquals("login");
    }

    private MockExternalContext startLoggedInFlow(FakeUserOperations service) throws Exception {
        MockExternalContext context = new MockExternalContext();
        startFlow(context);
        assertCurrentStateEquals("login");
        userBean.setUserService(service);
        userBean.setLogin("admin");
        userBean.setHaslo("admin");
        assertEquals("login", userBean.login());
        context = event(context, "login");
        assertCurrentStateEquals("users");
        return context;
    }

    private MockExternalContext event(MockExternalContext context, String eventId) throws Exception {
        context.setEventId(eventId);
        resumeFlow(context);
        return new MockExternalContext();
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

    private void assertDecision(Element flow, String stateId, String test, String thenState, String elseState) {
        NodeList conditions = state(flow, stateId).getElementsByTagNameNS(FLOW_NS, "if");
        assertEquals(1, conditions.getLength());
        Element condition = (Element) conditions.item(0);
        assertEquals(test, condition.getAttribute("test"));
        assertEquals(thenState, condition.getAttribute("then"));
        assertEquals(elseState, condition.getAttribute("else"));
    }

    private void assertTransition(Element flow, String stateId, String event, String target) {
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
        public List<UserSummary> findAll() {
            return Collections.emptyList();
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
