package org.wgs.wamp.jms;

import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.Topic;
import jakarta.jms.TopicConnection;
import jakarta.jms.TopicConnectionFactory;
import jakarta.jms.TopicPublisher;
import jakarta.jms.TopicSession;
import jakarta.jms.TopicSubscriber;

import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class WampJmsTests 
{
    public static final void main(String args[]) throws Exception 
    {
        String url = "ws://localhost:8080/wgs";
        String realm = "localhost";
        String user = null;
        String password = null;
        boolean digestMD5 = false;
        
        String topicName = "myapp";
        
        TopicConnectionFactory tcf = new WampTopicConnectionFactory(WampEncoding.MsgPack, url, realm, digestMD5);
        TopicConnection connection = tcf.createTopicConnection(user, password);

        TopicSession session = connection.createTopicSession(false, jakarta.jms.Session.AUTO_ACKNOWLEDGE);
        Topic jmsTopic = session.createTopic(topicName);

        String selector = "";
        
        synchronized(connection) {
            connection.stop();
            TopicSubscriber subscriber = session.createSubscriber(jmsTopic, selector, false);
            subscriber.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message msg) {
                    System.out.println("onMessage: "+ msg);
                }
            });
            
            connection.start();
            
        }

        
        TopicPublisher publisher = session.createPublisher(jmsTopic);
        WampDict details = new WampDict();
        details.put("_test", "value");
        WampMessage msg = new WampMessage(0l, jmsTopic, details, new WampList(1,2,3), (new WampDict()).put("arg1", "val1"));
        publisher.send(msg);
        
        System.out.println("Press a key to quit");
        System.in.read();

        publisher.close();
        
    }
}
