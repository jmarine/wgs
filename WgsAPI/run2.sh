#!/bin/bash
export JAVA=java
if [ ${JAVA_HOME} ]; then
  export JAVA=$JAVA_HOME/bin/java
fi

$JAVA -Xmx128m -Djava.util.logging.config.file=logging.properties -jar dist/WgsAPI.jar wgs2.properties
