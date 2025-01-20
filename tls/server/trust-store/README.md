### SERVER

* Affected profiles: PU, mTLS

* create server key
```
openssl genrsa -out tls/server/trust-store/server-key.pem 4096
```

* prepare root CA
```
openssl req -new -x509 -days 9999 -keyout tls/server/trust-store/ca-key.pem \
 -out tls/server/trust-store/ca-crt.pem \
 -subj '/C=DE/ST=Brandenburg/L=Berlin/O=Service-Health/OU=EPA/CN=RootCA'
```

* prepare CSR (Certificate Signing Request)
```
openssl req -new -key tls/server/trust-store/server-key.pem  -out tls/server/trust-store/server.csr -config <(cat << EOF
[ req ]
default_md         = sha512
distinguished_name = req_distinguished_name
req_extensions     = req_ext
prompt             = no

[ req_distinguished_name ]
countryName = "DE"
stateOrProvinceName = "Brandenburg"
localityName  = "Berlin"
organizationName  = "Service-Health"
organizationalUnitName = "EPA"
commonName = "ServerCA"

[ req_ext ]
basicConstraints = CA:FALSE
keyUsage = nonRepudiation, digitalSignature, keyEncipherment
subjectAltName = @alt_names

[ alt_names ]
DNS.1 = epa4all
DNS.2 = server.localhost
EOF
)
```

* sign the server CSR from new CA
```
openssl x509 -req -days 9999 -in tls/server/trust-store/server.csr \
  -CA tls/server/trust-store/ca-crt.pem -CAkey tls/server/trust-store/ca-key.pem \
  -CAcreateserial -out tls/server/trust-store/server-crt.pem
```

* OPTIONAL: p12 keystore for server key&certificate
```
openssl pkcs12 -export -in tls/server/trust-store/server-crt.pem -inkey tls/server/trust-store/server-key.pem -out tls/server/trust-store/server.p12 -name epa4all -CAfile tls/server/trust-store/ca-crt.pem -caname selfsignedroot
```

* OPTIONAL: convert to JKS
```
keytool -importkeystore -srckeystore tls/server/trust-store/server.p12 -srcstoretype PKCS12 \ 
-destkeystore tls/server/trust-store/server.jks -deststoretype JKS -alias epa4all \
-deststorepass changeit -destkeypass changeit
```

* import root & server CA into truststore
```
keytool -import -trustcacerts -file tls/server/trust-store/ca-crt.pem \
  -alias rootCA -keystore tls/server/trust-store/truststore.p12 -storepass changeit

keytool -import -trustcacerts -file tls/server/trust-store/server-crt.pem \
  -alias serverCA -keystore tls/server/trust-store/truststore.p12 -storepass changeit
```

### CLIENT:

* generate client key
```
openssl genrsa -out tls/server/trust-store/client/client.key 2048
```

* prepare CSR (Certificate Signing Request)
```
openssl req -new -key tls/server/trust-store/client/client.key \
 -out tls/server/trust-store/client/client.csr \
 -subj "/C=DE/ST=Brandenburg/L=Berlin/O=Service-Health/OU=EPA/CN=1-SMC-B-Testkarte--883110000162363"
```

* sign client certificate:
```
openssl x509 -req -in tls/server/trust-store/client/client.csr \
-CA tls/server/trust-store/ca-crt.pem \
-CAkey tls/server/trust-store/ca-key.pem \
-CAcreateserial \
-out tls/server/trust-store/client/client.pem \
-days 365 -sha256
```

* prepare p12 client key for clients: browser, etc:
```
openssl pkcs12 -export \
-in tls/server/trust-store/client/client.pem \
-inkey tls/server/trust-store/client/client.key \
-out tls/server/trust-store/client/client.p12 \
-name "Epa Client Certificate"
```

### Verify
```
openssl verify -CAfile tls/server/trust-store/ca-crt.pem tls/server/trust-store/client/client.pem
```