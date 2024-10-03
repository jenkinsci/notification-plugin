/*
 * Copyright 2017 mmitche.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tikal.hudson.plugins.notification;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author mmitche
 */
public class UrlInfo {

    private String urlOrId;
    private UrlType urlType;

    @DataBoundConstructor
    public UrlInfo(UrlType urlType, String urlOrId) {
        setUrlOrId(urlOrId);
        setUrlType(urlType);
    }

    public String getUrlOrId() {
        return urlOrId;
    }

    public void setUrlOrId(String urlOrId) {
        this.urlOrId = urlOrId;
    }

    public UrlType getUrlType() {
        return urlType;
    }

    public void setUrlType(UrlType urlType) {
        this.urlType = urlType;
    }
}
