package org.wgs.wamp;

import java.util.HashMap;


public class WampRemoteMethod extends WampMethod
{
    private Long registrationId;
    
    private WampSocket remotePeer;
    
    private MatchEnum matchType;
    
    private WampDict options;

    private HashMap<Long,WampSocket> pendingInvocations = new HashMap<Long,WampSocket>();
    
    
    public WampRemoteMethod(Long registrationId, WampSocket remotePeer, MatchEnum matchType, WampDict options)
    {
        super(null);
        this.registrationId = registrationId;
        this.remotePeer = remotePeer;
        this.matchType = matchType;
        this.options = options;
    }
    
    public boolean hasPartition(String partition)
    {
        if(options != null && options.has("partition")) {
            String regExp = options.get("paritition").asText();
            return partition == null || WampServices.isUriMatchingWithRegExp(partition, regExp);
        } else {
            return true;
        }
        
    }
    
    public WampSocket getRemotePeer()
    {
        return remotePeer;
    }
    
    
    @Override
    public Object invoke(final WampCallController task, final WampSocket clientSocket, final WampList args, final WampDict argsKw, final WampCallOptions callOptions) throws Exception
    {
        final Long invocationId = WampProtocol.newId();

        return new WampAsyncCall(null) {
            
            @Override
            public Void call() throws Exception {
                remotePeer.addRpcController(invocationId, task);
                remotePeer.addAsyncCallback(invocationId, getAsyncCallback());

                WampDict invocationOptions = new WampDict();
                if(matchType != MatchEnum.exact) invocationOptions.put("procedure", task.getProcedureURI());
                if(callOptions.hasDiscloseMe())  invocationOptions.put("caller", clientSocket.getSessionId());

                WampList msg = new WampList();
                msg.add(80);
                msg.add(invocationId);
                msg.add(registrationId);
                msg.add(invocationOptions);
                msg.add(args);
                msg.add(argsKw);                
                remotePeer.sendWampMessage(msg); 
                    
                return null;
            }

            @Override
            public void cancel(WampDict cancelOptions) {
                WampList msg = new WampList();
                msg.add(81);
                msg.add(invocationId);
                msg.add(cancelOptions);
                remotePeer.sendWampMessage(msg);
            }           
            
        };


    }    
    
}
