package de.servicehealth.epa4all.server.crypto;

import java.security.Security;

import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class AddBouncyCastlePQCProvider {

	void onStart(@Observes StartupEvent ev) {
		// Security.addProvider(new BouncyCastlePQCProvider());
    }
}
