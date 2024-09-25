package de.servicehealth.epa4all.restful;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.client.apache.ApacheHttpClient;
import ca.uhn.fhir.rest.client.apache.ApacheHttpRequest;
import ca.uhn.fhir.rest.client.api.Header;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicNameValuePair;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VauApacheHttpClient extends ApacheHttpClient {

    private final HttpClient myClient;

    public VauApacheHttpClient(
        HttpClient theClient, StringBuilder theUrl, Map<String, List<String>> theIfNoneExistParams,
        String theIfNoneExistString, RequestTypeEnum theRequestType, List<Header> theHeaders
    ) {
        super(theClient, theUrl, theIfNoneExistParams, theIfNoneExistString, theRequestType, theHeaders);
        this.myClient = theClient;
    }

    @Override
    protected IHttpRequest createHttpRequest() {
        return createVauHttpRequest(null);
    }

    @Override
    protected IHttpRequest createHttpRequest(byte[] content) {
        ByteArrayEntity entity = new ByteArrayEntity(content);
        return createVauHttpRequest(entity);
    }

    @Override
    protected IHttpRequest createHttpRequest(Map<String, List<String>> theParams) {
        List<NameValuePair> parameters = new ArrayList<>();
        for (Map.Entry<String, List<String>> nextParam : theParams.entrySet()) {
            List<String> value = nextParam.getValue();
            for (String s : value) {
                parameters.add(new BasicNameValuePair(nextParam.getKey(), s));
            }
        }

        UrlEncodedFormEntity entity = createVauFormEntity(parameters);
        return createVauHttpRequest(entity);
    }

    private UrlEncodedFormEntity createVauFormEntity(List<NameValuePair> parameters) {
        try {
            return new UrlEncodedFormEntity(parameters, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new InternalErrorException(Msg.code(1479) + "Server does not support UTF-8 (should not happen)", e);
        }
    }

    @Override
    protected IHttpRequest createHttpRequest(String theContents) {
        ByteArrayEntity entity = new ByteArrayEntity(theContents.getBytes(Constants.CHARSET_UTF8));
        return createVauHttpRequest(entity);
    }

    private HttpRequestBase constructVauRequestBase(HttpEntity theEntity) {
        String url = myUrl.toString();
        switch (myRequestType) {
            case PATCH:
                HttpPatch httpPatch = new HttpPatch(url);
                httpPatch.setEntity(theEntity);
                return httpPatch;
            case OPTIONS:
                return new HttpOptions(url);
            default:
                HttpPost httpPost = new HttpPost(url);
                httpPost.setEntity(theEntity);
                return httpPost;
        }
    }

    private ApacheHttpRequest createVauHttpRequest(HttpEntity theEntity) {
        HttpRequestBase request = constructVauRequestBase(theEntity);
        return new ApacheHttpRequest(myClient, request);
    }
}