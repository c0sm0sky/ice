package pl.demo.ice;

import pl.demo.ice.db.User;
import pl.demo.ice.service.UserOperations;
import pl.demo.ice.service.UserSummary;

import javax.faces.context.FacesContext;
import java.util.List;
import java.util.Map;

public class UserBean {
    private static final String CURRENT_PAGE_SESSION_KEY = "ice.currentPage";

    private UserOperations userService;
    private String login;
    private String haslo;
    private String message;
    private boolean loggedIn;
    private String loggedInName;
    private String editUserOriginalLogin;
    private String editUserPassword;

    public void setUserService(UserOperations userService) { this.userService = userService; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getHaslo() { return haslo; }
    public void setHaslo(String haslo) { this.haslo = haslo; }
    public String getMessage() { return message; }
    public boolean isLoggedIn() { return loggedIn; }
    public String getLoggedInName() { return loggedInName; }
    public String getCurrentPageView() {
        String page = "login";
        FacesContext context = FacesContext.getCurrentInstance();
        if (context != null) {
            Object savedPage = context.getExternalContext().getSessionMap().get(CURRENT_PAGE_SESSION_KEY);
            if (savedPage instanceof String) page = (String) savedPage;
        }
        if ("strona1".equals(page)) return "/strona1.xhtml";
        if ("strona2".equals(page)) return "/strona2.xhtml";
        return "/login.xhtml";
    }
    public List<UserSummary> getUsers() { return userService.findAll(); }
    public String getEditUserPassword() { return editUserPassword; }
    public void setEditUserPassword(String editUserPassword) { this.editUserPassword = editUserPassword; }

    public String login() {
        User user = userService.authenticate(login, haslo);
        loggedIn = user != null;
        loggedInName = loggedIn ? user.getLogin() : null;
        haslo = null;
        message = loggedIn ? null : "Nieprawidłowy login lub hasło.";
        setCurrentPage(loggedIn ? "strona1" : "login");
        return "login";
    }

    public String logout() {
        loggedIn = false;
        loggedInName = null;
        login = null;
        haslo = null;
        message = null;
        setCurrentPage("login");
        return "logout";
    }

    public String editUser() {
        editUserOriginalLogin = parameterValue("userLogin");
        editUserPassword = null;
        message = null;
        setCurrentPage("strona2");
        return "editUser";
    }

    public String saveUser() {
        try {
            userService.updatePassword(editUserOriginalLogin, editUserPassword);
            clearUserEdit();
            message = "Zapisano użytkownika.";
            setCurrentPage("strona1");
            return "saveUser";
        } catch (IllegalArgumentException exception) {
            message = exception.getMessage();
            setCurrentPage("strona2");
            return "saveUserError";
        }
    }

    public String cancelUserEdit() {
        clearUserEdit();
        message = null;
        setCurrentPage("strona1");
        return "cancelUserEdit";
    }

    private void clearUserEdit() {
        editUserOriginalLogin = null;
        editUserPassword = null;
    }

    private void setCurrentPage(String page) {
        FacesContext context = FacesContext.getCurrentInstance();
        if (context != null) {
            context.getExternalContext().getSessionMap().put(CURRENT_PAGE_SESSION_KEY, page);
        }
    }

    private String parameterValue(String key) {
        Map parameters = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        String value = (String) parameters.get(key);
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException("Nie wskazano użytkownika.");
        }
        return value.trim();
    }
}
