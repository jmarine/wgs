#!/bin/bash
export JAVA=java
if [ ${JAVA_HOME} ]; then
  export JAVA=$JAVA_HOME/bin/java
fi

$JAVA -Xmx128m -Djava.util.logging.config.file=logging.properties -Dderby.drda.host=127.0.0.1 -Dderby.drda.portNumber=15270 -jar dist/WgsAPI.jar wgs_standalone_8080.properties
