#!/bin/bash
export JAVA=java
if [ ${JAVA_HOME} ]; then
  export JAVA=$JAVA_HOME/bin/java
fi

$JAVA -Xmx128m -Djava.util.logging.config.file=logging.properties -jar target/WgsAPI-2.0-SNAPSHOT.jar wgs_federated_8082.properties
