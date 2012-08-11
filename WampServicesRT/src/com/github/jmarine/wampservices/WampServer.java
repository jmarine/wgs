package com.github.jmarine.wampservices;

import java.io.FileReader;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;

import com.sun.grizzly.websockets.WebSocketEngine;
import com.sun.grizzly.config.GrizzlyConfig;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource40;



/**
 * Standalone Java WAMP implementation.
 * Server expects to get the path to webapp as command line parameter
 *
 * @author Jordi Mariné Fort
 */
public class WampServer {

    
    public static void main(String[] args) throws Exception {
        // create a Grizzly HttpServer to server static resources from 'webapp', on PORT.
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.naming.java.javaURLContextFactory");
        System.setProperty(Context.URL_PKG_PREFIXES, "org.apache.naming");                    
        InitialContext ctx = new InitialContext();
        ctx.createSubcontext("jdbc");
       
        
        String configFileName = "wamp.properties";
        if(args.length > 0) configFileName = args[0];
        
        GrizzlyConfig grizzlyConfig = null;
        
        try(FileReader reader = new FileReader(configFileName)) {
            Properties wampConfig = new Properties();
            wampConfig.load(reader);
            reader.close();
            
            DocRootAdapter.setDocRoot(wampConfig.getProperty("docroot"));
            grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
            grizzlyConfig.setupNetwork();            
            
            
            String databases = wampConfig.getProperty("databases");
            if(databases != null) {
                StringTokenizer tokenizer = new StringTokenizer(databases, ",");
                while(tokenizer.hasMoreTokens()) {
                    String db = tokenizer.nextToken();
                    String jndi = wampConfig.getProperty("database." + db + ".jndi");
                    String url = wampConfig.getProperty("database." + db + ".url");
                    EmbeddedConnectionPoolDataSource40 ds = new EmbeddedConnectionPoolDataSource40();
                    ds.setDatabaseName(url);
                    ctx.bind(jndi, ds);
                } 
            }

            String contexts = wampConfig.getProperty("contexts");
            if(contexts != null) {
                StringTokenizer tkContexts = new StringTokenizer(contexts, ",");
                while(tkContexts.hasMoreTokens()) {
                    String context = tkContexts.nextToken();
                    String uri = wampConfig.getProperty("context." + context + ".uri");

                    boolean topicWildcardsEnabled = false;
                    String enableWildcards = wampConfig.getProperty("context." + context + ".enableTopicWildcards");
                    if((enableWildcards != null) && (enableWildcards.toUpperCase().equals("TRUE"))) topicWildcardsEnabled = true;
                    
                    System.out.println("Creating WAMP context URI: " + uri);
                    WampApplication wampApplication = new WampApplication(uri, topicWildcardsEnabled);

                    String topics = wampConfig.getProperty("context." + context + ".topics");
                    if(topics != null) {
                        StringTokenizer tkTopics = new StringTokenizer(topics, ",");
                        while(tkTopics.hasMoreTokens()) {
                            String topic = tkTopics.nextToken();
                            System.out.println("> Creating topic at "+uri+": " + topic);
                            wampApplication.createTopic(topic);
                        }
                    }                    
                    
                    String modules = wampConfig.getProperty("context." + context + ".modules");
                    if(modules != null) {
                        StringTokenizer tkModules = new StringTokenizer(modules, ",");
                        while(tkModules.hasMoreTokens()) {
                            String cls = tkModules.nextToken();
                            System.out.println("> Registering module at "+uri+": " + cls);
                            wampApplication.registerWampModule(Class.forName(cls));
                        }
                    }

                    // register the application
                    WebSocketEngine.getEngine().register(wampApplication);
                }
            }
        
            //server.start();
            System.out.println("Press any key to stop the server...");
            //noinspection ResultOfMethodCallIgnored
            System.in.read();
            
        } finally {
            // stop the grizzly service
            if(grizzlyConfig != null) {
                try { 
                    grizzlyConfig.shutdownNetwork(); 
                    System.out.println("Grizzly shut down normally");
                }
                catch(Exception ex) { 
                    System.out.println("Grizzly shut down with errors: " + ex.getMessage());
                    ex.printStackTrace();
                }
                
            }

            // stop embedded derby database
            if(ctx.list("jdbc").hasMore()) {
                try { 
                    DriverManager.getConnection("jdbc:derby:;shutdown=true");
                } catch (SQLException se) {
                    if (( (se.getErrorCode() == 50000) && ("XJ015".equals(se.getSQLState()) ))) {
                        System.out.println("Derby has been shut down normally");
                    } else {
                        System.err.println("Derby did not shut down normally (error code = " + se.getErrorCode() +", SQL state = " + se.getSQLState() + "): " + se.getMessage());
                        se.printStackTrace();
                    }
                }
            }
            
        }
    }
    
    
    protected static class DocRootAdapter extends StaticResourcesAdapter 
    {
        private static String docRoot = ".";

        protected static void setDocRoot(String docRoot) {
            DocRootAdapter.docRoot = docRoot;
        }

        public DocRootAdapter()
        {
            super(docRoot);
        }

    }
    
    
    
}
