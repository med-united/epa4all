package de.servicehealth.epa4all.server.rest.fileserver.paging;

import de.servicehealth.folder.WebdavConfig;
import jakarta.ws.rs.core.MultivaluedMap;
import lombok.Getter;
import lombok.Setter;

@Getter
public class Paginator {

    public static final String X_OFFSET = "X-Offset";
    public static final String X_LIMIT = "X-Limit";
    public static final String X_SORT_BY = "X-Sort-By";
    public static final String X_TOTAL_COUNT = "X-Total-Count";

    private final int offset;
    private final int limit;
    private final SortBy sortBy;

    @Setter
    private int totalCount;

    public Paginator(WebdavConfig webdavConfig, MultivaluedMap<String, String> requestHeaders) {
        offset = getIntValue(requestHeaders.getFirst(X_OFFSET), 0);
        limit = getIntValue(requestHeaders.getFirst(X_LIMIT), webdavConfig.getDefaultLimit());
        sortBy = SortBy.from(requestHeaders.getFirst(X_SORT_BY));
    }

    private int getIntValue(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
