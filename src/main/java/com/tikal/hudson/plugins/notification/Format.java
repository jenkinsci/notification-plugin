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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thoughtworks.xstream.XStream;
import com.tikal.hudson.plugins.notification.model.JobState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public enum Format {
    XML {
        private final transient XStream xstream = new XStream();

        @Override
        protected byte[] serialize(JobState jobState) throws IOException {
            xstream.processAnnotations(JobState.class);
            return xstream.toXML(jobState).getBytes(StandardCharsets.UTF_8);
        }
    },
    JSON {
        private final transient Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        @Override
        protected byte[] serialize(JobState jobState) throws IOException {
            return gson.toJson(jobState).getBytes(StandardCharsets.UTF_8);
        }
    };

    protected abstract byte[] serialize(JobState jobState) throws IOException;
}
