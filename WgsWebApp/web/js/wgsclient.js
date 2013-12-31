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
  subscriptionRequests: new Array(),
  subscriptionsByTopicAndOptions: new Array(),
  topicHandlers: new Array(),
  patternHandlers: new Array(),
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
      arr[2].roles.caller = {};
      
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
        var wildcards = false;
        if(!options) options = {};
        options.events = (event_cb != null);
        options.metaevents = (metaevent_cb != null);
        if(options.match && options.match.toLowerCase() == "prefix") {
            topic = topic + "*";
            options.match = "wildcard";
        }

        if(topic.indexOf("*") != -1) {
            wildcards = true;
            options.match = "wildcard";
            if(!this.patternHandlers[topic]) this.patternHandlers[topic] = [];
        } else {            
            if(!this.topicHandlers[topic]) this.topicHandlers[topic] = [];
        }
        
        if(!this.metaeventHandlers[topic]) this.metaeventHandlers[topic] = [];
        
        if(event_cb) {
            if(wildcards) this.patternHandlers[topic].push(event_cb);
            else this.topicHandlers[topic].push(event_cb);
        }
        if(metaevent_cb) {
            this.metaeventHandlers[topic].push(metaevent_cb);
        }
        if(!options.clientSideOnly) {
            var arr = [];
            arr[0] = 10;  // SUBSCRIBE
            arr[1] = this._newid();
            arr[2] = options;
            arr[3] = topic;      
            var topicAndOptionsKey = this._getTopicAndOptionsKey(topic,options);
            this.subscriptionRequests[arr[1]] = topicAndOptionsKey;
            this.send(JSON.stringify(arr));
        }
  },
  
  _getTopicAndOptionsKey: function(topicPattern,options)
  {
      if(!options) options = {};
      if(!options.match) options.match = "exact";
      return options.match.toLowerCase() + ":" + topicPattern;
  },
  
  getSubscriptionIdByTopicOptions: function(topicPattern, options)
  {
      var topicAndOptionsKey = this._getTopicAndOptionsKey(topicPattern, options);
      return this.subscriptionsByTopicAndOptions[topicAndOptionsKey];
  },
  
  unsubscribe: function(topic, event_cb, metaevent_cb, options) {
        var client = this;
        var indexOfEventHandlerToClear = -1;
        var indexOfMetaHandlerToClear = -1;
        var clientSideOnly = options && options.clientSideOnly;
        
        if(!options) options = {};
        if(options.match && options.match.toLowerCase() == "prefix") {
            topic = topic + "*";
            options.match = "wildcard";
        }
        
        var callbacks = this.topicHandlers[topic];
        if(topic.indexOf("*") != -1) {
            options.match = "wildcard";
            callbacks = this.patternHandlers[topic];
        }
        if(event_cb && callbacks) {
            indexOfEventHandlerToClear = callbacks.indexOf(event_cb);
        }

        if(metaevent_cb && this.metaeventHandlers[topic]) {
            indexOfMetaHandlerToClear = this.metaeventHandlers[topic].indexOf(metaevent_cb);
        }   
        
        
        var _clearHandlers = function() {
            var wildcards = (topic.indexOf("*") != -1);
            if(wildcards) {
                if(event_cb && callbacks && callbacks.length<=1 && indexOfEventHandlerToClear!=-1) delete client.patternHandlers[topic];
                else if(indexOfEventHandlerToClear != -1) client.patternHandlers.splice(indexOfEventHandlerToClear,1);
            } else {
                if(event_cb && callbacks && callbacks.length<=1 && indexOfEventHandlerToClear!=-1) delete client.topicHandlers[topic];
                else if(indexOfEventHandlerToClear != -1) client.topicHandlers.splice(indexOfEventHandlerToClear,1);
            }
            
            if(metaevent_cb && client.metaeventHandlers[topic] && client.metaeventHandlers[topic].length<=1 && indexOfMetaHandlerToClear != -1) delete client.metaeventHandlers[topic];
            else if(indexOfMetaHandlerToClear != -1) client.metaeventHandlers.splice(indexOfMetaHandlerToClear,1);
        }
            
        if(!this.metaeventHandlers[topic] || this.metaeventHandlers[topic].length == 0) {
            _clearHandlers();
        } else {
            // defer metaevent callback deletion, until #left/#error metatopic is received for the subscribed sessionId
            this.metaeventHandlers[topic].push(function(topic2, metatopic, metaevent) {
               if(topic == topic2 && metaevent && metaevent.sessionId == client.sid) {
                   _clearHandlers();
               }
            });
        }        
        
        if(!clientSideOnly) {
            // send unsubscribe message to server (when all eventHandlers or metaeventHandlers are cleared)
            var arr = [];
            arr[0] = 20;  // Unsubscribe message type
            arr[1] = this._newid();
            arr[2] = this.getSubscriptionIdByTopicOptions(topic,options);
            this.send(JSON.stringify(arr));
        }   

  },
  
  publish: function(topic, event, options) {
      var arr = [];
      arr[0] = 30;  // PUBLISH
      arr[1] = topic;
      arr[2] = event;
      arr[3] = (options) ? options : {};
      this.send(JSON.stringify(arr));
  }, 



  
  authreq: function(authKey, authExtra, callback) {
      if(!authExtra) authExtra = {};
      var args = []
      args[0] = authKey;
      this.call("wamp.cra.request", args, authExtra).then(callback,callback);      
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
  
  
  register: function(user, password, email, onstatechange) {
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
              var subscriptionId = arr[2];
              var topicAndOptionsKey = client.subscriptionRequests[requestId];
              client.subscriptionsByTopicAndOptions[topicAndOptionsKey] = subscriptionId;
              
          } else if(arr[0] == 8 || arr[0] == 40) {  // EVENT
              var topicURI = arr[1];
              if(client.topicHandlers[topicURI]) {
                  client.topicHandlers[topicURI].forEach(function(callback) {
                      callback.call(client, arr[2], topicURI);                
                  });
              } else {

                  for(var pattern in client.patternHandlers) {
                      if(client.topicMatchesWithPattern(topicURI,pattern)) {
                          client.patternHandlers[pattern].forEach(function(callback) {
                             callback.call(client, arr[2], topicURI);                
                          });
                      }
                  }
                  // client.debug("topic not found: " + topic);
              }

          } else if(arr[0] == 41) {  // META-EVENT  (not supported by WAMP v1)
              var topicURI = arr[1];
              if(client.metaeventHandlers[topicURI]) {
                  client.metaeventHandlers[topicURI].forEach(function(callback) {
                      var metatopic = arr[2];
                      var metaevent = (arr.length > 2) ? arr[3] : null;
                      callback.call(client, topicURI, metatopic, metaevent);
                  });
              } else {
                  // client.debug("topic not found: " + topic);
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
          client.subscribe("wgs.group_event:" + resultKw.gid, client._update_group_users, null, { "clientSideOnly":true} );
          client._update_group_users(resultKw);
          callback(result,resultKw);
      }, callback);
  },
  
  exitGroup: function(gid, callback) {
      var client = this;
      this.call("wgs.exit_group", gid).then(callback, callback);
      this.unsubscribe("wgs.group_event:" + gid, client._update_group_users, null, { "clientSideOnly":true});
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
     
      this.call("wgs.update_group", msg).then(function(response) { 
          client._update_group_users(response);
          callback(response);
      }, 
      function(response) { 
          client._update_group_users(response);
          callback(response) 
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
      this.call("wgs.update_member", msg).then(function(response) { 
          client._update_group_users(response);
          callback(response);
      }, 
      function(response) { 
          client._update_group_users(response);
          callback(response) 
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
      topicURI = topicURI.replace("*", ".*");
      var re = new RegExp(pattern);
      return topicURI.match(re);
  },
  
}
