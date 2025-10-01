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
package com.tikal.hudson.plugins.notification.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tikal.hudson.plugins.notification.HostnamePort;
import org.junit.jupiter.api.Test;

class HostnamePortTest {

    @Test
    void parseUrlTest() {
        HostnamePort hnp = HostnamePort.parseUrl("111");
        assertNull(hnp);
        hnp = HostnamePort.parseUrl(null);
        assertNull(hnp);
        hnp = HostnamePort.parseUrl("localhost:123");
        assertEquals("localhost", hnp.hostname);
        assertEquals(123, hnp.port);
        hnp = HostnamePort.parseUrl("localhost:123456");
        assertEquals(12345, hnp.port);
    }
}
