/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tikal.hudson.plugins.notification;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class Endpoint {

    public static final Integer DEFAULT_TIMEOUT = 30000;
    
    public static final Integer DEFAULT_RETRIES = 0;

    private Protocol protocol = Protocol.HTTP;
    
    /**
     * json as default
     */
    private Format format = Format.JSON;
    
    private UrlInfo urlInfo;

    private String event = "all";

    private Integer timeout = DEFAULT_TIMEOUT;

    private Integer loglines = 0;

    private String buildNotes;
    
    private Integer retries = DEFAULT_RETRIES;

    /**
     * Adds a new endpoint for notifications
     * @param protocol - Protocol to use
     * @param url Public URL
     * @param event - Event to fire on.
     * @param format - Format to send message in.
     * @param timeout Timeout for sending data
     * @param loglines - Number of lines to send
     */
    @Deprecated
    public Endpoint(Protocol protocol, String url, String event, Format format, Integer timeout, Integer loglines) {
        this.protocol = protocol;
        this.urlInfo = new UrlInfo(UrlType.PUBLIC, url);
        this.event = event;
        this.format = format;
        this.timeout = timeout;
        this.loglines = loglines;
    }
    
    /**
     * Adds a new endpoint for notifications
     * @param urlInfo Information about the target URL for the event.
     */
    @DataBoundConstructor
    public Endpoint(UrlInfo urlInfo) {
        setUrlInfo ( urlInfo );
    }
    
    public UrlInfo getUrlInfo() {
        if (this.urlInfo == null) {
            this.urlInfo = new UrlInfo(UrlType.PUBLIC, "");
        }
        return this.urlInfo;
    }

    public void setUrlInfo(UrlInfo urlInfo) {
        this.urlInfo = urlInfo;
    }

    public int getTimeout() {
        return this.timeout;
    }

    /**
     * Sets a timeout for the notification.
     * @param timeout - Timeout in ms.  Default is 30s (30000)
     */
    @DataBoundSetter
    public void setTimeout(Integer timeout) {
        this.timeout =  timeout;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * Sets the protocol for the 
     * @param protocol Protocol to use.  Valid values are: UDP, TCP, HTTP.  Default is HTTP.
     * HTTP event target urls must start with 'http'
     */
    @DataBoundSetter
    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public String getEvent (){
        return event;
    }

    /**
     * Sets the specific event to contact the endpoint for.
     * @param event 'STARTED' - Fire on job started. 'COMPLETED' - Fire on job completed. 'FINALIZED' - Fire on job finalized.
     */
    @DataBoundSetter
    public void setEvent ( String event ){
        this.event = event;
    }
    
    public Format getFormat() {
        if (this.format==null){
            this.format = Format.JSON;
        }
        return format;
    }

    /**
     * Format of the message sent to the endpoint should
     * @param format 'XML' or 'JSON'
     */
    @DataBoundSetter
    public void setFormat(Format format) {
        this.format = format;
    }

    public Integer getLoglines() {
        return this.loglines;
    }

    /**
     * Set the number of log lines to send with the message.
     * @param loglines - Default 0, -1 for unlimited.
     */
    @DataBoundSetter
    public void setLoglines(Integer loglines) {
        this.loglines = loglines;
    }

    public String getBuildNotes() {
        return buildNotes;
    }

    /**
     * Set any additional build information to be sent in message.
     * @param buildNotes - the additional data
     */
    @DataBoundSetter
    public void setBuildNotes(String buildNotes) {
        this.buildNotes = buildNotes;
    }

    public boolean isJson() {
        return getFormat() == Format.JSON;
    }
    
    public Integer getRetries() {
        return this.retries == null ? DEFAULT_RETRIES : this.retries;
    }
    
    /**
     * Number of retries before giving up on contacting an endpoint
     * @param retries - Number of retries.  Default 0.
     */
    @DataBoundSetter
    public void setRetries(Integer retries) {
        this.retries = retries;
    }

    @Override
    public String toString() {
        return protocol+":"+urlInfo.getUrlOrId();
    }
}
