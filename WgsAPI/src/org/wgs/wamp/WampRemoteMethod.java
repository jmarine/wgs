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
            String regExp = options.getText("paritition");
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

                WampProtocol.sendInvocationMessage(remotePeer, invocationId, registrationId, invocationOptions, args, argsKw);
                    
                return null;
            }

            @Override
            public void cancel(WampDict cancelOptions) {
                WampProtocol.sendInterruptMessage(remotePeer, invocationId, cancelOptions);
            }           
            
        };


    }    
    
}
