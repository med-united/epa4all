package org.apache.jackrabbit.webdav.jcr;

import de.servicehealth.epa4all.server.jcr.prop.JcrProp;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceIterator;
import org.apache.jackrabbit.webdav.DavResourceIteratorImpl;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.EPA_NAMESPACE_URI;
import static de.servicehealth.epa4all.server.jcr.prop.MixinProp.EPA_NAMESPACE_PREFIX;

public class JVersionControlledItemCollection extends VersionControlledItemCollection {

    private static final Logger log = LoggerFactory.getLogger(JVersionControlledItemCollection.class.getName());

    public JVersionControlledItemCollection(
        DavResourceLocator locator,
        JcrDavSession session,
        DavResourceFactory factory,
        Item item
    ) {
        super(locator, session, factory, item);
    }

    @Override
    public DavPropertyName[] getPropertyNames() {
        List<DavPropertyName> davPropertyNames = Arrays.asList(super.getPropertyNames());

        Namespace namespace = Namespace.getNamespace(EPA_NAMESPACE_PREFIX, EPA_NAMESPACE_URI);
        davPropertyNames.addAll(Arrays.stream(JcrProp.values()).map(p -> DavPropertyName.create(p.name(), namespace)).toList());
        return davPropertyNames.toArray(DavPropertyName[]::new);
    }

    @Override
    protected void initProperties() {
        super.initProperties();
        Node node = (Node) item;
        Namespace namespace = Namespace.getNamespace(EPA_NAMESPACE_PREFIX, EPA_NAMESPACE_URI);
        Arrays.stream(JcrProp.values()).forEach(p -> {
            try {
                Property property = node.getProperty(p.getName());

                // TODO get precise value

                properties.add(new DefaultDavProperty<>(p.name(), property.getValue().getString(), namespace, false));
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public DavProperty<?> getProperty(DavPropertyName name) {
        DavProperty<?> prop = super.getProperty(name);
        if (prop == null) {
            try {
                Namespace namespace = name.getNamespace();
                String prefix = namespace.getPrefix();
                if (!prefix.isEmpty()) {
                    prefix = prefix + ":";
                }
                String relPath = prefix + name.getName();
                Property property = ((Node) item).getProperty(relPath);

                // TODO get precise value

                return new DefaultDavProperty<>(name.getName(), property.getValue().getString(), namespace, false);
            } catch (Exception ignored) {
            }
        }
        return prop;
    }

    @Override
    public DavResourceIterator getMembers() {
        ArrayList<DavResource> memberList = new ArrayList<DavResource>();
        if (exists()) {
            try {
                Node n = (Node)item;
                // add all node members
                NodeIterator it = n.getNodes();
                while (it.hasNext()) {
                    Node node = it.nextNode();
                    DavResourceLocator loc = getLocatorFromItem(node);
                    memberList.add(createResourceFromLocator(loc));
                }
            } catch (RepositoryException e) {
                // ignore
                log.error(e.getMessage());
            } catch (DavException e) {
                // should never occur.
                log.error(e.getMessage());
            }
        }
        return new DavResourceIteratorImpl(memberList);
    }
}