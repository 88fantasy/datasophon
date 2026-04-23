FROM eclipse-temurin:8-jdk-ubi10-minimal


ENV AGENT_HOME=/opt/datasophon-k8s-agent

WORKDIR $AGENT_HOME

COPY .  $AGENT_HOME

# config file path is $AGENT_HOME/conf/bootstrap.yaml
# ADD config $AGENT_HOME/config

ENV IS_DOCKER=true

EXPOSE 12552

# debug port
EXPOSE 30105

#CMD ["sleep", "10000"]
ENTRYPOINT ["bin/datasophon-k8sagent.sh", "start"]