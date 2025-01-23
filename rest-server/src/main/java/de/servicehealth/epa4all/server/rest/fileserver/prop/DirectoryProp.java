package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.insurance.InsuranceData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;
import org.jugs.webdav.jaxrs.xml.elements.HRef;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;
import org.jugs.webdav.jaxrs.xml.elements.Response;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DirectoryProp extends AbstractWebDavProp {

    @Inject
    FileProp fileProp;

    @Override
    public MultiStatus propfind(
        File resource,
        PropFind propFind,
        URI requestUri,
        UriBuilder uriBuilder,
        int depth
    ) throws Exception {
        if (resource == null || !resource.isDirectory()) {
            return new MultiStatus();
        }
        InsuranceData insuranceData = getInsuranceData(requestUri);
        PropStatInfo propStatInfo = propFind.getPropName() != null
            ? getPropStatNamesInfo(webdavConfig.getDirectoryProps())
            : getPropStatInfo(webdavConfig.getDirectoryProps(), insuranceData, resource, propFind);

        final Response davResponse = new Response(
            new HRef(requestUri),
            null,
            null,
            null,
            propStatInfo.okStat,
            propStatInfo.notFoundStat
        );
        if (depth <= 0) {
            return new MultiStatus(davResponse);
        } else {
            List<Response> nestedResources = new ArrayList<>();
            nestedResources.add(davResponse);
            collectNestedResources(nestedResources, resource, uriBuilder, propFind, depth - 1);
            return new MultiStatus(nestedResources.toArray(Response[]::new));
        }
    }

    private void collectNestedResources(
        List<Response> nestedResources,
        File resource,
        UriBuilder uriBuilder,
        PropFind propFind,
        int depth
    ) throws Exception {
        File[] files = resource.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String fileName = file.getName();
            UriBuilder nestedBuilder = uriBuilder.clone().path(fileName);
            MultiStatus multiStatus = null;
            if (file.isDirectory()) {
                if (depth >= 0) {
                    multiStatus = propfind(file, propFind, nestedBuilder.build(), nestedBuilder, depth - 1);
                }
            } else {
                multiStatus = fileProp.propfind(file, propFind, nestedBuilder.build(), nestedBuilder, depth);
            }
            if (multiStatus != null) {
                nestedResources.addAll(multiStatus.getResponses());
            }
        }
    }
}