package pl.demo.ice.db;

import org.hibernate.SessionFactory;
import java.util.List;
import org.hibernate.Session;

public class ReportDao {
    private SessionFactory sessionFactory;
    public void setSessionFactory(SessionFactory sessionFactory) { this.sessionFactory = sessionFactory; }

    @SuppressWarnings("unchecked")
    public List<Report> findAll() {
        Session session = sessionFactory.openSession();
        try {
            return session.createQuery("from Report r join fetch r.owner order by r.id").list();
        } finally { session.close(); }
    }

    @SuppressWarnings("unchecked")
    public List<Report> findForOwner(Long ownerId) {
        Session session = sessionFactory.openSession();
        try {
            return session.createQuery("from Report r join fetch r.owner where r.owner.id = :ownerId order by r.id")
                    .setLong("ownerId", ownerId).list();
        } finally { session.close(); }
    }
}
