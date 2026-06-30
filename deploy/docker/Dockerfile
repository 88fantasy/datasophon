FROM eclipse-temurin:17-jre
LABEL "author"="88fantasy"
RUN mkdir -p /opt/datasophon
COPY ./bin/ /opt/datasophon/bin/
COPY ./conf/ /opt/datasophon/conf/
COPY ./lib/ /opt/datasophon/lib/
COPY ./jmx/ /opt/datasophon/jmx/
RUN chmod +x /opt/datasophon/bin/datasophon-api.sh
ENV TZ=Asia/Shanghai
EXPOSE 8081
WORKDIR /opt/datasophon
ENTRYPOINT ["/bin/sh", "-c", "bin/datasophon-api.sh start"]
