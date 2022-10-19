#!/bin/bash

export JAVA=java
if [ ${JAVA_HOME} ]; then
  export JAVA=$JAVA_HOME/bin/java
fi

$JAVA -Xmx128m -Djava.util.logging.config.file=logging.properties -Dderby.drda.host=127.0.0.1 -Dderby.drda.portNumber=15270 -jar target/WgsAPI-3.0-SNAPSHOT.jar wgs_standalone_8080.properties


# Maven command
# $MAVEN_HOME/bin/mvn exec:exec -Dexec.executable=java -Dexec.mainClass="org.wgs.util.Server" -Dexec.args="-Xmx128m -Djava.util.logging.config.file=logging.properties -Dderby.drda.host=127.0.0.1 -Dderby.drda.portNumber=15270 -cp %classpath org.wgs.util.Server wgs_standalone_8080.properties"


