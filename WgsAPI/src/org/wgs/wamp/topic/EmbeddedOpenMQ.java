package org.wgs.wamp.topic;

import java.util.Properties;
import javax.naming.InitialContext;

import com.sun.messaging.AdminConnectionFactory;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.runtime.BrokerInstance;
import com.sun.messaging.jmq.jmsclient.runtime.ClientRuntime;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsservice.BrokerEventListener;
import com.sun.messaging.jms.management.server.DestinationOperations;
import com.sun.messaging.jms.management.server.DestinationType;
import com.sun.messaging.jms.management.server.MQObjectName;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;


public class EmbeddedOpenMQ 
{
    private static BrokerInstance   brokerInstance = null;
    
    public static void start(Properties serverConfig) throws Exception
    {
        String imqHome = serverConfig.getProperty("imq.home");
        if(imqHome != null) {
            String instanceName = serverConfig.getProperty("imq.instancename", "wgs");
            System.out.println("Starting OpenMQ broker...");

            String[] args = { 
                "-imqhome", serverConfig.getProperty("imq.home"), 
                "-varhome", serverConfig.getProperty("imq.varhome"), 
                "-name",    instanceName
            };

            ClientRuntime clientRuntime = ClientRuntime.getRuntime();
            brokerInstance = clientRuntime.createBrokerInstance();

            Properties props = brokerInstance.parseArgs(args);
            props.put("imq."+instanceName+".max_threads", serverConfig.getProperty("imq."+instanceName+".max_threads", "10000"));
            BrokerEventListener listener = new EmbeddedOpenMQEventListener();
            brokerInstance.init(props, listener);
            brokerInstance.start();
        }

        com.sun.messaging.TopicConnectionFactory tcf = null;
        tcf = new com.sun.messaging.TopicConnectionFactory();
        tcf.setProperty(ConnectionConfiguration.imqAddressList, serverConfig.getProperty("imq.tcf.imqAddressList", "mq://localhost/direct"));

        InitialContext jndi = new InitialContext();
        jndi.bind("jms/CentralTopicConnectionFactory", tcf);
    }
    

    public static void stop() 
    {
        if(brokerInstance != null) {
            System.out.println("Stoping OpenMQ broker...");
            brokerInstance.stop();
            brokerInstance.shutdown();
        }
    }
    
    
    public static void destroyTopic(String topicName) throws Exception
    {
        AdminConnectionFactory acf = new AdminConnectionFactory();
        JMXConnector jmxc = acf.createConnection("admin", "admin");
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

        ObjectName destMgrMonitorName = new ObjectName(MQObjectName.DESTINATION_MANAGER_CONFIG_MBEAN_NAME);

        Object opParams[] = { DestinationType.TOPIC, JmsServices.normalizeTopicName(topicName) };
        String opSig[] = { String.class.getName(), String.class.getName() };

        mbsc.invoke(destMgrMonitorName, DestinationOperations.DESTROY, opParams, opSig);
        jmxc.close();
    }    
    
    
}


class EmbeddedOpenMQEventListener implements BrokerEventListener 
{
    @Override
    public void brokerEvent(BrokerEvent brokerEvent) 
    {
        System.out.println ("Received broker event: "+brokerEvent);
    }

    @Override
    public boolean exitRequested(BrokerEvent event, Throwable thr) 
    {
        System.out.println ("Broker is requesting a shutdown because of: "+event+" with "+thr);
        // return true to allow the broker to shutdown
        return true;
    }

}


