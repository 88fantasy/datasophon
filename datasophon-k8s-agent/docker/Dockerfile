FROM eclipse-temurin:21-jre-ubi9-minimal


ENV AGENT_HOME=/opt/datasophon-k8s-agent

ADD datasophon-k8s-agent-docker.tar.gz  $AGENT_HOME

# config file path is $AGENT_HOME/conf/bootstrap.yaml
# config file path is $AGENT_HOME/conf/common.properties


WORKDIR $AGENT_HOME
ENV IS_DOCKER=true

EXPOSE 12552

# debug port
EXPOSE 30105

#CMD ["sleep", "10000"]
ENTRYPOINT ["bin/datasophon-k8sagent.sh", "start"]