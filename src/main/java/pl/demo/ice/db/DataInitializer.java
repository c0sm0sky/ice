package pl.demo.ice.db;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import java.util.List;

public class DataInitializer {
    private SessionFactory sessionFactory;

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void initialize() {
        Session session = sessionFactory.openSession();
        Transaction transaction = session.beginTransaction();
        try {
            User admin = (User) session.createQuery("from User u where u.login = :login")
                    .setString("login", "admin").uniqueResult();
            if (admin == null) {
                admin = new User();
                admin.setLogin("admin");
                admin.setHaslo("admin");
                admin.setImie("Admin");
                admin.setNazwisko("Systemu");
                session.save(admin);
            }
            @SuppressWarnings("unchecked")
            List<User> users = session.createQuery("from User u order by u.login").list();
            for (User user : users) {
                String suffix = String.valueOf(user.getId());
                addDefaultReport(session, user, "Profil użytkownika nr " + suffix, "Profil użytkownika",
                        "Podstawowe dane wybranego użytkownika.");
                addDefaultReport(session, user, "Raporty użytkownika nr " + suffix, "Raporty użytkownika",
                        "Raporty przypisane do wybranego użytkownika.");
            }
            transaction.commit();
        } catch (RuntimeException exception) {
            transaction.rollback();
            throw exception;
        } finally {
            session.close();
        }
    }

    private void addDefaultReport(Session session, User owner, String code, String title, String description) {
        Report existing = (Report) session.createQuery("from Report r where r.code = :code")
                .setString("code", code).uniqueResult();
        if (existing == null) {
            Report report = new Report();
            report.setCode(code);
            report.setTitle(title);
            report.setDescription(description);
            report.setOwner(owner);
            session.save(report);
        } else if (!description.equals(existing.getDescription()) || !title.equals(existing.getTitle())) {
            existing.setTitle(title);
            existing.setDescription(description);
            session.merge(existing);
        }
    }
}
