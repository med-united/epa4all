FROM azul/zulu-openjdk-alpine:21-jre-headless-latest

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

RUN apk add --no-cache curl bash tini ca-certificates libc6-compat tcpdump less iproute2-ss logger sudo nano socat \
    && mkdir /opt/epa4all \
    && mkdir /opt/epa4all/app \
    && mkdir /opt/epa4all/promtail \
    && mkdir /opt/epa4all/lib \
    && mkdir /opt/epa4all/lib/boot \
    && mkdir /opt/epa4all/lib/main \
    && mkdir /opt/epa4all/certs \
    && mkdir /opt/epa4all/webdav \
    && mkdir /opt/epa4all/frontend \
    && mkdir /opt/epa4all/config \
    && mkdir /opt/epa4all/quarkus \
    && mkdir /opt/epa4all/secret \
    && mkdir /opt/epa4all/tls \
    && mkdir /opt/epa4all/ig-schema \
    && mkdir /opt/epa4all/config/konnektoren \
    && mkdir /opt/epa4all/config/konnektoren/8588 \
    && chown -R 1001:root /opt/epa4all \
    && chmod -R "g+rwX" /opt/epa4all \
    && echo "securerandom.source=file:/dev/urandom" >> /usr/lib/jvm/default-jvm/lib/security/java.security

RUN ls -la /usr/local/bin

WORKDIR /usr/local/bin

ARG TARGETPLATFORM
RUN case "${TARGETPLATFORM}" in \
    linux/amd64) ARCH='amd64' ;; \
    linux/arm64) ARCH='arm64' ;; \
    *) echo "Unsupported platform ${TARGETPLATFORM}"; exit 1 ;; \
    esac && \
    curl -O -L "https://github.com/grafana/loki/releases/download/v3.2.0/promtail-linux-${ARCH}.zip" \
    && unzip "promtail-linux-${ARCH}.zip" \
    && mv promtail-linux-${ARCH} /usr/local/bin/promtail \
    && chmod a+x /usr/local/bin/promtail

COPY --chown=1001 api-xds/src/main/resources/ig-schema/* /opt/epa4all/ig-schema/
COPY --chown=1001 rest-server/frontend/ /opt/epa4all/frontend/
COPY --chown=1001 tls/epa-certs/*.pem /opt/epa4all/certs
COPY --chown=1001 linux-service/run.sh /opt/epa4all
COPY --chown=1001 tls/server/key-store/keystore.p12 /opt/epa4all/tls
COPY --chown=1001 tls/server/trust-store/truststore.p12 /opt/epa4all/tls
COPY --chown=1001 rest-server/target/quarkus-app/app/* /opt/epa4all/app
COPY --chown=1001 rest-server/target/quarkus-app/lib/boot/* /opt/epa4all/lib/boot
COPY --chown=1001 rest-server/target/quarkus-app/lib/main/* /opt/epa4all/lib/main
COPY --chown=1001 rest-server/target/quarkus-app/quarkus/* /opt/epa4all/quarkus/
COPY --chown=1001 rest-server/target/quarkus-app/quarkus-run.jar /opt/epa4all/
COPY --chown=1001 rest-server/src/main/resources/application.properties /opt/epa4all/config/application.properties
COPY --chown=1001 rest-server/config/konnektoren/8588/user.properties /opt/epa4all/config/konnektoren/8588/user.properties
COPY --chown=1001 production_deployment/promtail.yaml /opt/epa4all/promtail

ENV JAVA_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5045"
ENV QUARKUS_CONFIG_LOCATIONS=/opt/epa4all/config

RUN chmod "+x" /opt/epa4all/run.sh

RUN for cert in /opt/epa4all/certs/*.pem; do \
  keytool -importcert -file "$cert" -alias "$(basename "$cert" .pem)" -cacerts -storepass changeit -noprompt; \
done

WORKDIR /opt/epa4all

RUN ls -la /opt/epa4all
RUN ls -la /opt/epa4all/app
RUN ls -la /opt/epa4all/lib
RUN ls -la /opt/epa4all/lib/boot
RUN ls -la /opt/epa4all/lib/main
RUN ls -la /opt/epa4all/quarkus
RUN ls -la /usr/bin/java

USER 1001

ENTRYPOINT ["/sbin/tini", "--", "/bin/bash", "/opt/epa4all/run.sh"]

