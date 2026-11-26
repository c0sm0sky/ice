package pl.demo.ice.extras;

import com.icesoft.faces.context.Resource;
import com.icesoft.faces.context.StringResource;
import org.springframework.webflow.action.MultiAction;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;
import pl.demo.ice.db.Report;
import pl.demo.ice.db.ReportDao;
import pl.demo.ice.service.UserOperations;
import pl.demo.ice.service.UserSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.faces.model.SelectItem;

/** Report data shared by the report views and report flow actions. */
public class ReportBean extends MultiAction {
    private boolean available = true;
    private String reportNoteDraft;
    private String savedReportNote;
    private List<UserSummary> reportUsers = Collections.emptyList();
    private List<UserSummary> reportPreviewRows = Collections.emptyList();
    private Resource reportResource = new StringResource("", "UTF-8");
    private int reportRecordCount;
    private String reportSummary;
    private UserOperations userService;
    private ReportDao reportDao;
    private List<Report> availableReports = Collections.emptyList();
    private boolean reportsLoaded;
    private String selectedReportOwnerId;
    private String selectedReportId;

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public Event initialize(RequestContext context) {
        available = true;
        reportNoteDraft = null;
        loadAvailableReports();
        if (findOwner(selectedReportOwnerId) == null && !availableReports.isEmpty()) {
            setSelectedReportOwnerId(String.valueOf(availableReports.get(0).getOwner().getId()));
        } else if (getSelectedReport() == null) {
            selectFirstReportForOwner();
        }
        return success();
    }

    public Event prepare(RequestContext context) { return success(); }

    public Event loadMetadata(RequestContext context) {
        if (available) {
            List<UserSummary> users = userService == null
                    ? Collections.<UserSummary>emptyList() : userService.findAll();
            prepareUserReport(users, getSelectedReport());
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

    private void prepareUserReport(List<UserSummary> users, Report report) {
        String code = report == null ? "Profil użytkownika nr 0" : report.getCode();
        String owner = report == null || report.getOwner() == null ? "" : report.getOwner().getLogin();
        String note = savedReportNote == null ? "" : savedReportNote;
        StringBuilder csv;
        if (code.startsWith("Raporty użytkownika nr ") && report != null && report.getOwner() != null) {
            List<Report> assignedReports = reportDao.findForOwner(report.getOwner().getId());
            reportUsers = new ArrayList<UserSummary>();
            csv = csvWithNoteMetadata(note, "Właściciel,Raport,Opis");
            for (Report assigned : assignedReports) {
                UserSummary row = new UserSummary(owner, assigned.getTitle(), assigned.getDescription());
                reportUsers.add(row);
                csv.append(csvField(row.getLogin())).append(',').append(csvField(row.getImie())).append(',')
                        .append(csvField(row.getNazwisko())).append("\r\n");
            }
            reportSummary = "Raporty przypisane użytkownikowi: " + owner;
        } else {
            reportUsers = report == null || report.getOwner() == null
                    ? new ArrayList<UserSummary>(users)
                    : Collections.singletonList(new UserSummary(owner, report.getOwner().getImie(),
                    report.getOwner().getNazwisko()));
            UserSummary profile = reportUsers.isEmpty() ? null : reportUsers.get(0);
            csv = csvWithNoteMetadata(note, "Login,Imię,Nazwisko");
            if (profile != null) {
                csv.append(csvField(profile.getLogin())).append(',').append(csvField(profile.getImie())).append(',')
                        .append(csvField(profile.getNazwisko())).append("\r\n");
            }
            reportRecordCount = reportUsers.size();
            reportSummary = "Profil użytkownika: " + owner;
        }
        reportRecordCount = reportUsers.size();
        reportPreviewRows = Collections.emptyList();
        reportResource = new StringResource(csv.toString(), "UTF-8");
    }

    private String csvField(String value) {
        String escaped = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private StringBuilder csvWithNoteMetadata(String note, String tableHeaders) {
        return new StringBuilder("\uFEFFNotatka,").append(csvField(note)).append("\r\n\r\n")
                .append(tableHeaders).append("\r\n");
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
    public void setReportDao(ReportDao reportDao) { this.reportDao = reportDao; }
    public List<SelectItem> getAvailableReports() {
        loadAvailableReports();
        List<SelectItem> items = new ArrayList<SelectItem>();
        for (Report report : reportsForSelectedOwner()) items.add(new SelectItem(String.valueOf(report.getId()), report.getTitle()));
        return items;
    }
    public List<SelectItem> getAvailableReportOwners() {
        loadAvailableReports();
        Map<Long, pl.demo.ice.db.User> owners = new LinkedHashMap<Long, pl.demo.ice.db.User>();
        for (Report report : availableReports) owners.put(report.getOwner().getId(), report.getOwner());
        List<SelectItem> items = new ArrayList<SelectItem>();
        for (pl.demo.ice.db.User owner : owners.values())
            items.add(new SelectItem(String.valueOf(owner.getId()), owner.getLogin() + " — " + owner.getImie() + " " + owner.getNazwisko()));
        return items;
    }
    public String getSelectedReportOwnerId() { return selectedReportOwnerId; }
    public void setSelectedReportOwnerId(String selectedReportOwnerId) {
        this.selectedReportOwnerId = selectedReportOwnerId;
        selectFirstReportForOwner();
    }
    public String getSelectedReportId() { return selectedReportId; }
    public void setSelectedReportId(String selectedReportId) { this.selectedReportId = selectedReportId; }
    public List<Report> getReportDefinitions() {
        loadAvailableReports();
        return availableReports;
    }
    private List<Report> reportsForSelectedOwner() {
        List<Report> result = new ArrayList<Report>();
        for (Report report : availableReports)
            if (String.valueOf(report.getOwner().getId()).equals(selectedReportOwnerId)) result.add(report);
        return result;
    }
    private pl.demo.ice.db.User findOwner(String id) {
        if (id == null) return null;
        for (Report report : availableReports)
            if (String.valueOf(report.getOwner().getId()).equals(id)) return report.getOwner();
        return null;
    }
    private void selectFirstReportForOwner() {
        List<Report> reports = reportsForSelectedOwner();
        selectedReportId = reports.isEmpty() ? null : String.valueOf(reports.get(0).getId());
    }
    private void loadAvailableReports() {
        if (!reportsLoaded) {
            availableReports = reportDao == null ? Collections.<Report>emptyList() : reportDao.findAll();
            reportsLoaded = true;
        }
    }
    public Report getSelectedReport() {
        loadAvailableReports();
        for (Report report : reportsForSelectedOwner())
            if (String.valueOf(report.getId()).equals(selectedReportId)) return report;
        return null;
    }
    public String getSelectedReportTitle() {
        Report report = getSelectedReport();
        return report == null ? "Raport użytkowników" : report.getTitle();
    }
    public String getSelectedReportOwner() {
        loadAvailableReports();
        pl.demo.ice.db.User owner = findOwner(selectedReportOwnerId);
        return owner == null ? "" : owner.getLogin();
    }
    public List<UserSummary> getReportUsers() { return reportUsers; }
    public List<UserSummary> getReportPreviewRows() { return reportPreviewRows; }
    public int getReportUserCount() { return reportUsers.size(); }
    public int getReportRecordCount() { return reportRecordCount; }
    public String getReportSummary() { return reportSummary; }
    public boolean isProfileReport() {
        Report report = getSelectedReport();
        return report != null && report.getCode().startsWith("Profil użytkownika nr ");
    }
    public boolean isAssignedReportsReport() {
        Report report = getSelectedReport();
        return report != null && report.getCode().startsWith("Raporty użytkownika nr ");
    }
    public boolean isUserDirectoryReport() { return isProfileReport(); }
    public boolean isUserSummaryReport() { return false; }
    public boolean isAccountAuditReport() { return isAssignedReportsReport(); }
    public Resource getReportResource() { return reportResource; }
}
