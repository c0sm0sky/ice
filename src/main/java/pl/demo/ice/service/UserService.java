package pl.demo.ice.service;

import org.springframework.transaction.annotation.Transactional;
import pl.demo.ice.db.User;
import pl.demo.ice.db.UserDao;

import java.util.ArrayList;
import java.util.List;

public class UserService implements UserOperations {
    private UserDao users;

    public void setUsers(UserDao users) { this.users = users; }

    @Transactional(readOnly = true)
    public User authenticate(String login, String haslo) {
        if (login == null || haslo == null) return null;
        User user = users.findByLogin(login.trim());
        return user != null && haslo.equals(user.getHaslo()) ? user : null;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> findAll() {
        List<User> records = users.findAll();
        List<UserSummary> result = new ArrayList<UserSummary>(records.size());
        for (User user : records) result.add(summary(user));
        return result;
    }

    @Transactional
    public void updatePassword(String login, String haslo) {
        User user = findRequired(login);
        user.setHaslo(required(haslo, "Podaj hasło.", 255));
    }

    private User findRequired(String login) {
        if (login == null || login.trim().length() == 0) {
            throw new IllegalArgumentException("Nie wskazano użytkownika.");
        }
        User user = users.findByLogin(login.trim());
        if (user == null) throw new IllegalArgumentException("Użytkownik już nie istnieje.");
        return user;
    }

    private String required(String value, String message, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() == 0) throw new IllegalArgumentException(message);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Maksymalna długość pola to " + maxLength + " znaków.");
        }
        return normalized;
    }

    private UserSummary summary(User user) {
        return new UserSummary(user.getLogin(), user.getImie(), user.getNazwisko());
    }
}
