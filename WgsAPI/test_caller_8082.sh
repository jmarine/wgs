#java -Xmx64m -Dfile.encoding=UTF-8 -Djava.util.logging.config.file=logging.properties -classpath lib/javax.websocket-api-1.1.jar:lib/grizzly-framework-2.3.19.jar:lib/grizzly-http-2.3.19.jar:lib/tyrus-client-1.9.jar:lib/tyrus-container-grizzly-client-1.9.jar:lib/tyrus-core-1.9.jar:lib/tyrus-spi-1.9.jar:lib/msgpack-core-0.7.0-p7.jar:lib/javax.json-1.0.4.jar:lib/jms.jar:lib/jdeferred-core-1.2.3.jar:lib/slf4j-api-1.7.5.jar:lib/slf4j-simple-1.7.5.jar:build/classes org.wgs.wamp.client.test.WampClientCallerTest

export MAVEN_HOME=/opt/apache-maven-3.3.9
export MAVEN_OPTS="-Xmx64m -Dfile.encoding=UTF-8 -Djava.util.logging.config.file=logging.properties"
$MAVEN_HOME/bin/mvn exec:java -Dexec.mainClass="org.wgs.wamp.client.test.WampClientCallerTest"


