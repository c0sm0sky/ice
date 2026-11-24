package pl.demo.ice;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Weryfikuje wygenerowaną definicję przepływu Web Flow. */
class FlowMigrationDefinitionTest {
    private static final String FLOW_NS = "http://www.springframework.org/schema/webflow";
    private static final Path FLOW_DIR = Paths.get(
            "primefaces-migrated/src/main/webapp/WEB-INF/flows");
    private static final Path WEBAPP_DIR = Paths.get("primefaces-migrated/src/main/webapp");

    @Test
    void flowStartsAtLoginDecisionAndContainsExpectedStates() throws Exception {
        Element flow = readFlow("main-flow.xml");
        assertEquals("loginDecision", flow.getAttribute("start-state"));

        Set<String> states = new HashSet<>();
        NodeList children = flow.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && ((Element) child).hasAttribute("id")) {
                states.add(((Element) child).getAttributes().getNamedItem("id").getNodeValue());
            }
        }
        assertEquals(new HashSet<>(Arrays.asList(
                "login", "loginDecision", "users", "editUser", "showCoverage", "report")), states);
    }

    @Test
    void loginDecisionRoutesToUsersOrLogin() throws Exception {
        Element flow = readFlow("main-flow.xml");
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
        Element flow = readFlow("main-flow.xml");
        assertTransition(flow, "users", "editUser", "editUser");
        assertTransition(flow, "users", "openReport", "report");
        assertTransition(flow, "users", "showCoverage", "showCoverage");
        assertTransition(flow, "showCoverage", "users", "users");
        assertEquals("secondary-flow", state(flow, "report").getAttribute("subflow"));
        assertTransition(flow, "users", "logout", "login");
        assertTransition(flow, "editUser", "saveUser", "users");
        assertTransition(flow, "editUser", "saveUserError", "editUser");
        assertTransition(flow, "editUser", "cancelUserEdit", "users");
    }

    @Test
    void upgradedFlowStartsAtLoginDecisionAndContainsAllPageViews() throws Exception {
        Element flow = readFlow("main-flow.xml");
        assertEquals("loginDecision", flow.getAttribute("start-state"));
        assertTrue(Files.isRegularFile(FLOW_DIR.resolve("main-flow.xml")),
                "Migrated flow must exist in generated project");
        assertView(flow, "login", "/login.xhtml");
        assertView(flow, "users", "/strona1.xhtml");
        assertView(flow, "editUser", "/strona2.xhtml");
        assertView(flow, "showCoverage", "/extras/coverage.xhtml");
        String usersPage = Files.readString(WEBAPP_DIR.resolve("strona1.xhtml"));
        assertTrue(usersPage.indexOf("user-table") < usersPage.indexOf("user-actions"),
                "Report and component links must follow the users table");
        assertTrue(usersPage.contains("<p:commandLink id=\"coverageLink\""),
                "Component navigation must be migrated to a PrimeFaces command link");
        int componentAction = usersPage.indexOf("action=\"#{userBean.showCoverage}\"");
        int componentLinkEnd = usersPage.indexOf("/>", componentAction);
        assertTrue(componentAction >= 0 && componentLinkEnd > componentAction
                        && usersPage.substring(componentAction, componentLinkEnd).contains("ajax=\"true\""),
                "Component panel command link must be AJAX-enabled");
        assertFalse(usersPage.contains("href=\"extras/coverage.faces\""),
                "Component navigation must not leave the current Faces flow");
    }

    @Test
    void supplementalFlowsKeepReportTransitionsAndResolveTheirViews() throws Exception {
        Element secondary = readFlow("extras/secondary-flow.xml");
        assertTransition(secondary, "chooseReport", "saveNote", "saveNote");
        assertTransition(secondary, "chooseReport", "run", "prepareReport");
        assertTransition(secondary, "chooseReport", "fail", "failReport");
        assertTransition(secondary, "chooseReport", "skip", "markUnavailable");
        assertTransition(secondary, "showReport", "finished", "finish");
        assertTransition(secondary, "reportError", "finish", "finish");
        assertEquals("detail-flow", state(secondary, "showReport").getAttribute("subflow"));

        Element detail = readFlow("extras/detail-flow.xml");
        assertEquals("detail", detail.getAttribute("start-state"));
        assertTransition(detail, "detail", "finish", "finished");

        for (String flowName : Arrays.asList("main-flow.xml", "extras/secondary-flow.xml", "extras/detail-flow.xml")) {
            NodeList views = readFlow(flowName).getElementsByTagNameNS(FLOW_NS, "view-state");
            for (int i = 0; i < views.getLength(); i++) {
                String view = ((Element) views.item(i)).getAttribute("view");
                assertTrue(view.startsWith("/"), flowName + " view must be rooted: " + view);
                assertTrue(Files.exists(WEBAPP_DIR.resolve(view.substring(1))),
                        flowName + " refers to missing view " + view);
            }
        }
        for (String page : Arrays.asList(
                "extras/coverage.xhtml", "extras/report.xhtml",
                "extras/report-detail.xhtml", "extras/report-error.xhtml")) {
            assertTrue(Files.exists(WEBAPP_DIR.resolve(page)), "Missing supplemental page " + page);
        }
    }

    @Test
    void migratedSortableTableKeepsAStableBeanList() throws Exception {
        String bean = Files.readString(Paths.get("primefaces-migrated/src/main/java/pl/demo/ice/extras/ComponentsBean.java"));
        assertTrue(bean.contains("coverageUsersCache"), "sorting migration must add a stable list cache");
        String userBean = Files.readString(Paths.get("primefaces-migrated/src/main/java/pl/demo/ice/UserBean.java"));
        assertFalse(userBean.contains("coverageLogin"), "component state must live outside UserBean");
        assertFalse(userBean.contains("reportNoteDraft"), "report state must live outside UserBean");
    }

    @Test
    void supplementalPagesAndBeanReceiveTheirRequiredPrimeFacesMigrations() throws Exception {
        String coverage = Files.readString(WEBAPP_DIR.resolve("extras/coverage.xhtml"));
        String template = Files.readString(WEBAPP_DIR.resolve("WEB-INF/layouts/mainTemplate.xhtml"));
        assertTrue(coverage.contains("<p:autoComplete"));
        assertTrue(coverage.contains("secure=\"false\""));
        assertTrue(coverage.contains("completeMethod=\"#{componentsBean.completeCoverageSuggestions}\""));
        assertTrue(coverage.contains("<p:splitter"));
        assertFalse(coverage.contains("rime.css"));
        assertFalse(template.contains("rime.css"), "the ICEfaces skin must not remain in the PrimeFaces layout");
        assertFalse(coverage.contains("menuPopup="));

        String report = Files.readString(WEBAPP_DIR.resolve("extras/report-detail.xhtml"));
        assertTrue(report.contains("<p:fileDownload value=\"#{reportBean.reportResource}\""));
        assertFalse(report.contains("<ice:outputResource"));

        String reportBean = Files.readString(Paths.get("primefaces-migrated/src/main/java/pl/demo/ice/extras/ReportBean.java"));
        assertTrue(reportBean.contains("StreamedContent"));
        assertFalse(reportBean.contains("import javax.faces."));
        String componentsBean = Files.readString(Paths.get("primefaces-migrated/src/main/java/pl/demo/ice/extras/ComponentsBean.java"));
        assertTrue(componentsBean.contains("completeCoverageSuggestions"));
        assertFalse(componentsBean.contains("import javax.faces."));

        String webXml = Files.readString(WEBAPP_DIR.resolve("WEB-INF/web.xml"));
        assertFalse(webXml.contains("File Upload Servlet"));
        assertTrue(webXml.contains("primefaces.THEME"));
    }

    private static Element readFlow(String name) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new File(FLOW_DIR.resolve(name).toString()));
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
