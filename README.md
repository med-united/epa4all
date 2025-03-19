# epa4all Video

[![Medication list from the epa4all implementation](https://img.youtube.com/vi/fryBy0tj31k/0.jpg)](https://www.youtube.com/watch?v=fryBy0tj31k)

## Screenshots
![Patienten](doc/Screenshots/Patienten.png?raw=true "Patienten")
![Patient](doc/Screenshots/Patient.png?raw=true "Patient")
![Medikationsliste](doc/Screenshots/Medikationsliste.png?raw=true "Medikationsliste")
![Medikationsplan](doc/Screenshots/Medikationsplan.png?raw=true "Medikationsplan")
![Dokument](doc/Screenshots/Dokument.png?raw=true "Dokument")
![Medication KI](doc/Screenshots/Medication_KI.png?raw=true "Medication KI")


## Testing Status

| Test Case  | System | KVNR | Status |
|------------|--------|------|--------|
| Lookup Record | IBM  | X110486750 | Works |
| Lookup Record | RISE | X110485291 | Works |
| VAU | IBM  | X110486750 | Works |
| VAU | RISE | X110485291 | Works |
| OIDC Flow | IBM  | X110486750 | Works |
| OIDC Flow | RISE | X110485291 | Works |
| Entitlemenets (setEntitlementPs) | IBM  | X110486750 | Works |
| Entitlemenets (setEntitlementPs) | RISE | X110485291 | Works |
| XDS Service | IBM | X110486750 | Works |
| XDS Service (documentRepositoryRetrieveDocumentSet) | IBM | X110486750 | Works |
| XDS Service (documentRepositoryRetrieveDocumentSet) | RISE | X110485291 | Works |
| XDS Service (documentRepositoryProvideAndRegisterDocumentSetB) | IBM | X110486750 | Works  |
| XDS Service (documentRepositoryProvideAndRegisterDocumentSetB)  | RISE | X110485291 | Works |
| Medication PDF | IBM | X110486750 | Works |
| Medication HTML | IBM | X110486750 | Works |
| Fhir Raw | IBM | X110486750 | Works |
| Medication PDF | RISE | X110485291 | Works |
| Medication HTML | RISE | X110485291 | Works |
| Fhir Raw | RISE | X110485291 | Works |

## Description


This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
git clone --recurse-submodules https://github.com/med-united/epa4all
cd epa4all
./mvnw -DskipTests -T1C clean install
cd rest-server
../mvnw quarkus:dev
```

Open http://localhost:8090/frontend/

## Running IT tests:

```shell script
./mvn clean verify -DskipIT=false
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

## JCR support

- JCR repository operates with nodes, their types and properties. There are standard node types related to webdav - **nt:folder** and **nt:file**. JCR repository supports multiple workspaces. We map telematikId to separate workspace. Every workspace has a root node, so we map `/webdav/<telematikId>` folder to root node of that workspace. Root node JCR access path has built in JCR identifier: **jcr:root**. Thus, the JCR workspace root node `/3-SMC-B-Testkarte--883110000147807/jcr:root` contains many system properties and nested system nodes, making it inconvenient to skip them while responding to WebDAV requests. To solve this, an additional top level **nt:folder** `rootFolder` node was added to workspace root. Therefore, to reach the root node that contains all KVNR nodes, the following JCR path should be used:
`/3-SMC-B-Testkarte--883110000147807/jcr:root/rootFolder`

    Same approach is used for **nt:file** nodes. They have built in child node **jcr:content** with type **nt:resource**. Standard type **nt:resource** has fixed properties set which includes:

  * **jcr:data** - binary content of any file
  * **jcr:mimeType** - file mime type
  * **jcr:encoding** - file content encoding
    When a file is added to the repository, these properties are populated, content of the **jcr:data** is extracted according to MIME type and indexed using `node scope` that’s why we perform a fulltext search widely, e.g r.*:
```SQL
SELECT * FROM [nt:resource] as r WHERE CONTAINS(r.*, '%s')
```  
> Note: `CONTAINS(r.[jcr:data], '%s')` won’t work

- epa4all has its own properties set: firstname, lastname, birthday, displayname, validto, getlastmodified, smcb, entryuuid. They are included into JCR mixin. The concept of a mixin is similar to interface, so you can add additional mixin types into JCR standard type and therefore extend that JCR type with additional properties. Another way to have additional properties is no create child node with custom type but mixin looks more convenient. In addition, epa4all introduces its own namespace to not interfere with system properties:

```java
String EPA_NAMESPACE_PREFIX = "epa";
String EPA_NAMESPACE_URI = "https://www.service-health.de/epa";
```

Thus, epa mixin names must be always prefixed with namespace prefix, at moment only one epa mixin is added with name **epa:custom**. Its properties are firstname, lastname, birthday, displayname, validto, getlastmodified, smcb, entryuuid. The behaviour of epa properties is defined in the interface MixinProp (custom indexing behaviour can be added in rest-server/resources/jcr/indexing-config.xml). Property  **isFulltext()** defines if particular property will be fulltext indexed. The fulltext search through nt:folder nodes by lastname will look like:
```sql
SELECT f.* FROM [nt:folder] as f
WHERE CONTAINS(f.[epa:lastname], '%s')
```
> epa:lastname property was indexed with nodeScope=false

- The actual configuration of epa4all mixins is supported on runtime and based on implementations of the Mixin interface found on classpath. When service is starting, it checks current JCR repository mixins and then compare with actual epa mixins from runtime. Unused mixins (with their properties) and modified mixins will be removed from JCR repository, new mixins will be added and applied to nodes defined in the application.properties:
```properties
jcr.mixin.config."nt\:folder"=epa:custom
jcr.mixin.config."nt\:file"=epa:custom
```

- JCR repository is file based for epa4all, property **jcr.repository.home** defines where in filesystem the root folder will be located. At moment is is `config/repository`. This means that for docker deployment we need to keep repository home in the volume. 
>Note: if some issue with existing repository is detected at startup then home folder will be removed and recreated from scratch. The property jcr.repository.reinit.attempts is introduced.

- At the moment there are two content management systems in the epa4all: legacy one is webdav folder where actual medication files are stored and `webdav-jaxrs` library provides WEBDAV access to that folder and Java Content Repository (Jackrabbit implementation) which behaves as downstream read-only system at the moment. So, when we write new file into webdav folder, we notify jcr repository about that file. Jcr repository creates a new node, indexes its properties, and so on.
  legacy cms has `/webdav` path and jcr repository has `/webdav2` path.

- Since the Undertow servlet container was added to the project, It has no longer been able to support the WebJars approach for the frontend module. Its import was removed from the REST server, and the frontend module with UI5-related JavaScript sources was extracted to the static folder. Thus, Quarkus now handles it using the Vert.x StaticHandler. Please check frontend/pom.xml; everything is now extracted to the `/rest-server/frontend` folder. We need to adapt this approach for Docker deployment.
