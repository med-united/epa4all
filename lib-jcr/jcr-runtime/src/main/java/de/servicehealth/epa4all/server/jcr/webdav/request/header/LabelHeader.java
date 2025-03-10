package de.servicehealth.epa4all.server.jcr.webdav.request.header;

import de.servicehealth.epa4all.server.jcr.webdav.request.JWebdavRequest;
import org.apache.jackrabbit.webdav.header.Header;
import org.apache.jackrabbit.webdav.util.EncodeUtil;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>LabelHeader</code>...
 */
public class LabelHeader implements Header {

    private static final Logger log = LoggerFactory.getLogger(LabelHeader.class);

    private final String label;

    public LabelHeader(String label) {
        if (label == null) {
            throw new IllegalArgumentException("null is not a valid label.");
        }
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getHeaderName() {
        return DeltaVConstants.HEADER_LABEL;
    }

    public String getHeaderValue() {
        return EncodeUtil.escape(label);
    }

    public static LabelHeader parse(JWebdavRequest request) {
        String hv = request.getHeader(DeltaVConstants.HEADER_LABEL);
        if (hv == null) {
            return null;
        } else {
            return new LabelHeader(EncodeUtil.unescape(hv));
        }
    }
}