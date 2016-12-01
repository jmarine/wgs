FROM openjdk:8-jdk

# Install Maven building tool for Java applications
ARG MAVEN_VERSION=3.3.9
ENV MAVEN_HOME=/opt/apache-maven-$MAVEN_VERSION
ENV PATH ${PATH}:$MAVEN_HOME/bin

RUN mkdir -p $MAVEN_HOME && curl -fsSL http://apache.osuosl.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar -xzC $MAVEN_HOME --strip-components=1 

# Deploy application files
COPY WgsAPI /opt/wgs
COPY WgsAPI/wgs_docker_master.properties /etc/opt/wgs/wgs_master.properties
COPY WgsAPI/wgs_docker_federated.properties /etc/opt/wgs/wgs_federated.properties
COPY WgsAPI/derby.properties /etc/opt/wgs/derby.properties
COPY WgsAPI/logging.properties /etc/opt/wgs/logging.properties
COPY WgsWebApp/src/main/webapp /var/www/html

# Build application JAR
WORKDIR /opt/wgs
RUN mvn -Duser.home=/opt/wgs install

# Define application directories/permissions and data volumes
RUN mkdir -p /var/opt/wgs 
RUN chown -R www-data:www-data /opt/wgs
RUN chown -R www-data:www-data /etc/opt/wgs
RUN chown -R www-data:www-data /var/opt/wgs
RUN chown -R www-data:www-data /var/www/html

# Disable some volumes for Openshift (that mounts them as empty directories) 
#VOLUME ["/var/opt/wgs", "/etc/opt/wgs", "/var/www/html"]
VOLUME ["/var/opt/wgs"]

# Run WGS server
# (we don't want to exit on key press, like in OpenShift environments)
ENV OPENSHIFT_APP_NAME wgs
ENV WGS_NODE_TYPE master
ENV WGS_MASTER_NODE wgs-master

# Expose application ports (don't require root user)
EXPOSE 8080/tcp
EXPOSE 8443/tcp
EXPOSE 15270/tcp

# Run as unprivileged user
USER www-data

# Set working directory (it must be the parent directory of Derby databases)
WORKDIR /var/opt/wgs

# Define default command.
CMD java -Xmx128m -Djava.util.logging.config.file=/etc/opt/wgs/logging.properties -Dderby.drda.startNetworkServer=true -Dderby.drda.host=0.0.0.0 -Dderby.drda.portNumber=15270 -jar /opt/wgs/target/WgsAPI-2.0-SNAPSHOT.jar /etc/opt/wgs/wgs_$WGS_NODE_TYPE.properties
