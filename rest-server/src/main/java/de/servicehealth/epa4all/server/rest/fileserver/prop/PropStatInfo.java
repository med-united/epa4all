package de.servicehealth.epa4all.server.rest.fileserver.prop;

import org.jugs.webdav.jaxrs.xml.elements.PropStat;

public class PropStatInfo {
    PropStat okStat;
    PropStat[] notFoundStat;

    public PropStatInfo(PropStat okStat, PropStat[] notFoundStat) {
        this.okStat = okStat;
        this.notFoundStat = notFoundStat;
    }
}
