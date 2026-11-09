package com.tikal.hudson.plugins.notification;

import static org.junit.Assert.assertEquals;

import hudson.util.XStream2;
import org.junit.Test;

public class EndpointTest {

    @Test
    public void branchDefaultsWhenMissingFromSavedConfig() {
        // Configs saved before the branch option was added have no <branch>, and XStream skips field initializers
        Endpoint endpoint = (Endpoint) new XStream2()
                .fromXML("<com.tikal.hudson.plugins.notification.Endpoint>"
                        + "<urlInfo><urlOrId>http://localhost</urlOrId><urlType>PUBLIC</urlType></urlInfo>"
                        + "</com.tikal.hudson.plugins.notification.Endpoint>");

        assertEquals(Endpoint.DEFAULT_BRANCH, endpoint.getBranch());
    }

    @Test
    public void branchKeepsConfiguredFilter() {
        Endpoint endpoint = new Endpoint(new UrlInfo(UrlType.PUBLIC, "http://localhost"));
        endpoint.setBranch("master");

        assertEquals("master", endpoint.getBranch());
    }
}
