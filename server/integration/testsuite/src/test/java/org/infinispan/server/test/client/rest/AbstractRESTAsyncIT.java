package org.infinispan.server.test.client.rest;

import static org.infinispan.server.test.client.rest.RESTHelper.KEY_A;
import static org.infinispan.server.test.client.rest.RESTHelper.KEY_B;
import static org.infinispan.server.test.client.rest.RESTHelper.delete;
import static org.infinispan.server.test.client.rest.RESTHelper.fullPathKey;
import static org.infinispan.server.test.client.rest.RESTHelper.get;
import static org.infinispan.server.test.client.rest.RESTHelper.getWithoutAssert;
import static org.infinispan.server.test.client.rest.RESTHelper.head;
import static org.infinispan.server.test.client.rest.RESTHelper.put;
import static org.infinispan.server.test.util.ITestUtils.Condition;
import static org.infinispan.server.test.util.ITestUtils.eventually;
import static org.infinispan.server.test.util.ITestUtils.isReplicatedMode;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.List;

import org.apache.http.HttpStatus;
import org.infinispan.arquillian.core.RemoteInfinispanServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the REST client putAsync header.
 *
 * @author mgencur
 */
public abstract class AbstractRESTAsyncIT {

    protected abstract int getRestPort1();
    protected abstract int getRestPort2();

    protected abstract List<RemoteInfinispanServer> getServers();

    @Before
    public void setUp() throws Exception {
        if (isReplicatedMode()) {
            RESTHelper.addServer(getServers().get(0).getRESTEndpoint().getInetAddress().getHostName(), getRestPort1(), getServers().get(0).getRESTEndpoint().getContextPath());
            RESTHelper.addServer(getServers().get(1).getRESTEndpoint().getInetAddress().getHostName(), getRestPort2(), getServers().get(1).getRESTEndpoint().getContextPath());
        } else {
            RESTHelper.addServer(getServers().get(0).getRESTEndpoint().getInetAddress().getHostName(), getServers().get(0).getRESTEndpoint().getContextPath());
            RESTHelper.addServer(getServers().get(1).getRESTEndpoint().getInetAddress().getHostName(), getServers().get(1).getRESTEndpoint().getContextPath());
        }
        delete(fullPathKey(KEY_A));
        head(fullPathKey(KEY_A), HttpStatus.SC_NOT_FOUND);
    }

    @After
    public void tearDown() throws Exception {
        delete(fullPathKey(null));
        RESTHelper.clearServers();
    }

    @Test
    public void testPutOperation() throws Exception {
        URI fullPathKey = fullPathKey(KEY_A);
        int NUM_OPERATIONS = 1000;
        String initialXML = "<hey>ho</hey>";

        StringBuilder initial = new StringBuilder(initialXML);
        for (int i = 0; i < 200; i++) {
            initial.append("expanding");
        }
        initialXML = initial.toString();

        long t1 = System.currentTimeMillis();
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            put(fullPathKey, initialXML, "application/octet-stream", HttpStatus.SC_OK, "performAsync", "false");
        }
        long putSyncTime = System.currentTimeMillis() - t1;

        delete(fullPathKey);

        t1 = System.currentTimeMillis();
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            put(fullPathKey, initialXML, "application/octet-stream", HttpStatus.SC_OK, "performAsync", "true");
        }
        long putAsyncTime = System.currentTimeMillis() - t1;

        assertTrue("PUT : async- " + putAsyncTime + ", sync- " + putSyncTime, putAsyncTime < putSyncTime);
        get(fullPathKey, initialXML, HttpStatus.SC_OK, true, "performAsync", "true");
    }

    @Test
    public void testDeleteOperation() throws Exception {
        int NUM_OPERATIONS = 15;

        int SIZE = 900;
        byte[] bytes = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            bytes[i] = (byte) (i % 10);
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            put(fullPathKey(String.valueOf(i)), bytes, "application/octet-stream", HttpStatus.SC_OK, "performAsync", "false");
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            delete(fullPathKey(String.valueOf(i)), HttpStatus.SC_OK, "performAsync", "true");
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            final int iter = i; // inner class can access only final variables...
            // since the delete is async, the get can execute before the delete finishes (delete needs communication between nodes)
            eventually(new Condition() {
                @Override
                public boolean isSatisfied() throws Exception {
                    return getWithoutAssert(fullPathKey(String.valueOf(iter)), null, HttpStatus.SC_NOT_FOUND, true, "performAsync", "true");
                }
            }, 5000, 10);
        }

        put(fullPathKey(KEY_A), KEY_A, "application/octet-stream");
        put(fullPathKey(KEY_B), KEY_B, "application/octet-stream");
        delete(fullPathKey(null), HttpStatus.SC_OK, "performAsync", "true");

        eventually(new Condition() {
            @Override
            public boolean isSatisfied() throws Exception {
                return getWithoutAssert(fullPathKey(KEY_A), null, HttpStatus.SC_NOT_FOUND, true) &&
                       getWithoutAssert(fullPathKey(KEY_B), null, HttpStatus.SC_NOT_FOUND, true);
            }
        }, 5000, 10);
    }
}
