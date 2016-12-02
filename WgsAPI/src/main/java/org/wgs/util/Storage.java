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
    
    public static <T> void createEntity(T entity)
    {
        EntityManager manager = null;
        EntityTransaction transaction = null;

        try {
            if(entity != null) {
                manager = getEntityManager();
                transaction = manager.getTransaction();

                transaction.begin();
                manager.persist(entity);
                transaction.commit();
            }
        } catch(Exception ex) {
            if(transaction != null) {
                try { transaction.rollback(); } 
                catch(Exception ex2) { }
            }
            throw ex;
            
        } finally {
            if(manager != null) {
                try { manager.close(); } 
                catch(Exception ex) { }
            }
        }
        
    }    
    
    public static <T> T saveEntity(T entity)
    {
        EntityManager manager = null;
        EntityTransaction transaction = null;

        try {
            if(entity != null) {
                manager = getEntityManager();
                transaction = manager.getTransaction();

                transaction.begin();
                entity = manager.merge(entity);
                transaction.commit();
            }
        } catch(Exception ex) {
            if(transaction != null) {
                try { transaction.rollback(); } 
                catch(Exception ex2) { }
            }
            throw ex;
            
        } finally {
            if(manager != null) {
                try { manager.close(); } 
                catch(Exception ex) { }
            }
        }
        return entity;
    }
    
    public static <T> T removeEntity(T entity)
    {
        EntityManager manager = null;
        EntityTransaction transaction = null;
        
        try {
            if(entity != null) {
                manager = getEntityManager();
                transaction = manager.getTransaction();

                transaction.begin();
                entity = manager.merge(entity);
                manager.remove(entity);
                transaction.commit();
            }
            
        } catch(Exception ex) {
            if(transaction != null) {
                try { transaction.rollback(); } 
                catch(Exception ex2) { }
            }
            throw ex;
            
        } finally {
            if(manager != null) {
                try { manager.close(); } 
                catch(Exception ex) { }
            }
        }
        return entity;
    }    
    
    public static <T> T findEntity(Class<T> cls, Object key) 
    {
        T entity = null;
        EntityManager manager = null;
        
        try {
            manager = getEntityManager();
            entity = manager.find(cls, key);
            
        } finally {
            if(manager != null) {
                try { manager.close(); }
                catch(Exception ex) { }
            }
        }        

        return entity;
    }
    
    public static <T> List<T> findEntities(Class<T> cls, String namedQueryName, Object ... params)
    {
        List<T> entities =null;
        EntityManager manager = null;
        
        try { 
             manager = getEntityManager();

            javax.persistence.TypedQuery<T> query = manager.createNamedQuery(namedQueryName, cls);
            if(params != null) {
                for(int index = 0; index < params.length; index++) {
                    query.setParameter(index+1, params[index]);
                }
            }

            entities = query.getResultList();

        } finally {
            if(manager != null) {
                try { manager.close(); }
                catch(Exception ex) { }
            }
        }

        return entities;
    }
    
}
