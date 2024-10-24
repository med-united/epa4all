package de.servicehealth.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

@Getter
@AllArgsConstructor
public class SSLResult {

    private final SSLContext sslContext;
    
    private final KeyManagerFactory keyManagerFactory;
}
