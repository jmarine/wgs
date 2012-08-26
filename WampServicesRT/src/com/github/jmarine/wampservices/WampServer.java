/**
 * Standalone Java WAMP implementation.
  *
 * @author Jordi Mariné Fort
 */

package com.github.jmarine.wampservices;

import com.sun.grizzly.config.GrizzlyConfig;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.websockets.WebSocketEngine;
import java.io.FileReader;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.StringTokenizer;
import javax.naming.Context;
import javax.naming.InitialContext;
import org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource40;


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

        FileReader configReader = null;
        Properties wampConfig = new Properties();
        try {
            configReader = new FileReader(configFileName);
            wampConfig.load(configReader);
        } finally {
            if(configReader != null) {
                configReader.close(); 
                configReader=null;
            }            
        }
        
        try {
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
                            wampApplication.createTopic(topic, null);
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
            
            // SSL fix for Grizzly 1.9 (it is not required for Grizzly 2.x):
            if(System.getProperty("os.name").equalsIgnoreCase("linux")) {
                this.setUseSendFile(false);  
            }
        }

    }
    
    
}
