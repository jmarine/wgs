package org.wgs.wamp;

import java.util.Collection;
import java.util.HashMap;


public class WampRemoteMethod extends WampMethod
{
    private Long registrationId;
    
    private MatchEnum matchType;
    
    private String methodRegExp;
    
    private WampDict options;
    
    private HashMap<Long,WampSocket> remotePeers = new HashMap<Long,WampSocket>();
    
    private HashMap<Long,WampSocket> pendingInvocations = new HashMap<Long,WampSocket>();
    
    
    public WampRemoteMethod(Long registrationId, MatchEnum matchType, String methodUriOrPattern, WampDict options)
    {
        super(methodUriOrPattern);
        this.registrationId = registrationId;
        this.matchType = matchType;
        this.methodRegExp = WampServices.getPatternRegExp(matchType, methodUriOrPattern);
        this.options = options;
    }
    
    public Long getId()
    {
        return this.registrationId;
    }    
    
    public String getRegExp()
    {
        return methodRegExp;
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
    
    
    
    public void addRemotePeer(Long registrationId, WampSocket remotePeer)
    {
        
        remotePeers.put(registrationId, remotePeer);
    }
    
    public void removeRemotePeer(Long registrationId)
    {
        remotePeers.remove(registrationId);
    }
    
    public WampSocket getRemotePeer(Long registrationId)
    {
        return remotePeers.get(registrationId);
    }
    
    
    public Collection<Long> selectRemotePeers(WampCallOptions callOptions)
    {
        return remotePeers.keySet();
    }
    
    @Override
    public Object invoke(final WampCallController task, WampSocket clientSocket, final WampList args, final WampDict argsKw, final WampCallOptions callOptions) throws Exception
    {
        final Collection<Long> registrationIds = selectRemotePeers(callOptions);
        final HashMap<Long,Long> invocationIdByRegistrationId = new HashMap<Long,Long>();
        for(Long registrationId : registrationIds) {
            invocationIdByRegistrationId.put(registrationId, WampProtocol.newId());
        }

        return new WampAsyncCall() {
            
            @Override
            public void call() throws Exception {
                for(Long registrationId : registrationIds) {
                    WampSocket remotePeer = getRemotePeer(registrationId);
                    Long invocationId = invocationIdByRegistrationId.get(registrationId);
                    remotePeer.addRpcController(invocationId, task);

                    WampDict invocationOptions = new WampDict();
                    if(matchType != MatchEnum.exact) invocationOptions.put("procedure", task.getProcedureURI());
                    
                    WampList msg = new WampList();
                    msg.add(80);
                    msg.add(invocationId);
                    msg.add(registrationId);
                    msg.add(invocationOptions);
                    msg.add(args);
                    msg.add(argsKw);                
                    remotePeer.sendWampMessage(msg); 
                }
            }

            @Override
            public void cancel(WampDict cancelOptions) {
                for(Long registrationId : registrationIds) {
                    WampSocket remotePeer = getRemotePeer(registrationId);
                    WampList msg = new WampList();
                    msg.add(81);
                    msg.add(invocationIdByRegistrationId.get(registrationId));
                    msg.add(cancelOptions);
                    remotePeer.sendWampMessage(msg);
                }
            }            
        };


    }    
    
}
