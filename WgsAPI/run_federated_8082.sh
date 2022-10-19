#!/bin/bash
export JAVA=java
if [ ${JAVA_HOME} ]; then
  export JAVA=$JAVA_HOME/bin/java
fi

$JAVA -Xmx128m -Djava.util.logging.config.file=logging.properties -jar target/WgsAPI-3.0-SNAPSHOT.jar wgs_federated_8082.properties

# Maven command:
# $MAVEN_HOME/bin/mvn exec:exec -Dexec.executable=java -Dexec.mainClass="org.wgs.util.Server" -Dexec.args="-Xmx128m -Djava.util.logging.config.file=logging.properties -cp %classpath org.wgs.util.Server wgs_federated_8082.properties"

