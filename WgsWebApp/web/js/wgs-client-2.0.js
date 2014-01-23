var WgsState = {
  DISCONNECTED: 0,
  ERROR: 1,
  CONNECTED: 2,
  WELCOMED: 3,
  ANONYMOUS: 4,  
  AUTHENTICATED: 5
};

function WgsClient(u) {
  this.serverWampVersion = 1;
  this.url = u;
  return this;
}

WgsClient.prototype = {
  ws: null,
  sid: null,
  state: WgsClient.DISCONNECTED,
  groups: new Array(),
  incomingHeartbeatSeq: 0,
  outgoingHeartbeatSeq: 0,
  heartbeatIntervalHandler: null,
  pendingRequests: new Array(),
  rpcRegistrationsById: new Array(),
  rpcRegistrationsByURI: new Array(),
  rpcHandlers: new Array(),
  subscriptionsById: new Array(),
  subscriptionsByTopicAndOptions: new Array(),
  eventHandlers: new Array(),
  metaeventHandlers: new Array(),
  
  debug: function(str) {
    console.log(str);
  },
          
  setState: function(state) {
      this.state = state;
  },
          
  getState: function() {
    return this.state;  
  },

  close: function() {
    if(this.heartbeatIntervalHandler != null) {
        clearInterval(this.heartbeatIntervalHandler);
        this.heartbeatIntervalHandler = null;
    }
    if(this.ws /* && this.ws.state == this.ws.OPEN */) {
        this.ws.close();
        this.ws = null;
    }
    this.state = WgsState.DISCONNECTED;
  },

  send: function(msg) {
      if(!this.ws || this.ws.readyState != 1) {
         this.debug("Websocket is not avaliable for writing");
      } else {
         this.ws.send(msg);
      }
  },
  
  _newid: function() {
      var r = Math.random()*131072.0*131072.0*131072.0 + Math.random()*131072.0*131072.0 + Math.random()*131072.0;
      return Math.floor(r);
  },
          
  hello: function() {
      this.serverSID = this._newid();
      var arr = [];
      arr[0] = 1;  // HELLO
      arr[1] = this.serverSID;
      arr[2] = {};  // HelloDetails
      arr[2].agent = "wgs-client-2.0-alpha1";
      arr[2].roles = {};
      arr[2].roles.publisher = {};
      arr[2].roles.publisher.features = { "subscriber_blackwhite_listing": true, "publisher_exclusion": true };
      arr[2].roles.subscriber = {};
      arr[2].roles.subscriber.features = { "publisher_identification": true, "pattern_based_subscription": true, "subscriber_metaevents": true };
      arr[2].roles.caller = {};
      arr[2].roles.caller.features = { "caller_identification": true, "call_canceling": true, "progressive_call_results": true };
      arr[2].roles.callee = {};
      arr[2].roles.callee.features = { "caller_identification": true, "pattern_based_registration": true };
      this.send(JSON.stringify(arr));
  },
          
  goodbye: function(details) {
      if(!details) details = {};
      var arr = [];
      arr[0] = 2;   // Goodbye
      arr[1] = details;
      this.send(JSON.stringify(arr));
  },
  
  heartbeat: function(timeout, discard) {
      var client = this;

      var sendHeartbeat = function() {
          var arr = [];
          arr[0] = 3;   // Goodbye
          arr[1] = client.incomingHeartbeatSeq;
          arr[2] = ++client.outgoingHeartbeatSeq;
          arr[3] = discard;
          client.send(JSON.stringify(arr));
      }
      
      if(client.heartbeatIntervalHandler != null) {
          clearInterval(client.heartbeatIntervalHandler);
          client.heartbeatIntervalHandler = null;
      }
      
      if(timeout != 0) client.heartbeatIntervalHandler = setInterval(sendHeartbeat, timeout);
  },
 
  call: function(cmd, args, argsKw, wampOptions) {
      var dfd = $.Deferred();
      var msg = [];
      msg[0] = 48;
      msg[1] = this._newid();
      msg[2] = (wampOptions!=null) ? wampOptions : {};
      msg[3] = cmd;
      if(args != null || argsKw != null) msg[4] = (args!=null)? ((args instanceof Array)? args : [args] ) : [];
      if(argsKw!=null) msg[5] = argsKw;

      this.pendingRequests[msg[1]] = [dfd];
      this.send(JSON.stringify(msg));
      return dfd.promise();
  },  
  
  cancelCall: function(callID, options) {
      var arr = [];
      arr[0] = 49;  // CANCEL CALL
      arr[1] = callID;
      if(options) arr[2] = options;
      this.send(JSON.stringify(arr));      
  },
  
  subscribe: function(topicURI, event_cb, metaevent_cb, options) {
        if(!options) options = {};
        if(event_cb==null && metaevent_cb!=null) options.metaonly = 1;
        
        if(!options.match && topicURI.indexOf("..") != -1) {
            options.match = "wildcard";
        }
        
        if(options.match && options.match.toLowerCase() == "prefix") {
            topicURI = topicURI + "..";
        }

        var dfd = $.Deferred();            
        var arr = [];
        arr[0] = 32;  // SUBSCRIBE
        arr[1] = this._newid();
        arr[2] = options;
        arr[3] = topicURI;      
        var topicAndOptionsKey = this._getTopicAndOptionsKey(topicURI,options);
        this.pendingRequests[arr[1]] = [dfd, topicURI, event_cb, metaevent_cb, options];
        this.send(JSON.stringify(arr));
        return dfd.promise();
  },
  
  _getTopicAndOptionsKey: function(topicPattern, options)
  {
      if(!options) options = {};
      if(!options.match) options.match = (topicPattern.indexOf("..") != -1)? "wildcard" : "exact";
      else if(options.match=="prefix") topicPattern = topicPattern + "..";
      return topicPattern;
  },
  
  getSubscriptionIdByTopicOptions: function(topicPattern, options)
  {
      var topicAndOptionsKey = this._getTopicAndOptionsKey(topicPattern, options);
      return this.subscriptionsByTopicAndOptions[topicAndOptionsKey];
  },
  
  unsubscribe: function(topic, event_cb, metaevent_cb, options) {
        var client = this;
        
        if(!options) options = {};
        
        if(!options.match && topic.indexOf("..") != -1) {
            options.match = "wildcard";
        }
        
        if(options.match && options.match.toLowerCase() == "prefix") {
            topic = topic + "..";
        }
        
        var dfd = $.Deferred();            
        var arr = [];
        arr[0] = 34;  // UNSUBSCRIBE
        arr[1] = this._newid();
        arr[2] = this.getSubscriptionIdByTopicOptions(topic,options);
        var topicAndOptionsKey = this._getTopicAndOptionsKey(topic,options);
        this.pendingRequests[arr[1]] = [dfd, arr[2], topicAndOptionsKey, event_cb, metaevent_cb];
        this.send(JSON.stringify(arr));
        
        return dfd.promise();
  },
  
  publish: function(topic, payload, payloadKw, options) {
      var dfd = $.Deferred();
      var arr = [];
      arr[0] = 16;  // PUBLISH
      arr[1] = this._newid();
      arr[2] = (options) ? options : {};      
      arr[3] = topic;
      arr[4] = payload;
      arr[5] = payloadKw;
      this.send(JSON.stringify(arr));
      this.pendingRequests[arr[1]] = dfd;
      return dfd.promise();
  }, 
  
  registerRPC: function(options, procedureURI, callback) {
        var dfd = $.Deferred();            
        var arr = [];
        arr[0] = 64;  // REGISTER
        arr[1] = this._newid();
        arr[2] = options;
        arr[3] = procedureURI;      
        this.pendingRequests[arr[1]] = [dfd, procedureURI, callback];
        this.send(JSON.stringify(arr));
        return dfd.promise();   
  },
  
  unregisterRPC: function(options, procedureURI, callback) {
        var registrationId = this.rpcRegistrationsByURI[procedureURI];
        var dfd = $.Deferred();            
        var arr = [];
        arr[0] = 66;  // UNREGISTER
        arr[1] = this._newid();
        arr[2] = registrationId;
        this.pendingRequests[arr[1]] = [dfd, procedureURI, callback];
        this.send(JSON.stringify(arr));
        return dfd.promise();      
  },
  
  authreq: function(authKey, authExtra, callback) {
      if(!authExtra) authExtra = {};
      var args = []
      args[0] = authKey;
      args[1] = authExtra;
      this.call("wamp.cra.request", args).then(callback,callback);      
  },
          
  auth: function(signature, callback) {
      this.call("wamp.cra.authenticate", signature).then(callback,callback);      
  },          
  
  getUserInfo: function(callback) {
      this.call("wgs.get_user_info").then(callback,callback);
  },
  
  login: function(user, password, onstatechange) {
      var client = this;
      client._connect(function(state, msg) {
        onstatechange(state, msg);
        if(state == WgsState.WELCOMED) {
          if(user == null || user.length == 0) {
            client.user = "#anonymous-" + client.sid;
            client.state = WgsState.ANONYMOUS;
            onstatechange(WgsState.ANONYMOUS);              
          } else {
            var authExtra = null; // { salt: "RANDOMTEXT", keylen: 32, iterations: 4096 };
            client.authreq(user, authExtra, function(id,details,errorURI,result,resultKw) {
                if(result && result.length > 0 && typeof(result[0]) == "string") {
                    var challenge = JSON.parse(result[0]);
                    password = CryptoJS.MD5(password).toString();
                    if(challenge.extra && challenge.extra.salt) {
                        var key = CryptoJS.PBKDF2(password, challenge.extra.salt, { keySize: challenge.extra.keylen / 4, iterations: challenge.extra.iterations, hasher: CryptoJS.algo.SHA256 });
                        password = key.toString(CryptoJS.enc.Base64);                        
                    }
                    var signature = CryptoJS.HmacSHA256(result[0], password).toString(CryptoJS.enc.Base64);
                    client.auth(signature, function(id,details,errorURI,result,resultKw) {
                        if(!errorURI) {
                            client.getUserInfo(function(id,details,errorURI,result,resultKw) {
                                if(!errorURI) {
                                    client.user = user;
                                    client.state = WgsState.AUTHENTICATED;
                                    onstatechange(WgsState.AUTHENTICATED, resultKw);
                                } else {
                                    var errorCode = resultKw.errorURI;
                                    onstatechange(WgsState.ERROR, errorCode);
                                }
                            });
                        } else {
                            var errorCode = resultKw.errorURI;
                            onstatechange(WgsState.ERROR, errorCode);
                        }
                    });
                } else {
                    var errorCode = resultKw.errorURI;
                    onstatechange(WgsState.ERROR, errorCode);
                }
            });
          }
        }
      });
  },
  
  
  registerUser: function(user, password, email, onstatechange) {
      var client = this;
      client._connect(function(state, msg) {
        onstatechange(state, msg);
        if(state == WgsState.WELCOMED) {
            var msg = Object();
            msg.user = user;
            msg.password = CryptoJS.MD5(password).toString();
            msg.email = email;
            client.call("wgs.register", msg).then(
                function(id,details,errorURI,result,resultKw) {
                    client.user = resultKw.user;
                    client.state = WgsState.AUTHENTICATED;
                    onstatechange(WgsState.AUTHENTICATED, resultKw);
                }, 
                function(id,details,errorURI,result,resultKw) {
                    onstatechange(WgsState.ERROR, errorURI);
                });
        }
      });
  },  
  
  
  openIdConnectProviders: function(redirectUri, callback) {
      var client = this;
      client._connect(function(state, msg) {
        if(state == WgsState.WELCOMED) {
            var msg = Object();
            msg.redirect_uri = redirectUri;
            client.call("wgs.openid_connect_providers", msg).then(
                function(id,details,errorURI,result,resultKw) {
                    //client.close();
                    callback(id,details,errorURI,result,resultKw);
                }, 
                function(id,details,errorURI,result,resultKw) {
                    client.close();
                    callback(id,details,errorURI,result,resultKw);
                });
        } 
      });
  },

  openIdConnectLoginUrl: function(principal, redirectUri, notificationChannel, onstatechange) {
      var client = this;
      client._connect(function(state, msg) {
        if(state == WgsState.WELCOMED) {
            var msg = Object();
            msg.principal = principal;
            msg.redirect_uri = redirectUri;
            msg.state = notificationChannel;
            client.call("wgs.openid_connect_login_url", msg).then(
                function(id,details,errorURI,result,resultKw) {
                    client.close();
                    //document.location.href = result;
                    window.open(response, "_blank");  // + "&nonce=" + escape(client.sid)
                }, 
                function(id,details,errorURI,result,resultKw) {
                    onstatechange(WgsState.ERROR, errorURI);
                });
        }
      });
  },

  
  openIdConnectAuthCode: function(provider, redirectUri, code, notificationChannel, onstatechange) {
      var client = this;
      client._connect(function(state, msg) {
        onstatechange(state, msg);          
        if(state == WgsState.WELCOMED) {
            var msg = Object();
            msg.provider = provider;
            msg.code = code;
            msg.redirect_uri = redirectUri;
            if(notificationChannel) msg.notification_channel = notificationChannel;
            
            client.call("wgs.openid_connect_auth", msg).then(
                function(id,details,errorURI,result,resultKw) {
                    client.user = resultKw.user;
                    client.state = WgsState.AUTHENTICATED;
                    onstatechange(WgsState.AUTHENTICATED, resultKw);
                }, 
                function(id,details,errorURI,result,resultKw) {
                    onstatechange(WgsState.ERROR, errorURI);
                });
        }
      });
  },
  
  
  _connect: function(onstatechange) {
      var client = this;
      this.user = null;
      this.debug("Connecting to url: " + this.url);

      if(this.state >= WgsState.WELCOMED) {
        // REAUTHENTICATION
        onstatechange(WgsState.WELCOMED);
        
      } else {
        // RESET CONNECTION:
        var ws = null; 
        this.ws = null;
        
        if ("WebSocket" in window) {
          ws = new WebSocket(this.url);
        } else if ("MozWebSocket" in window) {
          ws = new MozWebSocket(this.url);
        } else {
          this.debug("This Browser does not support WebSockets");
          onstatechange(WgsState.ERROR, "browser.websockets_not_supported");
          return;
        }

        ws.onopen = function(e) {
          client.debug("A connection to "+this.url+" has been opened.");
          client.ws = ws;
          this.state = WgsState.CONNECTED;
          onstatechange(WgsState.CONNECTED);
          client.hello();  // FIX: move to ws.onopen
        };

        ws.onclose = function(e) {
          client.debug("The connection to "+this.url+" was closed.");
          onstatechange(WgsState.DISCONNECTED);    
          client.close();
        };

        ws.onerror = function(e) {
          client.debug("WebSocket error: " + e);
          onstatechange(WgsState.ERROR, "wgs.websocket.error");
          client.close();
        };

        ws.onmessage = function(e) {
          client.debug("ws.onmessage: " + e.data);
          var arr = JSON.parse(e.data);

          if (arr[0] == 1) {  // HELLO
              client.sid = arr[1];
              client.state = WgsState.WELCOMED;
              onstatechange(WgsState.WELCOMED);
              
          } else if (arr[0] == 2) {  // GOODBYE 
              onstatechange(WgsState.DISCONNECTED);    
              client.close();
              
          } else if (arr[0] == 3) {  // HEARTBEAT
              client.incomingHeartbeatSeq = arr[2];
              // TODO: request unreceived EVENTs ?
              
          } else if (arr[0] == 4) {  // ERROR
              var requestId = arr[1];
              if(client.pendingRequests[requestId]) {
                var details = arr[2];
                var errorURI = arr[3];
                var args = (arr.length>4)? arr[4] : [];
                var argsKw = (arr.length>5)? arr[5] : {};
                var promise = client.pendingRequests[requestId][0];
                promise.reject(requestId, details, errorURI, args, argsKw);
                delete client.pendingRequests[requestId];     
              }

          } else if (arr[0] == 50) {  // RESULT
              var requestId = arr[1];
              if(client.pendingRequests[requestId]) {      
                  var promise = client.pendingRequests[requestId][0];
                  var details = arr[2];
                  var result = (arr && arr.length > 2)? arr[3] : [];
                  var resultKw = (arr && arr.length > 3)? arr[4] : {};
                  if(details && details.progress) {
                      promise.notify(requestId, details, null, result, resultKw);
                  } else {
                      promise.resolve(requestId, details, null, result, resultKw);
                      delete client.pendingRequests[requestId];
                  }
              } else {
                  client.debug("call not found: " + requestId);
              }
              
          } else if(arr[0] == 33) {  // SUBSCRIBED
              var requestId = arr[1];
              if(requestId && client.pendingRequests[requestId]) {
                  var subscriptionId = arr[2];
                  var promise = client.pendingRequests[requestId][0];
                  var topicURI = client.pendingRequests[requestId][1];
                  var options = client.pendingRequests[requestId][4];
                  var topicAndOptionsKey = client._getTopicAndOptionsKey(topicURI,options);
                  client.subscriptionsById[subscriptionId] = topicURI;
                  client.subscriptionsByTopicAndOptions[topicAndOptionsKey] = subscriptionId;
                  if(client.pendingRequests[requestId][2]) {
                    if(!client.eventHandlers[subscriptionId]) client.eventHandlers[subscriptionId] = [];
                    client.eventHandlers[subscriptionId].push(client.pendingRequests[requestId][2]);
                  }
                  if(client.pendingRequests[requestId][3]) {                  
                    if(!client.metaeventHandlers[subscriptionId]) client.metaeventHandlers[subscriptionId] = [];
                    client.metaeventHandlers[subscriptionId].push(client.pendingRequests[requestId][3]);
                  }
                  promise.resolve(requestId,subscriptionId);
                  delete client.pendingRequests[requestId];
              }
              
          } else if(arr[0] == 35) {  // UNSUBSCRIBED
              var requestId = arr[1];
              if(requestId && client.pendingRequests[requestId]) {
                  var promise = client.pendingRequests[requestId][0];
                  var subscriptionId = client.pendingRequests[requestId][1];
                  var topicAndOptionsKey = client.pendingRequests[requestId][2];
                  
                  var event_cb = client.pendingRequests[requestId][3];
                  var metaevent_cb = client.pendingRequests[requestId][4];
                  var callbacks = client.eventHandlers[subscriptionId];
                  if(callbacks && callbacks.length>0) {
                      var indexOfEventHandlerToClear = callbacks.indexOf(event_cb);
                      if(event_cb && callbacks && callbacks.length<=1 && indexOfEventHandlerToClear!=-1) delete client.eventHandlers[subscriptionId];
                      else if(indexOfEventHandlerToClear != -1) client.eventHandlers[subscriptionId].splice(indexOfEventHandlerToClear,1);
                  }
            
                  callbacks = client.metaeventHandlers[subscriptionId];
                  if(callbacks && callbacks.length>0) {
                      var indexOfMetaHandlerToClear = callbacks.indexOf(metaevent_cb);
                      if(metaevent_cb && callbacks && callbacks.length<=1 && indexOfMetaHandlerToClear != -1) delete client.metaeventHandlers[subscriptionId];
                      else if(indexOfMetaHandlerToClear != -1) client.metaeventHandlers[subscriptionId].splice(indexOfMetaHandlerToClear,1);
                  }
                  
                  promise.resolve(requestId,subscriptionId);
                  delete client.pendingRequests[requestId];
                  //delete client.subscriptionsById[subscriptionId];
                  //delete client.subscriptionsByTopicAndOptions[topicAndOptionsKey];              
              }
              
          } else if(arr[0] == 17) {  // PUBLISHED
              var requestId = arr[1];
              var publicationId = arr[2];
              var promise = client.pendingRequests[requestId];
              promise.resolve(requestId, publicationId);
              delete client.pendingRequests[requestId];
              
          } else if(arr[0] == 36) {  // EVENT
              var subscriptionId = arr[1];
              var publicationId = arr[2];
              var details = arr[3];
              var payload   = (arr.length>4)? arr[4] : null;
              var payloadKw = (arr.length>5)? arr[5] : null;
              var topicURI = client.subscriptionsById[subscriptionId];
              if(details && details.topic) topicURI = details.topic;
              
              if(details && details.metatopic) {
                var metatopic = details.metatopic;
                var metaevent = details;

                if(client.metaeventHandlers[subscriptionId]) {
                    client.metaeventHandlers[subscriptionId].forEach(function(callback) {
                        callback.call(client, topicURI, metatopic, metaevent);
                    });
                } 
                
              } else {
                if(client.eventHandlers[subscriptionId]) {
                    client.eventHandlers[subscriptionId].forEach(function(callback) {
                        callback.call(client, publicationId, details, null, payload, payloadKw, topicURI);                
                    });
                } 
              }
              
          } else if(arr[0] == 65) {  // REGISTERED
              var requestId = arr[1];
              var registrationId = arr[2];
              if(requestId && client.pendingRequests[requestId]) {
                  var promise = client.pendingRequests[requestId][0];
                  var procedureURI = client.pendingRequests[requestId][1];
                  var callback = client.pendingRequests[requestId][2];
                  client.rpcRegistrationsById[registrationId] = procedureURI;
                  client.rpcRegistrationsByURI[procedureURI] = registrationId;
                  client.rpcHandlers[registrationId] = callback;
                  promise.resolve(requestId, registrationId);
              }

          } else if(arr[0] == 67) {  // UNREGISTERED                
              var requestId = arr[1];
              if(requestId && client.pendingRequests[requestId]) {
                  var registrationId = arr[2];
                  var promise = client.pendingRequests[requestId][0];
                  var procedureURI = client.pendingRequests[requestId][1];
                  promise.resolve(requestId,registrationId);
                  delete client.rpcHandlers[registrationId];
                  delete client.pendingRequests[requestId];                  
                  //delete client.rpcRegistrationsById[registrationId];
                  //delete client.rpcRegistrationsByURI[procedureURI];              
              }
              
          } else if(arr[0] == 68) {  // INVOCATION
              var requestId = arr[1];
              try {
                var registrationId = arr[2];
                var details = arr[3];
                var arguments = arr[4];
                var argumentsKw = arr[5];
                if(requestId && client.rpcHandlers[registrationId]) {
                  var callback = client.rpcHandlers[registrationId];
                  
                  var resultKw = {};
                  var result = callback(arguments, argumentsKw, details);
                  if(isFinite(result)) {
                      result = [result];
                  } else if(typeof(result) == "string") {
                      result = [result];
                  } else if(!(result instanceof Array)) {
                      resultKw = result;
                      result = [];
                  }
                  
                  var arr = [];
                  arr[0] = 70;  // YIELD (=INVOCATION_RESULT)
                  arr[1] = requestId;
                  arr[2] = {};  // options
                  arr[3] = result;
                  arr[4] = resultKw;
                  client.send(JSON.stringify(arr)); 
                }
                
              } catch(e) {
                var arr = [];
                arr[0] = 4;  // ERROR
                arr[1] = requestId;
                arr[2] = {}; // details
                arr[3] = "wamp.error.invalid_argument";
                arr[4] = [];
                arr[5] = e;
                client.send(JSON.stringify(arr)); 
              }

          } else {
              client.debug("Server message not recognized: " + e.data);
          }

        };
      }

  },
  
  listApps: function(filterByDomain, callback) {
      var msg = Object();
      if(filterByDomain) msg.domain = document.domain.toString();

      this.call("wgs.list_apps", [], msg).then(
        function(id,details,errorURI,result,resultKw) {
          callback(id,details,errorURI,result,resultKw);
        }, 
        function(id,details,errorURI,result,resultKw) {
          callback(id,details,errorURI,result,resultKw);
        });
  },
  
  listGroups: function(appId, scope, state, callback) {
      this.call("wgs.list_groups", [appId, state, scope]).then(callback, callback);
  },

  newApp: function(name, domain, version, maxScores, descScoreOrder, min, max, delta, observable, dynamic, alliances, ai_available, roles, callback) {
      var msg = Object();
      msg.name = name;
      msg.domain = domain;
      msg.version = version;
      msg.max_scores = maxScores;
      msg.desc_score_order = descScoreOrder;
      msg.min = min;
      msg.max = max;
      msg.delta = delta;
      msg.observable = observable;      
      msg.dynamic = dynamic;
      msg.alliances = alliances;
      msg.ai_available = ai_available;
      msg.roles = roles;
      
      this.call("wgs.new_app", msg).then(callback, callback);
  },

  deleteApp: function(appId, callback) {
      var msg = Object();
      msg.app = appId;
          
      this.call("wgs.delete_app", msg).then(callback, callback);
  },  
  
  _update_group_users: function(id,details,errorURI,payload, payloadKw, topicURI) {
      var client = this;
      if(payloadKw.connections) {
          client.groups[payloadKw.gid] = new Object();
          client.groups[payloadKw.gid].min = payloadKw.min;
          client.groups[payloadKw.gid].max = payloadKw.max;
          client.groups[payloadKw.gid].delta = payloadKw.delta;
          client.groups[payloadKw.gid].members = payloadKw.members;
          client.groups[payloadKw.gid].connections = new Array();
          payloadKw.connections.forEach(function(con) { 
              client.groups[payloadKw.gid].connections[con.sid] = con;
          });
      } else if(payloadKw.cmd == "user_joined" || payloadKw.cmd == "group_updated") {
          var gid = payloadKw.gid;
          
          if(isFinite(payloadKw.slot)) payloadKw.members = [ payloadKw ];
          else if(payloadKw.members) client.groups[gid].members = new Array();

          if(payloadKw.cmd == "user_joined") client.groups[gid].connections[payloadKw.sid] = payloadKw;
          
          if(payloadKw.members) {
              payloadKw.members.forEach(function(item) {
                  if(isFinite(item.sid) > 0) client.groups[gid].connections[item.sid] = item;
                  if(isFinite(item.slot)) client.groups[gid].members[item.slot] = item;
              });
          }
      } else if(payloadKw.cmd == "user_detached") {
          delete client.groups[payloadKw.gid].connections[payloadKw.sid];
          if(payloadKw.members) {
              payloadKw.members.forEach(function(item) {
                  if(isFinite(item.slot)) client.groups[payloadKw.gid].members[item.slot] = item;
              });
          }
      }
  },  
  
  openGroup: function(appId, gid, options, callback) {
      var client = this;
      var args = Array();
      args[0] = appId? appId : null;
      args[1] = gid? gid : null;
      args[2] = options;

      this.call("wgs.open_group", args).then(function(id,details,errorURI,result,resultKw) {
          client.subscribe("wgs.group_event:" + resultKw.gid, client._update_group_users, null, {} );
          client._update_group_users(id,details,errorURI,result,resultKw);
          callback(id,details,errorURI,result,resultKw);
      }, callback);
  },
  
  exitGroup: function(gid, callback) {
      var client = this;
      this.call("wgs.exit_group", gid).then(callback, callback);
      this.unsubscribe("wgs.group_event:" + gid, client._update_group_users, null, {});
      delete this.groups[gid];
  },
          
  getGroupMinMembers: function(gid) {          
    return this.groups[gid].min;
  },          

  getGroupMaxMembers: function(gid) {          
    return this.groups[gid].max;
  },          

  getGroupConnections: function(gid) {
      return this.groups[gid].connections;
  },
          
  getGroupMembers: function(gid) {
      return this.groups[gid].members;
  },
  
  getGroupMember: function(gid,slot) {
      return this.groups[gid].members[slot];
  },
          
  updateGroup: function(appId, gid, state, data, automatch, hidden, observable, dynamic, alliances, callback) {
      var client = this;
      var msg = Object();
      msg.app = appId;
      msg.gid = gid;
      msg.automatch = automatch;
      msg.hidden = hidden;
      msg.observable = observable;
      msg.dynamic = dynamic;
      msg.alliances = alliances;      
      if(state) msg.state = state;
      if(data) msg.data  = data;
     
      this.call("wgs.update_group", msg).then(function(id,details,errorURI,result,resultKw) { 
          client._update_group_users(id,details,errorURI,result,resultKw);
          callback(id,details,errorURI,result,resultKw);
      }, 
      function(id,details,errorURI,result,resultKw) { 
          client._update_group_users(id,details,errorURI,result,resultKw);
          callback(id,details,errorURI,result,resultKw);
      } );
  },

  updateMember: function(appId, gid, state, slot, sid, usertype, user, role, team, callback) {
      var client = this;      
      var msg = Object();
      msg.app = appId;
      msg.gid = gid;
      msg.state = state;
      if(!isNaN(slot)) {
        msg.slot = slot;
        msg.sid  = sid;
        msg.user = user;
        msg.role = role;
        msg.team = team;
        msg.type = usertype;
      }
      this.call("wgs.update_member", msg).then(
        function(id,details,errorURI,result,resultKw) { 
            client._update_group_users(id,details,errorURI,result,resultKw);
            callback(id,details,errorURI,result,resultKw);
        }, 
        function(id,details,errorURI,result,resultKw) { 
            client._update_group_users(id,details,errorURI,result,resultKw);
            callback(id,details,errorURI,result,resultKw) 
        } );
  },
  
  sendGroupMessage: function(gid, data, callback) {
      var args = Array();
      args[0] = gid;
      args[1] = data;
      
      this.call("wgs.send_group_message", args).then(callback, callback);
  },

  sendTeamMessage: function(gid, data, callback) {
      var args = Array();
      args[0] = gid;
      args[1] = data;
      
      this.call("wgs.send_team_message", args).then(callback, callback);
  },
          
  topicMatchesWithPattern: function(topicURI,pattern) {
      topicURI = topicURI.replace("..", ".*");
      var re = new RegExp(pattern);
      return topicURI.match(re);
  },
  
}
