FROM eclipse-temurin:8-jdk-ubi10-minimal


ENV AGENT_HOME=/opt/datasophon-k8s-agent

ADD target/datasophon-k8s-agent-docker.tar.gz  $AGENT_HOME

# config file path is $AGENT_HOME/conf/bootstrap.yaml
# ADD config $AGENT_HOME/config

WORKDIR $AGENT_HOME
ENV IS_DOCKER=true

EXPOSE 12552

# debug port
EXPOSE 30105

#CMD ["sleep", "10000"]
ENTRYPOINT ["bin/datasophon-k8sagent.sh", "start"]