package pl.demo.ice.extras;

import pl.demo.ice.service.UserOperations;
import pl.demo.ice.service.UserSummary;

import javax.faces.event.ActionEvent;
import javax.faces.event.ValueChangeEvent;
import javax.faces.model.SelectItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Session state and actions used by the component compatibility page. */
public class ComponentsBean {
    private static final List<String> SUGGESTION_OPTIONS = Arrays.asList(
            "admin", "administrator", "anna", "user");

    private UserOperations userService;
    private String coverageLogin;
    private String coveragePassword;
    private String coverageRole = "admin";
    private String[] coverageRoles = new String[0];
    private String coverageSuggestion;
    private String coverageUserFilter;
    private String coverageSortColumn = "login";
    private boolean coverageSortAscending = true;
    private boolean coveragePopupVisible;

    public String applyCoverageUserFilter() { return null; }

    public String clearCoverageUserFilter() {
        coverageUserFilter = "";
        return null;
    }

    public void updateCoverageSuggestions(ValueChangeEvent event) {
        Object value = event.getNewValue();
        coverageSuggestion = value == null ? null : value.toString();
    }

    public List<String> completeCoverageSuggestions(String query) {
        String prefix = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<String>();
        for (String option : SUGGESTION_OPTIONS) {
            if (prefix.isEmpty() || option.startsWith(prefix)) suggestions.add(option);
        }
        return suggestions;
    }

    public List<SelectItem> getCoverageSuggestions() {
        String prefix = coverageSuggestion == null ? "" : coverageSuggestion.trim().toLowerCase(Locale.ROOT);
        List<SelectItem> suggestions = new ArrayList<SelectItem>();
        for (String option : SUGGESTION_OPTIONS) {
            if (prefix.isEmpty() || option.startsWith(prefix)) suggestions.add(new SelectItem(option, option));
        }
        return suggestions;
    }

    public List<UserSummary> getCoverageUsers() {
        String filter = coverageUserFilter == null ? "" : coverageUserFilter.trim().toLowerCase(Locale.ROOT);
        List<UserSummary> results = new ArrayList<UserSummary>();
        for (UserSummary user : userService.findAll()) {
            if (filter.isEmpty() || containsIgnoreCase(user.getLogin(), filter)
                    || containsIgnoreCase(user.getImie(), filter)
                    || containsIgnoreCase(user.getNazwisko(), filter)) {
                results.add(user);
            }
        }
        final String column = coverageSortColumn == null ? "login" : coverageSortColumn;
        Collections.sort(results, new Comparator<UserSummary>() {
            @Override
            public int compare(UserSummary left, UserSummary right) {
                return sortValue(left, column).compareTo(sortValue(right, column));
            }
        });
        if (!coverageSortAscending) Collections.reverse(results);
        return results;
    }

    private static boolean containsIgnoreCase(String value, String filter) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(filter);
    }

    private static String sortValue(UserSummary user, String column) {
        String value = "imie".equals(column) ? user.getImie()
                : "nazwisko".equals(column) ? user.getNazwisko() : user.getLogin();
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public void showCoveragePopup(ActionEvent event) { coveragePopupVisible = true; }
    public void hideCoveragePopup(ActionEvent event) { coveragePopupVisible = false; }

    public void setUserService(UserOperations userService) { this.userService = userService; }
    public String getCoverageLogin() { return coverageLogin; }
    public void setCoverageLogin(String coverageLogin) { this.coverageLogin = coverageLogin; }
    public String getCoveragePassword() { return coveragePassword; }
    public void setCoveragePassword(String coveragePassword) { this.coveragePassword = coveragePassword; }
    public String getCoverageRole() { return coverageRole; }
    public void setCoverageRole(String coverageRole) { this.coverageRole = coverageRole; }
    public String[] getCoverageRoles() { return coverageRoles; }
    public void setCoverageRoles(String[] coverageRoles) { this.coverageRoles = coverageRoles; }
    public String getCoverageSuggestion() { return coverageSuggestion; }
    public void setCoverageSuggestion(String coverageSuggestion) { this.coverageSuggestion = coverageSuggestion; }
    public String getCoverageUserFilter() { return coverageUserFilter; }
    public void setCoverageUserFilter(String coverageUserFilter) { this.coverageUserFilter = coverageUserFilter; }
    public String getCoverageSortColumn() { return coverageSortColumn; }
    public void setCoverageSortColumn(String coverageSortColumn) { this.coverageSortColumn = coverageSortColumn; }
    public boolean isCoverageSortAscending() { return coverageSortAscending; }
    public void setCoverageSortAscending(boolean coverageSortAscending) {
        this.coverageSortAscending = coverageSortAscending;
    }
    public boolean isCoveragePopupVisible() { return coveragePopupVisible; }
}
