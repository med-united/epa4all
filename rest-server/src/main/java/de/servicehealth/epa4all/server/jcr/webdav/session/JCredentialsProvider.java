package de.servicehealth.epa4all.server.jcr.webdav.session;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import javax.jcr.Credentials;
import javax.jcr.LoginException;

public interface JCredentialsProvider {

    Credentials getCredentials(HttpServletRequest request) throws LoginException, ServletException;
}
