package org.apache.jackrabbit.webdav.jcr;

import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.property.HrefProperty;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.Property;
import java.util.Arrays;
import java.util.List;

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
        return super.getPropertyNames();
        
        //List<DavPropertyName> davPropertyNames = Arrays.asList(super.getPropertyNames());
        // 
    }

    @Override
    protected void initProperties() {
        super.initProperties();
        Node n = (Node) item;
        try {
            properties.add(new DefaultDavProperty<String>(AUTO_VERSION, null, false));
        } catch (Exception e) {

        }
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
                return new DefaultDavProperty<>(
                    name.getName(), property.getValue().getString(), namespace, false
                );
            } catch (Exception e) {
                log.error("Error while getting property, name = " + name.getName(), e);
            }
        }
        return prop;
    }
}