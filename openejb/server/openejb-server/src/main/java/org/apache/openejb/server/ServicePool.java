/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.server;

import org.apache.openejb.loader.Options;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.util.LogCategory;
import org.apache.openejb.util.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServicePool implements ServerService {
    private static final Logger log = Logger.getInstance(LogCategory.SERVICEPOOL, "org.apache.openejb.util.resources");

    private final ServerService next;
    private final Executor executor;
    private final ThreadPoolExecutor threadPool;
    private final AtomicBoolean stop = new AtomicBoolean();

    public ServicePool(final ServerService next, final String name, final Properties properties) {
        this(next, name, new Options(properties).get("threads", 100));
    }

    public ServicePool(final ServerService next, final String name, final int threads) {
        this.next = next;

        final int keepAliveTime = (1000 * 60 * 5);

        threadPool = new ThreadPoolExecutor(threads, threads, keepAliveTime, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        threadPool.setThreadFactory(new ThreadFactory() {
            private volatile int id = 0;

            @Override
            public Thread newThread(final Runnable arg0) {
                return new Thread(arg0, name + " " + getNextID());
            }

            private int getNextID() {
                return id++;
            }

        });

        executor = threadPool;
        SystemInstance.get().setComponent(ServicePool.class, this);
    }

    public ServicePool(final ServerService next, final Executor executor) {
        this.next = next;
        this.executor = executor;
        this.threadPool = null;
    }

    public ThreadPoolExecutor getThreadPool() {
        return threadPool;
    }

    @Override
    public void service(final InputStream in, final OutputStream out) throws ServiceException, IOException {
    }

    @Override
    public void service(final Socket socket) throws ServiceException, IOException {
        final Runnable service = new Runnable() {
            @Override
            public void run() {
                try {
                    if (stop.get()) return;
                    next.service(socket);
                } catch (SecurityException e) {
                    log.error("Security error: " + e.getMessage(), e);
                } catch (IOException e) {
                    log.debug("Unexpected IO error", e);
                } catch (Throwable e) {
                    log.error("Unexpected error", e);
                } finally {
                    try {
                        // Once the thread is done with the socket, clean it up
                        // The ServiceDaemon does not close the sockets as it is
                        // single threaded and only accepts sockets and then
                        // hands them off to be proceeceed.  As the thread doing
                        // that processing it is our job to close the socket
                        // when we are finished with it.
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (Throwable t) {
                        log.warning("Error while closing connection with client", t);
                    }
                }
            }
        };

        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        final Runnable ctxCL = new Runnable() {
            @Override
            public void run() {
                final ClassLoader cl = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(tccl);
                try {
                    service.run();
                } finally {
                    Thread.currentThread().setContextClassLoader(cl);
                }
            }
        };

        executor.execute(ctxCL);
    }

    /**
     * Pulls out the access log information
     *
     * @param props Properties
     * @throws ServiceException
     */
    @Override
    public void init(final Properties props) throws Exception {
        // Do our stuff

        // Then call the next guy
        next.init(props);
    }

    @Override
    public void start() throws ServiceException {
        // Do our stuff

        // Then call the next guy
        next.start();
    }

    @Override
    public void stop() throws ServiceException {
        // Do our stuff

        // Then call the next guy
        next.stop();
    }


    /**
     * Gets the name of the service.
     * Used for display purposes only
     */
    @Override
    public String getName() {
        return next.getName();
    }

    /**
     * Gets the ip number that the
     * daemon is listening on.
     */
    @Override
    public String getIP() {
        return next.getIP();
    }

    /**
     * Gets the port number that the
     * daemon is listening on.
     */
    @Override
    public int getPort() {
        return next.getPort();
    }

}
