#!/bin/bash
export ALIAS=`hostname -f`

echo "Generating RSA keys..."
keytool -genkey -alias $ALIAS -keyalg RSA -validity 365 -keystore keystore.jks -storetype JKS

echo "Exporting public certificate..."
keytool -export -alias $ALIAS -keystore keystore.jks -rfc -file server.cer

echo "Importing public certificate in trusted key store..."
keytool -import -alias $ALIAS -file server.cer -storetype JKS -keystore cacerts.jks

#echo "Install certificate for Google's Chrome browser..."
#certutil -d sql:$HOME/.pki/nssdb -A -t CP,,C -n "$ALIAS" -i server.cer
