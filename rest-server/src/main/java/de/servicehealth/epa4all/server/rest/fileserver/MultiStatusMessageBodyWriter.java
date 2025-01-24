package de.servicehealth.epa4all.server.rest.fileserver;

import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.BirthDay;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.Entries;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.EntryUUID;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.FirstName;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.LastName;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.Smcb;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.ValidTo;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.jugs.webdav.jaxrs.xml.conditions.CannotModifyProtectedProperty;
import org.jugs.webdav.jaxrs.xml.conditions.LockTokenMatchesRequestUri;
import org.jugs.webdav.jaxrs.xml.conditions.LockTokenSubmitted;
import org.jugs.webdav.jaxrs.xml.conditions.NoConflictingLock;
import org.jugs.webdav.jaxrs.xml.conditions.NoExternalEntities;
import org.jugs.webdav.jaxrs.xml.conditions.PreservedLiveProperties;
import org.jugs.webdav.jaxrs.xml.conditions.PropFindFiniteDepth;
import org.jugs.webdav.jaxrs.xml.elements.Error;
import org.jugs.webdav.jaxrs.xml.elements.*;
import org.jugs.webdav.jaxrs.xml.properties.CreationDate;
import org.jugs.webdav.jaxrs.xml.properties.DisplayName;
import org.jugs.webdav.jaxrs.xml.properties.GetContentLanguage;
import org.jugs.webdav.jaxrs.xml.properties.GetContentLength;
import org.jugs.webdav.jaxrs.xml.properties.GetContentType;
import org.jugs.webdav.jaxrs.xml.properties.GetETag;
import org.jugs.webdav.jaxrs.xml.properties.GetLastModified;
import org.jugs.webdav.jaxrs.xml.properties.LockDiscovery;
import org.jugs.webdav.jaxrs.xml.properties.ResourceType;
import org.jugs.webdav.jaxrs.xml.properties.SupportedLock;

import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;

import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML_TYPE;

@Produces({APPLICATION_XML})
@Provider
public class MultiStatusMessageBodyWriter implements MessageBodyWriter<MultiStatus> {

    private static final Logger log = Logger.getLogger(MultiStatusMessageBodyWriter.class.getName());

    static JAXBContext jaxbContext;

    static {
        final Class<?>[] webDavClasses = new Class<?>[]{
            ActiveLock.class, AllProp.class, CannotModifyProtectedProperty.class, Collection.class,
            CreationDate.class, Depth.class, DisplayName.class, Error.class, Exclusive.class, GetContentLanguage.class,
            GetContentLength.class, GetContentType.class, GetETag.class, GetLastModified.class, HRef.class, Include.class,
            Location.class, LockDiscovery.class, LockEntry.class, LockInfo.class, LockRoot.class, LockScope.class,
            LockToken.class, LockTokenMatchesRequestUri.class, LockTokenSubmitted.class, LockType.class, MultiStatus.class,
            NoConflictingLock.class, NoExternalEntities.class, Owner.class, PreservedLiveProperties.class, Prop.class,
            PropertyUpdate.class, PropFind.class, PropFindFiniteDepth.class, PropName.class, PropStat.class, Remove.class,
            ResourceType.class, Response.class, ResponseDescription.class, Set.class, Shared.class, Status.class,
            SupportedLock.class, TimeOut.class, Write.class, FirstName.class, LastName.class, BirthDay.class,
            Entries.class, EntryUUID.class, Smcb.class, ValidTo.class
        };
        try {
            jaxbContext = JAXBContext.newInstance(webDavClasses);
        } catch (JAXBException e) {
            log.log(Level.SEVERE, "Could not build JAXB context", e);
        }
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type.equals(MultiStatus.class) && mediaType.isCompatible(APPLICATION_XML_TYPE);
    }

    @Override
    public void writeTo(
        MultiStatus t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
        MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream
    ) throws WebApplicationException {
        try {
            Marshaller m = jaxbContext.createMarshaller();
            m.marshal(t, entityStream);
        } catch (JAXBException e) {
            throw new WebApplicationException(e);
        }
    }
}
