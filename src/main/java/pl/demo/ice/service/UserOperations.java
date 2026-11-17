package pl.demo.ice.service;

import pl.demo.ice.db.User;

import java.util.List;

public interface UserOperations {
    User authenticate(String login, String haslo);
    List<UserSummary> findAll();
    void updatePassword(String login, String haslo);
}
