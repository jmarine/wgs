export MAVEN_HOME=/opt/apache-maven-3.1.1
export MAVEN_OPTS="-Xmx64m -Dfile.encoding=UTF-8 -Djava.util.logging.config.file=logging.properties"
$MAVEN_HOME/bin/mvn exec:java -Dexec.mainClass="org.wgs.wamp.client.test.WampClientTest"
