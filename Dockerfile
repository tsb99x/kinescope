FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.source=https://github.com/tsb99x/kinescope
LABEL org.opencontainers.image.description="Kinescope"
LABEL org.opencontainers.image.licenses=MIT

ENV VERTICLE_FILE kinescope-0.1.0-fat.jar
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

COPY build/libs/$VERTICLE_FILE $VERTICLE_HOME/

WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec java -jar $VERTICLE_FILE"]
