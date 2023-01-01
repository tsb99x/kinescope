FROM eclipse-temurin:17-jre-alpine

ARG VERSION

LABEL org.opencontainers.image.source=https://github.com/tsb99x/kinescope
LABEL org.opencontainers.image.authors="Anton Muravev"
LABEL org.opencontainers.image.description="Kinescope"
LABEL org.opencontainers.image.licenses=MIT
LABEL org.opencontainers.image.version=$VERSION

EXPOSE 8888

WORKDIR /app
COPY build/distributions/kinescope-${VERSION}.tar .
RUN tar -xvf kinescope-${VERSION}.tar --strip-components 1 \
    && rm kinescope-${VERSION}.tar
USER nobody

ENTRYPOINT ["sh", "-c"]
CMD ["exec bin/kinescope run tsb99x.kinescope.Application"]
