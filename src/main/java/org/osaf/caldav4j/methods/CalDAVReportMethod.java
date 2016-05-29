package org.osaf.caldav4j.methods;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import org.apache.commons.httpclient.HeaderElement;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.DavMethodBase;
import org.apache.jackrabbit.webdav.header.DepthHeader;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.osaf.caldav4j.CalDAVConstants;
import org.osaf.caldav4j.model.request.CalDAVReportRequest;
import org.osaf.caldav4j.util.CaldavStatus;

import java.io.IOException;
import java.io.InputStream;

/**
 * CalDAV Report Method, which extends DavMethodBase
 * @author <a href="mailto:ankushmishra9@gmail.com">Ankush Mishra</a>
 */
public class CalDAVReportMethod extends DavMethodBase {

    private static final Log log = LogFactory.getLog(CalDAVReportMethod.class);

    private boolean isCalendarResponse = false;
    private boolean isDeep = false;
    private Calendar calendarResponse = null;
    private CalDAVReportRequest reportRequest = null;
    private CalendarBuilder calendarBuilder = null;

    public CalDAVReportMethod(String uri){
        super(uri);
    }

    public CalDAVReportMethod(String uri, CalDAVReportRequest reportRequest) throws IOException {
        this(uri, reportRequest, CalDAVConstants.DEPTH_1);
    }

    public CalDAVReportMethod(String uri, CalDAVReportRequest reportRequest, int depth) throws IOException {
        super(uri);
        this.reportRequest = reportRequest;
        processReportRequest(reportRequest);
        setDepth(depth);
    }

    /**
     * Sets the depth and the request body as the Report specified.
     * @param reportRequest
     * @throws IOException
     */
    private void processReportRequest(CalDAVReportRequest reportRequest) throws IOException {
        setRequestBody(reportRequest);
    }

    public void setReportRequest(CalDAVReportRequest reportRequest) throws IOException {
        this.reportRequest = reportRequest;
        processReportRequest(reportRequest);
    }

    public void setDepth(int depth){
        isDeep = depth > CalDAVConstants.DEPTH_0;

        setRequestHeader(new DepthHeader(depth));
    }

    public void setCalendarBuilder(CalendarBuilder calendarBuilder) {
        this.calendarBuilder = calendarBuilder;
    }

    public CalendarBuilder getCalendarBuilder() {
        return this.calendarBuilder;
    }

    /**
     * @see HttpMethod#getName()
     */
    @Override
    public String getName() {
        return DavMethods.METHOD_REPORT;
    }

    /**
     *
     * @param statusCode
     * @return true if status code is {@link DavServletResponse#SC_OK 200 (OK)}
     * or {@link DavServletResponse#SC_MULTI_STATUS 207 (Multi Status)}. If the
     * report request included a depth other than {@link CalDAVConstants#DEPTH_0 0}
     * a multi status response is required.
     */
    @Override
    protected boolean isSuccess(int statusCode) {
        if (isDeep) {
            return statusCode == CaldavStatus.SC_MULTI_STATUS;
        } else {
            return statusCode == CaldavStatus.SC_OK || statusCode == CaldavStatus.SC_MULTI_STATUS;
        }
    }

    @Override
    protected void processResponseHeaders(HttpState state, HttpConnection conn) {
        super.processResponseHeaders(state, conn);
        HeaderElement[] elements = getResponseHeader(CalDAVConstants.HEADER_CONTENT_TYPE).getElements();
        for (HeaderElement element : elements) {
            if (element.getName().equals(CalDAVConstants.CONTENT_TYPE_CALENDAR)) {
                isCalendarResponse = true;
                log.info("Response Content-Type: text/calendar");
            } else if (element.getName().equals(CalDAVConstants.CONTENT_TYPE_TEXT_XML)) {
                log.info("Response Content-Type: text/xml");
            } else log.warn("Response Content-Type is not text/xml or text/calendar");
        }
    }

    public Calendar getResponseBodyAsCalendar(){
        return this.calendarResponse;
    }

    @Override
    protected void processResponseBody(HttpState httpState, HttpConnection httpConnection) {
        if (getStatusCode() == CaldavStatus.SC_OK && isCalendarResponse){
            try {
                InputStream stream = getResponseBodyAsStream();
                calendarResponse = calendarBuilder.build(stream);
            } catch (IOException e) {
                e.printStackTrace();
                log.error("Error while parsing Calendar response: " + e);
            } catch (ParserException e) {
                e.printStackTrace();
                log.error("Error while parsing Calendar response: " + e);
            }
        }
        else
            super.processResponseBody(httpState, httpConnection);
    }

    public DavProperty getDavProperty(String urlPath, DavPropertyName property) {
        MultiStatusResponse[] responses = getResponseBodyAsMultiStatusResponse();
        if(responses != null && succeeded()) {
            for (MultiStatusResponse r : responses) {
                if(r.getHref().equals(urlPath)){
                    DavPropertySet props = r.getProperties(CaldavStatus.SC_OK);
                    return props.get(property);
                }
            }
        }

        log.warn("Can't find object at: " + urlPath);
        return null;
    }

    /**
     * Returns all the set of properties and their value, for all the hrefs
     * @param property
     * @return
     */
    public DavPropertySet getDavProperty(DavPropertyName property) {
        MultiStatusResponse[] responses = getResponseBodyAsMultiStatusResponse();
        DavPropertySet set = new DavPropertySet(); //TODO: Use Collection instead of Set?
        if(responses != null && succeeded()) {
            for (MultiStatusResponse r : responses) {
                DavPropertySet props = r.getProperties(CaldavStatus.SC_OK);
                if(!props.isEmpty()) set.add(props.get(property));
            }
        }

        return set;
    }

    public MultiStatusResponse[] getResponseBodyAsMultiStatusResponse(){
        try {
            return getResponseBodyAsMultiStatus().getResponses();
        } catch (IOException e) {
            log.error("Unable to get MultiStatusResponses.");
        } catch (DavException e) {
            log.error("Unable to get MultiStatusResponses.");
        }

        return null;
    }

    public MultiStatusResponse getResponseBodyAsMultiStatusResponse(String uri){
        MultiStatusResponse[] responses = getResponseBodyAsMultiStatusResponse();
        for(MultiStatusResponse response: responses)
            if(response.getHref().equals(uri))
                return response;
        log.warn("No Response found for uri: " + uri);
        return null;
    }
}
