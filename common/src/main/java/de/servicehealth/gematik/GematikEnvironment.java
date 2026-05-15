package de.servicehealth.gematik;

import lombok.Getter;

@Getter
public enum GematikEnvironment {

    PU(
        "http://download.crl.ti-dienste.de/TSL-ECC/ECC-RSA_TSL.xml",
        "signing-cert-issuer/C.GEM.TSL-CA3.der"
    ),
    // TODO - confirm RU and REF TSL sources
    RU(
        "http://download-testref.crl.ti-dienste.de/TSL-ECC-ref/ECC-RSA_TSL-ref.xml",
        "signing-cert-issuer/C.GEM.TSL-CA28.der"
    ),
    REF(
        "http://download-testref.crl.ti-dienste.de/TSL-ECC-ref/ECC-RSA_TSL-ref.xml",
        "signing-cert-issuer/C.GEM.TSL-CA28.der"
    );

    private final String tslUrl;
    private final String signingCertResource;

    GematikEnvironment(String tslUrl, String signingCertResource) {
        this.tslUrl = tslUrl;
        this.signingCertResource = signingCertResource;
    }

    public static GematikEnvironment fromQuarkusProfile(String profile) {
        return switch (profile) {
            case String p when p.equalsIgnoreCase("pu") -> PU;
            case String p when p.equalsIgnoreCase("ru") -> RU;
            default -> REF;
        };
    }
}
