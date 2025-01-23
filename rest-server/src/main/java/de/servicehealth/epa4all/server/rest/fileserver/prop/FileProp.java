package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.insurance.InsuranceData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.UriBuilder;
import org.jugs.webdav.jaxrs.xml.elements.HRef;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;
import org.jugs.webdav.jaxrs.xml.elements.Response;

import java.io.File;
import java.net.URI;

@ApplicationScoped
public class FileProp extends AbstractWebDavProp {

    @Override
    public MultiStatus propfind(
        File resource,
        PropFind propFind,
        URI requestUri,
        UriBuilder uriBuilder,
        int depth
    ) throws Exception {
        InsuranceData insuranceData = getInsuranceData(requestUri);
        PropStatInfo propStatInfo = propFind.getPropName() != null
            ? getPropStatNamesInfo(webdavConfig.getFileProps())
            : getPropStatInfo(webdavConfig.getFileProps(), insuranceData, resource, propFind);
        Response davFile = new Response(
            new HRef(requestUri),
            null,
            null,
            null,
            propStatInfo.okStat,
            propStatInfo.notFoundStat);

        return new MultiStatus(davFile);
    }

}
