FROM java:8-jdk-alpine
RUN mkdir -p /home/braid
RUN chgrp -R 0 /home/braid && chmod -R g+rwX /home/braid
ADD ./target/example-server.jar /home/braid/bin/example-server.jar
EXPOSE 8080
USER 999
#ENTRYPOINT ["ls"]
WORKDIR /home/braid
ENTRYPOINT ["/usr/bin/java", "-jar", "bin/example-server.jar"]