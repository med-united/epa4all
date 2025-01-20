#!/bin/bash
sudo keytool -importcert -file epa-as-1.dev.epa4all.de.pem -alias epa-as-1.dev.epa4all.de -cacerts
sudo keytool -importcert -file epa-as-2.dev.epa4all.de.pem -alias epa-as-2.dev.epa4all.de -cacerts
