/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.ning.http.client.async;

import static com.ning.http.util.DateUtils.millisTime;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.Response;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Per request timeout configuration test.
 * 
 * @author Hubert Iwaniuk
 */
public abstract class PerRequestTimeoutTest extends AbstractBasicTest {
    private static final String MSG = "Enough is enough.";

    protected abstract void checkTimeoutMessage(String message);

    @Override
    public AbstractHandler configureHandler() throws Exception {
        return new SlowHandler();
    }

    private class SlowHandler extends AbstractHandler {
        public void handle(String target, Request baseRequest, HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
            response.setStatus(HttpServletResponse.SC_OK);
            final Continuation continuation = ContinuationSupport.getContinuation(request);
            continuation.suspend();
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(1500);
                        response.getOutputStream().print(MSG);
                        response.getOutputStream().flush();
                    } catch (InterruptedException e) {
                        log.error(e.getMessage(), e);
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }).start();
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(3000);
                        response.getOutputStream().print(MSG);
                        response.getOutputStream().flush();
                        continuation.complete();
                    } catch (InterruptedException e) {
                        log.error(e.getMessage(), e);
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }).start();
            baseRequest.setHandled(true);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testRequestTimeout() throws IOException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            Future<Response> responseFuture = client.prepareGet(getTargetUrl()).setRequestTimeout(100).execute();
            Response response = responseFuture.get(2000, TimeUnit.MILLISECONDS);
            assertNull(response);
        } catch (InterruptedException e) {
            fail("Interrupted.", e);
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof TimeoutException);
            checkTimeoutMessage(e.getCause().getMessage());
        } catch (TimeoutException e) {
            fail("Timeout.", e);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testGlobalDefaultPerRequestInfiniteTimeout() throws IOException {
        try (AsyncHttpClient client = getAsyncHttpClient(new AsyncHttpClientConfig.Builder().setRequestTimeout(100).build())) {
            Future<Response> responseFuture = client.prepareGet(getTargetUrl()).setRequestTimeout(-1).execute();
            Response response = responseFuture.get();
            assertNotNull(response);
        } catch (InterruptedException e) {
            fail("Interrupted.", e);
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof TimeoutException);
            checkTimeoutMessage(e.getCause().getMessage());
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testGlobalRequestTimeout() throws IOException {
        try (AsyncHttpClient client = getAsyncHttpClient(new AsyncHttpClientConfig.Builder().setRequestTimeout(100).build())) {
            Future<Response> responseFuture = client.prepareGet(getTargetUrl()).execute();
            Response response = responseFuture.get(2000, TimeUnit.MILLISECONDS);
            assertNull(response);
        } catch (InterruptedException e) {
            fail("Interrupted.", e);
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof TimeoutException);
            checkTimeoutMessage(e.getCause().getMessage());
        } catch (TimeoutException e) {
            fail("Timeout.", e);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testGlobalIdleTimeout() throws IOException {
        final long times[] = new long[] { -1, -1 };

        try (AsyncHttpClient client = getAsyncHttpClient(new AsyncHttpClientConfig.Builder().setPooledConnectionIdleTimeout(2000).build())) {
            Future<Response> responseFuture = client.prepareGet(getTargetUrl()).execute(new AsyncCompletionHandler<Response>() {
                @Override
                public Response onCompleted(Response response) throws Exception {
                    return response;
                }

                @Override
                public STATE onBodyPartReceived(HttpResponseBodyPart content) throws Exception {
                    times[0] = millisTime();
                    return super.onBodyPartReceived(content);
                }

                @Override
                public void onThrowable(Throwable t) {
                    times[1] = millisTime();
                    super.onThrowable(t);
                }
            });
            Response response = responseFuture.get();
            assertNotNull(response);
            assertEquals(response.getResponseBody(), MSG + MSG);
        } catch (InterruptedException e) {
            fail("Interrupted.", e);
        } catch (ExecutionException e) {
            log.info(String.format("\n@%dms Last body part received\n@%dms Connection killed\n %dms difference.", times[0], times[1], (times[1] - times[0])));
            fail("Timeouted on idle.", e);
        }
    }
}
