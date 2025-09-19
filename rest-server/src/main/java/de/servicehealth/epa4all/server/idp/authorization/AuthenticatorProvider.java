package de.servicehealth.epa4all.server.idp.authorization;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.data.UserConsent;
import de.gematik.idp.token.JsonWebToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import kong.unirest.core.Config;
import kong.unirest.core.HttpRequest;
import kong.unirest.core.HttpRequestSummary;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Interceptor;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import kong.unirest.jackson.JacksonObjectMapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AuthenticatorProvider {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatorProvider.class.getName());

    @Produces
    @Singleton
    AuthenticatorClient getAuthenticatorClient(@ConfigProperty(name="idp.kind") String kind) {
        UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().followRedirects(false);
        unirestInstance.config().setObjectMapper(new JacksonObjectMapper());
        unirestInstance.config().interceptor(new Interceptor() {
            
            @Override
            public void onRequest(HttpRequest<?> req, Config cfg) {
                log.info("--> {} {}\nHeaders: {}\nBody: {}\n",
                    req.getHttpMethod(),
                    req.getUrl(),
                    req.getHeaders(),
                    req.getBody().isPresent() ? req.getBody().get().multiParts().toString() : "none");
            }

            @Override
            public void onResponse(HttpResponse<?> res, HttpRequestSummary req, Config cfg) {
                Object body = res.getBody();
                if (body instanceof AuthenticationChallenge challenge) {
                    JsonWebToken jwt = challenge.getChallenge();
                    String headerClaims = jwt.extractHeaderClaims().toString();
                    String bodyClaims = jwt.extractBodyClaims().toString();
                    UserConsent userConsent = challenge.getUserConsent();

                    log.info("<-- {} {}\nHeaders: {}\nBody: {}\n",
                        res.getStatus(),
                        req.getUrl(),
                        res.getHeaders(),
                        headerClaims + "\n" + bodyClaims + "\n" + userConsent.toString()
                    );
                } else {
                    log.info("<-- {} {}\nHeaders: {}\nBody: {}\n",
                        res.getStatus(),
                        req.getUrl(),
                        res.getHeaders(),
                        body
                    );
                }
            }
            
            @Override
            public HttpResponse<?> onFail(Exception e, HttpRequestSummary req, Config cfg) {
                log.error("<XX {} {} :", req.getHttpMethod(), req.getUrl(), e);
                return null;
            }
        });
        
        if ("tss".equals(kind)) {
            return new TSSAuthenticatorClient(unirestInstance);
        } else {
            return new AuthenticatorClient(unirestInstance);
        }
    }
}