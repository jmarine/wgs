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
  calls: new Array(),
  rpcHandlers: new Array(),
  rpcRegistrationsById: new Array(),
  rpcRegistrationsByURI: new Array(),
  rpcRegistrationRequests: new Array(),
  rpcUnregistrationRequests: new Array(),
  subscriptionRequests: new Array(),
  unsubscriptionRequests: new Array(),
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
      arr[0] = 0;  // HELLO
      arr[1] = this.serverSID;
      arr[2] = {};  // HelloDetails
      arr[2].agent = "wgsclient/2.0.0";
      arr[2].roles = {};
      arr[2].roles.publisher = {};
      arr[2].roles.subscriber = {};
      arr[2].roles.caller = { "progressive" : 1 };
      arr[2].roles.callee = {};
      this.send(JSON.stringify(arr));
  },
          
  goodbye: function() {
      var arr = [];
      arr[0] = 2;   // Goodbye
      arr[1] = {};  // GoodbyeDetails
      this.send(JSON.stringify(arr));
  },
 
  call: function(cmd, args, argumentsKw, wampOptions) {
      var dfd = $.Deferred();
      var msg = [];
      msg[0] = 70;
      msg[1] = this._newid();
      msg[2] = (wampOptions!=null) ? wampOptions : {};
      msg[3] = cmd;
      msg[4] = (args!=null)? ((args instanceof Array)? args : [args] ) : [];
      msg[5] = (argumentsKw!=null) ? argumentsKw : {};

      this.calls[msg[1]] = dfd;
      this.send(JSON.stringify(msg));
      return dfd.promise();
  },  
  
  callCancel: function(callID, options) {
      var arr = [];
      arr[0] = 71;  // CALL_CANCEL
      arr[1] = callID;
      if(options) arr[2] = options;
      this.send(JSON.stringify(arr));      
  },
  
  subscribe: function(topic, event_cb, metaevent_cb, options) {
        if(!options) options = {};
        if(event_cb==null && metaevent_cb!=null) options.metaonly = 1;
        
        if(options.match && options.match.toLowerCase() == "prefix") {
            topic = topic + "..";
        }

        if(topic.indexOf("..") != -1) {
            options.match = "wildcard";
        }
        
        var dfd = $.Deferred();            
        var arr = [];
        arr[0] = 10;  // SUBSCRIBE
        arr[1] = this._newid();
        arr[2] = options;
        arr[3] = topic;      
        var topicAndOptionsKey = this._getTopicAndOptionsKey(topic,options);
        this.subscriptionRequests[arr[1]] = [dfd, topicAndOptionsKey, event_cb, metaevent_cb];
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
        if(options.match && options.match.toLowerCase() == "prefix") {
            topic = topic + "..";
            options.match = "wildcard";
        }
        
        if(topic.indexOf("..") != -1) {
            options.match = "wildcard";
        }

        var dfd = $.Deferred();            
        var arr = [];
        arr[0] = 20;  // Unsubscribe message type
        arr[1] = this._newid();
        arr[2] = this.getSubscriptionIdByTopicOptions(topic,options);
        var topicAndOptionsKey = this._getTopicAndOptionsKey(topic,options);
        this.unsubscriptionRequests[arr[1]] = [dfd, topicAndOptionsKey, event_cb, metaevent_cb];
        this.send(JSON.stringify(arr));
        
        return dfd.promise();
  },
  
  publish: function(topic, event, options) {
      var arr = [];
      arr[0] = 30;  // PUBLISH
      arr[1] = topic;
      arr[2] = event;
      arr[3] = (options) ? options : {};
      this.send(JSON.stringify(arr));
  }, 

  registerRPC: function(options, procedureURI, callback) {
        var dfd = $.Deferred();            
        var arr = [];
        arr[0] = 50;  // REGISTER
        arr[1] = this._newid();
        arr[2] = options;
        arr[3] = procedureURI;      
        this.rpcRegistrationRequests[arr[1]] = [dfd, procedureURI, callback];
        this.send(JSON.stringify(arr));
        return dfd.promise();      
  },
  
  unregisterRPC: function(options, procedureURI, callback) {
        var registrationId = this.rpcRegistrationsByURI[procedureURI];
        var dfd = $.Deferred();            
        var arr = [];
        arr[0] = 60;  // UNREGISTER
        arr[1] = this._newid();
        arr[2] = registrationId;
        this.rpcUnregistrationRequests[arr[1]] = [dfd, procedureURI, callback];
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
            client.authreq(user, authExtra, function(result,resultKw) {
                if(result && result.length > 0 && typeof(result[0]) == "string") {
                    var challenge = JSON.parse(result[0]);
                    password = CryptoJS.MD5(password).toString();
                    if(challenge.extra && challenge.extra.salt) {
                        var key = CryptoJS.PBKDF2(password, challenge.extra.salt, { keySize: challenge.extra.keylen / 4, iterations: challenge.extra.iterations, hasher: CryptoJS.algo.SHA256 });
                        password = key.toString(CryptoJS.enc.Base64);                        
                    }
                    var signature = CryptoJS.HmacSHA256(result[0], password).toString(CryptoJS.enc.Base64);
                    client.auth(signature, function(result,resultKw) {
                        if(resultKw && resultKw.valid) {
                            client.getUserInfo(function(result,resultKw) {
                                if(resultKw.valid) {
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
                function(result,resultKw) {
                    client.user = resultKw.user;
                    client.state = WgsState.AUTHENTICATED;
                    onstatechange(WgsState.AUTHENTICATED, resultKw);
                }, 
                function(result,resultKw) {
                    var errorCode = resultKw.errorURI;
                    onstatechange(WgsState.ERROR, errorCode);
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
                function(result,resultKw) {
                    //client.close();
                    callback(result,resultKw);
                }, 
                function(result,resultKw) {
                    client.close();
                    callback(result,resultKw);
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
                function(result,resultKw) {
                    client.close();
                    document.location.href = result;
                    //window.open(response + "&nonce=" + escape(client.sid), "_blank");
                }, 
                function(result,resultKw) {
                    var errorCode = resultKw.errorURI;
                    onstatechange(WgsState.ERROR, errorCode);
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
                function(result,resultKw) {
                    client.user = resultKw.user;
                    client.state = WgsState.AUTHENTICATED;
                    onstatechange(WgsState.AUTHENTICATED, resultKw);
                }, 
                function(result,resultKw) {
                    var errorCode = resultKw.errorURI;
                    onstatechange(WgsState.ERROR, errorCode);
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

          if (arr[0] == 0) {  // HELLO (WAMPv2)
              client.sid = arr[1];
              client.state = WgsState.WELCOMED;
              onstatechange(WgsState.WELCOMED);
          } else if (arr[0] == 72) {  // CALLPROGRESS
              var call = arr[1];
              if(client.calls[call]) {       
                  var progress = (arr && arr.length >= 3)? arr[2] : [];
                  var progressKw = (arr && arr.length >= 4)? arr[3] : {};
                  if(!progress) progress = [];
                  if(!progressKw) progressKw = {};
                  progress.valid = true;
                  progressKw.valid = true;
                  client.calls[call].notify(progress,progressKw);
              } else {
                  client.debug("call not found: " + call);
              }              
          } else if (arr[0] == 3 || arr[0] == 73) {  // CALLRESULT
              var call = arr[1];
              if(client.calls[call]) {       
                  var result = (arr && arr.length >= 3)? arr[2] : [];
                  var resultKw = (arr && arr.length >= 4)? arr[3] : {};
                  if(!result) result = [];
                  if(!resultKw) resultKw = {};
                  result.valid = true;
                  resultKw.valid = true;
                  client.calls[call].resolve(result,resultKw);
                  delete client.calls[call];
              } else {
                  client.debug("call not found: " + call);
              }
          } else if (arr[0] == 4 || arr[0] == 74) {  // CALLERROR
              var call = arr[1];
              if(client.calls[call]) {  
                  var args = {};
                  args.valid = false;
                  args.errorURI = arr[2];
                  if(arr.length >= 4) args.errorDesc = arr[3];
                  if(arr.length == 5) args.errorDetails = arr[4];
                  client.calls[call].reject(args);
                  delete client.calls[call];
              } else {
                  client.debug("call not found: " + call);
              }            
              
          } else if(arr[0] == 11) {  // SUBSCRIBED
              var requestId = arr[1];
              if(requestId && client.subscriptionRequests[requestId]) {
                  var subscriptionId = arr[2];
                  var promise = client.subscriptionRequests[requestId][0];
                  var topicAndOptionsKey = client.subscriptionRequests[requestId][1];
                  client.subscriptionsById[subscriptionId] = topicAndOptionsKey;
                  client.subscriptionsByTopicAndOptions[topicAndOptionsKey] = subscriptionId;
                  if(client.subscriptionRequests[requestId][2]) {
                    if(!client.eventHandlers[subscriptionId]) client.eventHandlers[subscriptionId] = [];
                    client.eventHandlers[subscriptionId].push(client.subscriptionRequests[requestId][2]);
                  }
                  if(client.subscriptionRequests[requestId][3]) {                  
                    if(!client.metaeventHandlers[subscriptionId]) client.metaeventHandlers[subscriptionId] = [];
                    client.metaeventHandlers[subscriptionId].push(client.subscriptionRequests[requestId][3]);
                  }
                  promise.resolve(requestId,subscriptionId);
                  delete client.subscriptionRequests[requestId];
              }
              
          } else if(arr[0] == 12) {  // SUBSCRIBE_ERROR
              var requestId = arr[1];
              var promise = client.subscriptionRequests[requestId][0];
              promise.reject(requestId);
              delete client.subscriptionRequests[requestId];

          } else if(arr[0] == 21) {  // UNSUBSCRIBED
              var requestId = arr[1];
              if(requestId && client.unsubscriptionRequests[requestId]) {
                  var subscriptionId = arr[2];
                  var promise = client.unsubscriptionRequests[requestId][0];
                  var topicAndOptionsKey = client.unsubscriptionRequests[requestId][1];
                  
                  var event_cb = client.unsubscriptionRequests[requestId][2];
                  var metaevent_cb = client.unsubscriptionRequests[requestId][3];
                  var callbacks = client.eventHandlers[subscriptionId];
                  if(callbacks && callbacks.length>0) {
                      var indexOfEventHandlerToClear = callbacks.indexOf(event_cb);
                      if(event_cb && callbacks && callbacks.length<=1 && indexOfEventHandlerToClear!=-1) delete client.eventHandlers[subscriptionId];
                      else if(indexOfEventHandlerToClear != -1) client.eventHandlers[subscriptionId].splice(indexOfEventHandlerToClear,1);
                  }
            
                  callbacks = client.metaeventHandlers[subscriptionId];
                  if(callbacks && callbacks.length>0) {
                      var indexOfMetaHandlerToClear = callbacks.indexOf(event_cb);
                      if(metaevent_cb && callbacks && callbacks.length<=1 && indexOfMetaHandlerToClear != -1) delete client.metaeventHandlers[subscriptionId];
                      else if(indexOfMetaHandlerToClear != -1) client.metaeventHandlers[subscriptionId].splice(indexOfMetaHandlerToClear,1);
                  }
                  
                  promise.resolve(requestId,subscriptionId);
                  delete client.unsubscriptionRequests[requestId];
                  //delete client.subscriptionsById[subscriptionId];
                  //delete client.subscriptionsByTopicAndOptions[topicAndOptionsKey];              
              }
              
          } else if(arr[0] == 22) {  // UNSUBSCRIBE_ERROR
              var requestId = arr[1];
              var promise = client.unsubscriptionRequests[requestId][0];
              promise.reject(requestId);
              delete client.unsubscriptionRequests[requestId];
                
          } else if(arr[0] == 8 || arr[0] == 40) {  // EVENT
              var subscriptionId = arr[1];
              var publicationId = arr[2];
              var details = arr[3];
              var event = arr[4];
              var topicURI = client.subscriptionsById[subscriptionId];
              if(details && details.topic) topicURI = details.topic;
              
              if(client.eventHandlers[subscriptionId]) {
                  client.eventHandlers[subscriptionId].forEach(function(callback) {
                      callback.call(client, event, topicURI);                
                  });
              } else {
                  // client.debug("subscription not found: " + topic);
              } 

          } else if(arr[0] == 41) {  // META-EVENT  (not supported by WAMP v1)
              var subscriptionId = arr[1];
              var publicationId = arr[2];
              var metatopic = arr[3];
              var metaevent = arr[4];
              var topicURI = null;
              
              if(client.metaeventHandlers[subscriptionId]) {
                  client.metaeventHandlers[subscriptionId].forEach(function(callback) {
                      callback.call(client, topicURI, metatopic, metaevent);
                  });
              } else {
                  // client.debug("subscription not found: " + topic);
              }            
              
          } else if(arr[0] == 51) {  // REGISTERED
              var requestId = arr[1];
              var registrationId = arr[2];
              if(requestId && client.rpcRegistrationRequests[requestId]) {
                  var promise = client.rpcRegistrationRequests[requestId][0];
                  var procedureURI = client.rpcRegistrationRequests[requestId][1];
                  var callback = client.rpcRegistrationRequests[requestId][2];
                  client.rpcRegistrationsById[registrationId] = procedureURI;
                  client.rpcRegistrationsByURI[procedureURI] = registrationId;
                  client.rpcHandlers[registrationId] = callback;
                  promise.resolve(requestId, registrationId);
              }

          } else if(arr[0] == 61) {  // UNREGISTERED                
              var requestId = arr[1];
              if(requestId && client.rpcUnregistrationRequests[requestId]) {
                  var registrationId = arr[2];
                  var promise = client.rpcUnregistrationRequests[requestId][0];
                  var procedureURI = client.rpcUnregistrationRequests[requestId][1];
                  promise.resolve(requestId,registrationId);
                  delete client.rpcHandlers[registrationId];
                  delete client.rpcUnregistrationRequests[requestId];                  
                  //delete client.rpcRegistrationsById[registrationId];
                  //delete client.rpcRegistrationsByURI[procedureURI];              
              }
              
          } else if(arr[0] == 80) {  // INVOCATION
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
                  arr[0] = 83;  // 
                  arr[1] = requestId;
                  arr[2] = result;
                  arr[3] = resultKw;
                  client.send(JSON.stringify(arr)); 
                }
                
              } catch(e) {
                var arr = [];
                arr[0] = 84;  // INVOCATION_ERROR
                arr[1] = requestId;
                arr[2] = "wamp.error.invalid_argument";
                arr[3] = e;
                client.send(JSON.stringify(arr)); 
              }

          } else {
              client.debug("Server message not recognized: " + message.type);
          }

        };
      }

  },
  
  listApps: function(filterByDomain, callback) {
      var msg = Object();
      if(filterByDomain) msg.domain = document.domain.toString();

      this.call("wgs.list_apps", [], msg).then(function(result,resultKw) {
          callback(result,resultKw);
        }, function(response) {
          callback(response);
        });
  },
  
  listGroups: function(appId, filters, callback) {
      var args = [ appId, filters ];
      this.call("wgs.list_groups", args).then(callback, callback);
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
  
  _update_group_users: function(msg, topicURI) {
      var client = this;
      if(msg.connections) {
          client.groups[msg.gid] = new Object();
          client.groups[msg.gid].min = msg.min;
          client.groups[msg.gid].max = msg.max;
          client.groups[msg.gid].delta = msg.delta;
          client.groups[msg.gid].members = msg.members;
          client.groups[msg.gid].connections = new Array();
          msg.connections.forEach(function(con) { 
              client.groups[msg.gid].connections[con.sid] = con;
          });
      } else if(msg.cmd == "user_joined" || msg.cmd == "group_updated") {
          var gid = msg.gid;
          
          if(isFinite(msg.slot)) msg.members = [ msg ];
          else if(msg.members) client.groups[gid].members = new Array();

          if(msg.cmd == "user_joined") client.groups[gid].connections[msg.sid] = msg;
          
          if(msg.members) {
              msg.members.forEach(function(item) {
                  if(item.sid.length > 0) client.groups[gid].connections[item.sid] = item;
                  if(isFinite(item.slot)) client.groups[gid].members[item.slot] = item;
              });
          }
      } else if(msg.cmd == "user_detached") {
          delete client.groups[msg.gid].connections[msg.sid];
          if(msg.members) {
              msg.members.forEach(function(item) {
                  if(isFinite(item.slot)) client.groups[msg.gid].members[item.slot] = item;
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

      this.call("wgs.open_group", args).then(function(result,resultKw) {
          client.subscribe("wgs.group_event:" + resultKw.gid, client._update_group_users, null, {} );
          client._update_group_users(resultKw);
          callback(result,resultKw);
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
     
      this.call("wgs.update_group", msg).then(function(result,resultKw) { 
          client._update_group_users(resultKw);
          callback(resultKw);
      }, 
      function(result,resultKw) { 
          client._update_group_users(resultKw);
          callback(resultKw) 
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
      this.call("wgs.update_member", msg).then(function(result,resultKw) { 
          client._update_group_users(resultKw);
          callback(resultKw);
      }, 
      function(result,resultKw) { 
          client._update_group_users(resultKw);
          callback(resultKw) 
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
