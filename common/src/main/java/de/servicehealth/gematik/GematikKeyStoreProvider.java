package de.servicehealth.gematik;

import lombok.Getter;

import java.io.Serial;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;

/**
 * JCA {@link Provider} that exposes a single {@code KeyStore} service of type {@code "GEM"}
 * backed by a {@link GematikKeyStoreSpi} bound to a Gematik environment
 * ({@link GematikEnvironment#PU} or {@link GematikEnvironment#RU}).
 */
@Getter
public final class GematikKeyStoreProvider extends Provider {

    @Serial
    private static final long serialVersionUID = -5230792479053161198L;

    public static final String NAME_PREFIX = "Gematik-";
    public static final String KEYSTORE_TYPE = "GEM";
    private static final String VERSION = "1.0";
    private static final String INFO = "Gematik TSL-backed KeyStore provider";

    private final GematikEnvironment environment;

    public GematikKeyStoreProvider(GematikEnvironment environment) {
        super(NAME_PREFIX + environment.name(), VERSION, INFO);
        this.environment = environment;
        putService(new GematikKeyStoreService(this));
    }

    private static final class GematikKeyStoreService extends Service {

        private final GematikKeyStoreProvider provider;

        GematikKeyStoreService(GematikKeyStoreProvider provider) {
            super(provider, "KeyStore", KEYSTORE_TYPE, GematikKeyStoreSpi.class.getName(), null, null);
            this.provider = provider;
        }

        @Override
        public Object newInstance(Object constructorParameter) throws NoSuchAlgorithmException {
            if (constructorParameter != null) {
                throw new NoSuchAlgorithmException(
                    "GematikProvider KeyStore service does not accept a constructor parameter; "
                        + "the environment is bound to the provider instance");
            }
            return new GematikKeyStoreSpi(provider.getEnvironment());
        }
    }
}
