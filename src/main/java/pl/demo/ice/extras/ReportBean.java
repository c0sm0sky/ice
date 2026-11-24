package pl.demo.ice.extras;

import com.icesoft.faces.context.Resource;
import com.icesoft.faces.context.StringResource;
import org.springframework.webflow.action.MultiAction;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;
import pl.demo.ice.service.UserOperations;
import pl.demo.ice.service.UserSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Report data shared by the report views and report flow actions. */
public class ReportBean extends MultiAction {
    private boolean available = true;
    private String reportNoteDraft;
    private String savedReportNote;
    private List<UserSummary> reportUsers = Collections.emptyList();
    private Resource reportResource = new StringResource("", "UTF-8");
    private UserOperations userService;

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public Event initialize(RequestContext context) {
        available = true;
        reportNoteDraft = null;
        return success();
    }

    public Event prepare(RequestContext context) { return success(); }

    public Event loadMetadata(RequestContext context) {
        if (available) {
            List<UserSummary> users = userService == null
                    ? Collections.<UserSummary>emptyList() : userService.findAll();
            prepareUserReport(users);
        }
        return success();
    }

    public Event selectReport(RequestContext context) {
        return new Event(this, available ? "available" : "unavailable");
    }

    public Event markUnavailable(RequestContext context) {
        available = false;
        return success();
    }

    public Event saveNote(RequestContext context) {
        saveReportNote(reportNoteDraft);
        return success();
    }

    public Event fail(RequestContext context) {
        throw new IllegalStateException("Report failed deliberately.");
    }

    public void releaseResources() { }

    private void prepareUserReport(List<UserSummary> users) {
        reportUsers = new ArrayList<UserSummary>(users);
        StringBuilder csv = new StringBuilder("\uFEFFLogin,Imię,Nazwisko\r\n");
        for (UserSummary user : reportUsers) {
            csv.append(csvField(user.getLogin())).append(',')
                    .append(csvField(user.getImie())).append(',')
                    .append(csvField(user.getNazwisko())).append("\r\n");
        }
        reportResource = new StringResource(csv.toString(), "UTF-8");
    }

    private String csvField(String value) {
        String escaped = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    public void saveReportNote(String note) {
        savedReportNote = note == null ? "" : note.trim();
        reportNoteDraft = null;
    }

    public String getReportNoteDraft() { return reportNoteDraft; }
    public void setReportNoteDraft(String reportNoteDraft) { this.reportNoteDraft = reportNoteDraft; }
    public String getSavedReportNote() { return savedReportNote; }
    public void setSavedReportNote(String savedReportNote) { this.savedReportNote = savedReportNote; }
    public void setUserService(UserOperations userService) { this.userService = userService; }
    public List<UserSummary> getReportUsers() { return reportUsers; }
    public int getReportUserCount() { return reportUsers.size(); }
    public Resource getReportResource() { return reportResource; }
}
