#!/usr/bin/env bash
rm -f *.pem
rm -f *.p12
rm -f *.jks
openssl req -x509 -out certificate.pem -keyout key.pem -newkey rsa:2048 -nodes -sha256 -days 365000 -subj '/CN=localhost' -extensions EXT -config <( printf "[dn]\nCN=localhost\n[req]\ndistinguished_name = dn\n[EXT]\nsubjectAltName=DNS:localhost\nkeyUsage=digitalSignature\nextendedKeyUsage=serverAuth")
openssl pkcs12 -export -in certificate.pem -inkey key.pem -name localhost -out certificate.p12
keytool -importkeystore -deststorepass password -destkeystore certificate.jks -srckeystore certificate.p12 -srcstoretype PKCS12
