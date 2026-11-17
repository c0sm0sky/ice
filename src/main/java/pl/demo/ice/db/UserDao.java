package pl.demo.ice.db;

import org.hibernate.SessionFactory;

import java.util.List;

public class UserDao {
    private SessionFactory sessionFactory;

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public User findByLogin(String login) {
        return (User) sessionFactory.getCurrentSession()
                .createQuery("from User u where u.login = :login")
                .setString("login", login)
                .uniqueResult();
    }

    @SuppressWarnings("unchecked")
    public List<User> findAll() {
        return sessionFactory.getCurrentSession().createQuery("from User u order by u.login").list();
    }

}
