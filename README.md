# epa4all

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

## Running IT tests:

```shell script
./mvnw clean verify -Pdev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/epa-connector-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- Quarkus CXF ([guide](https://quarkiverse.github.io/quarkiverse-docs/quarkus-cxf/dev/reference/extensions/quarkus-cxf.html)): Core capabilities for implementing SOAP clients and JAX-WS services
- Quarkus CXF WS-ReliableMessaging ([guide](https://quarkiverse.github.io/quarkiverse-docs/quarkus-cxf/dev/reference/extensions/quarkus-cxf-rt-ws-rm.html)): Consume and produce web services with Web Services Reliable Messaging (WS-ReliableMessaging, WSRM)
- Quarkus CXF WS-Security ([guide](https://quarkiverse.github.io/quarkiverse-docs/quarkus-cxf/dev/reference/extensions/quarkus-cxf-rt-ws-security.html)): Consume and produce web services with Web Services Security (WS-Security, WSS)
- Citrus ([guide](https://github.com/christophd/citrus-demo-quarkus)): Add Citrus support to your Quarkus tests. Citrus is an Open Source Java integration testing framework supporting a wide range of message protocols and data formats (Kafka, Http REST, JMS, TCP/IP, SOAP, FTP/SFTP, XML, Json, and more)
- Quarkus CXF Transports HTTP Async ([guide](https://quarkiverse.github.io/quarkiverse-docs/quarkus-cxf/dev/reference/extensions/quarkus-cxf-rt-transports-http-hc5.html)): Implement async SOAP Clients using Apache HttpComponents HttpClient 5
