package org.wgs.util;

import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Parameter;
import javax.persistence.Persistence;


public class Storage 
{
    private static final String PU_NAME = "WgsPU";
    
    private static EntityManagerFactory emf = Persistence.createEntityManagerFactory(PU_NAME);        
    
    
    public static EntityManager getEntityManager()
    {
        EntityManager manager = emf.createEntityManager();
        return manager;
    }
    
    
    public static <T> T saveEntity(T entity)
    {
        if(entity != null) {
            EntityManager manager = getEntityManager();
            EntityTransaction transaction = manager.getTransaction();
            transaction.begin();

            entity = manager.merge(entity);

            transaction.commit();
            manager.close();        
        }
        return entity;
    }
    
    public static <T> T removeEntity(T entity)
    {
        if(entity != null) {
            EntityManager manager = getEntityManager();
            EntityTransaction transaction = manager.getTransaction();
            transaction.begin();

            entity = manager.merge(entity);
            manager.remove(entity);

            transaction.commit();
            manager.close();        
        }
        return entity;
    }    
    
    public static <T> T findEntity(Class<T> cls, Object key) 
    {
        EntityManager manager = getEntityManager();
        T entity = manager.find(cls, key);
        manager.close();

        return entity;
    }
    
    public static <T> List<T> findEntities(Class<T> cls, String namedQueryName, Object ... params)
    {
        EntityManager manager = getEntityManager();

        javax.persistence.TypedQuery<T> query = manager.createNamedQuery(namedQueryName, cls);
        if(params != null) {
            for(int index = 0; index < params.length; index++) {
                query.setParameter(index+1, params[index]);
            }
        }

        List<T> entities = query.getResultList();

        manager.close();

        return entities;
    }
    
}
