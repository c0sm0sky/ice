package pl.demo.ice.db;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

public class UserDataInitializer {
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
            transaction.commit();
        } catch (RuntimeException exception) {
            transaction.rollback();
            throw exception;
        } finally {
            session.close();
        }
    }
}
