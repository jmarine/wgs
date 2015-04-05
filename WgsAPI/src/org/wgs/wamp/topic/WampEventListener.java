package org.wgs.wamp.topic;

import org.wgs.wamp.WampSocket;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;

@FunctionalInterface
public interface WampEventListener {
    
    void onEvent(WampSocket serverSocket, Long subscriptionId, Long publicationId, WampDict details, WampList payload, WampDict payloadKw) throws Exception;
    
}
