package de.servicehealth.epa4all.server.jcr.webdav.request;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.transaction.TransactionConstants;
import org.apache.jackrabbit.webdav.transaction.TransactionInfo;

public interface JTransactionDavServletRequest extends JDavServletRequest {

    /**
     * Retrieve the 'transactioninfo' request body that must be included with
     * the UNLOCK request of a transaction lock. If the request body is does not
     * provide the information required (either because it is missing or the
     * Xml is not valid) <code>null</code> is returned.
     *
     * @return <code>TransactionInfo</code> object encapsulating the 'transactioninfo'
     * Xml element present in the request body or <code>null</code> if no
     * body is present or if it could not be parsed.
     * @throws DavException if an invalid request body is present.
     */
    public TransactionInfo getTransactionInfo() throws DavException;


    /**
     * Retrieve the transaction id from the
     * {@link TransactionConstants#HEADER_TRANSACTIONID TransactionId header}.
     *
     * @return transaction id as present in the {@link TransactionConstants#HEADER_TRANSACTIONID TransactionId header}
     * or <code>null</code>.
     */
    public String getTransactionId();
}