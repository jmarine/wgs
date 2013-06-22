#!/bin/bash
export JAVA=java
if [ ${JAVA_HOME} ]; then
  export JAVA=$JAVA_HOME/bin/java
fi

$JAVA -Djava.util.logging.config.file=logging.properties -jar dist/WgsAPI.jar wgs.properties
