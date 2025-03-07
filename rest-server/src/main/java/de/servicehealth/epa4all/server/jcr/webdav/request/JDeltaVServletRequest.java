package de.servicehealth.epa4all.server.jcr.webdav.request;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.LabelInfo;
import org.apache.jackrabbit.webdav.version.MergeInfo;
import org.apache.jackrabbit.webdav.version.OptionsInfo;
import org.apache.jackrabbit.webdav.version.UpdateInfo;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;

public interface JDeltaVServletRequest extends JDavServletRequest {

    /**
     * Returns the Label header or <code>null</code>
     *
     * @return label header or <code>null</code>
     * @see DeltaVConstants#HEADER_LABEL
     */
    public String getLabel();

    /**
     * Return the request body as <code>LabelInfo</code> object or <code>null</code>
     * if parsing the request body or the creation of the label info failed.
     *
     * @return <code>LabelInfo</code> object or <code>null</code>
     * @throws DavException in case of an invalid request body
     */
    public LabelInfo getLabelInfo() throws DavException;

    /**
     * Return the request body as <code>MergeInfo</code> object or <code>null</code>
     * if the creation failed due to invalid format.
     *
     * @return <code>MergeInfo</code> object or <code>null</code>
     * @throws DavException in case of an invalid request body
     */
    public MergeInfo getMergeInfo() throws DavException;

    /**
     * Parses the UPDATE request body a build the corresponding <code>UpdateInfo</code>
     * object. If the request body is missing or does not of the required format
     * <code>null</code> is returned.
     *
     * @return the parsed update request body or <code>null</code>
     * @throws DavException in case of an invalid request body
     */
    public UpdateInfo getUpdateInfo() throws DavException;

    /**
     * Returns the request body and the Depth header as <code>ReportInfo</code>
     * object. The default depth, if no {@link org.apache.jackrabbit.webdav.DavConstants#HEADER_DEPTH
     * Depth header}, is {@link org.apache.jackrabbit.webdav.DavConstants#DEPTH_0}.
     * If the request body could not be parsed into an {@link org.w3c.dom.Element}
     * <code>null</code> is returned.
     *
     * @return <code>ReportInfo</code> or <code>null</code>
     * @throws DavException in case of an invalid request body
     */
    public ReportInfo getReportInfo() throws DavException;

    /**
     * Returns the {@link OptionsInfo} present with the request or <code>null</code>.
     *
     * @return {@link OptionsInfo} or <code>null</code>
     * @throws DavException in case of an invalid request body
     */
    public OptionsInfo getOptionsInfo() throws DavException;
}
