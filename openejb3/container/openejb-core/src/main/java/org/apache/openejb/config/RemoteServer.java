/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.config;

import org.apache.openejb.util.Join;
import org.apache.openejb.util.Pipe;

import java.io.File;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @version $Rev$ $Date$
 */
public class RemoteServer {
    private static final boolean DEBUG = System.getProperty("openejb.server.debug","false").equalsIgnoreCase("TRUE");
    private static final boolean TOMCAT;
    static {
        File home = getHome();
        TOMCAT = (home != null) && (new File(new File(home, "bin"), "catalina.sh").exists());
    }

    /**
     * Has the remote server's instance been already running ?
     */
    private boolean serverHasAlreadyBeenStarted = true;

    private Properties properties;
    private Process server;
    private final int tries;
    private final boolean verbose;

    public RemoteServer() {
        this(10, false);
    }

    public RemoteServer(int tries, boolean verbose) {
        this.tries = tries;
        this.verbose = verbose;
    }

    public void init(Properties props) {
        properties = props;

        props.put("java.naming.factory.initial", "org.apache.openejb.client.RemoteInitialContextFactory");
        props.put("java.naming.provider.url", "127.0.0.1:4201");
        props.put("java.naming.security.principal", "testuser");
        props.put("java.naming.security.credentials", "testpassword");
    }

    public static void main(String[] args) {
        assert args.length > 0 : "no arguments supplied: valid argumen -efts are 'start' or 'stop'";
        if (args[0].equalsIgnoreCase("start")){
            new RemoteServer().start();
        } else if (args[0].equalsIgnoreCase("stop")) {
            RemoteServer remoteServer = new RemoteServer();
            remoteServer.serverHasAlreadyBeenStarted = false;
            remoteServer.stop();
        } else {
            throw new RuntimeException("valid arguments are 'start' or 'stop'");
        }
    }
    public Properties getProperties() {
        return properties;
    }

    public void destroy() {
        stop();
    }

    public void start() {
        if (!connect()) {
            try {
                System.out.println("[] START SERVER");

                File home = getHome();
                System.out.println("OPENEJB_HOME = "+home.getAbsolutePath());
                String systemInfo = "Java " + System.getProperty("java.version") + "; " + System.getProperty("os.name") + "/" + System.getProperty("os.version");
                System.out.println("SYSTEM_INFO  = "+systemInfo);

                serverHasAlreadyBeenStarted = false;

                File openejbJar = null;
                File javaagentJar = null;

                File lib;
                if (!TOMCAT) {
                    lib = new File(home, "lib");
                } else {
                    lib = new File(new File(new File(home, "webapps"), "openejb"), "lib");
                }
                
                for (File file : lib.listFiles()) {
                    if (file.getName().startsWith("openejb-core") && file.getName().endsWith("jar")){
                        openejbJar = file;
                    }
                    if (file.getName().startsWith("openejb-javaagent") && file.getName().endsWith("jar")){
                        javaagentJar = file;
                    }
                }

                if (openejbJar == null){
                    throw new IllegalStateException("Cannot find the openejb-core jar in "+lib.getAbsolutePath());
                }
                if (javaagentJar == null){
                    throw new IllegalStateException("Cannot find the openejb-javaagent jar in "+lib.getAbsolutePath());
                }

                //File openejbJar = new File(lib, "openejb-core-" + version + ".jar");

                //DMB: If you don't use an array, you get problems with jar paths containing spaces
                // the command won't parse correctly
                String[] args;
                if (!TOMCAT) {
                    if (DEBUG) {
                        args = new String[]{"java",
                                "-XX:+HeapDumpOnOutOfMemoryError",
                                "-Xdebug",
                                "-Xnoagent",
                                "-Djava.compiler=NONE",
                                "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",

                                "-javaagent:" + javaagentJar.getAbsolutePath(),

                                "-jar", openejbJar.getAbsolutePath(), "start"
                        };
                    } else {
                        args = new String[]{"java",
                                "-XX:+HeapDumpOnOutOfMemoryError",
                                "-javaagent:" + javaagentJar.getAbsolutePath(),
                                "-jar", openejbJar.getAbsolutePath(), "start"
                        };
                    }
                } else {
                    File bin = new File(home, "bin");
                    File tlib = new File(home, "lib");
                    File bootstrapJar = new File(bin, "bootstrap.jar");
                    File juliJar = new File(bin, "tomcat-juli.jar");
                    File commonsLoggingJar = new File(bin, "commons-logging-api.jar");

                    File conf = new File(home, "conf");
                    File loggingProperties = new File(conf, "logging.properties");

                    File endorsed = new File(home, "endorsed");
                    File temp = new File(home, "temp");

                    List<String> argsList = new ArrayList<String>() {};
                    argsList.add("java");
                    argsList.add("-XX:+HeapDumpOnOutOfMemoryError");

                    if (DEBUG) {
                        argsList.add("-Xdebug");
                        argsList.add("-Xnoagent");
                        argsList.add("-Djava.compiler=NONE");
                        argsList.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005");
                    }

                    argsList.add("-javaagent:" + javaagentJar.getAbsolutePath());
                    argsList.add("-Dcom.sun.management.jmxremote");
                    argsList.add("-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager");
                    argsList.add("-Djava.util.logging.config.file=" + loggingProperties.getAbsolutePath());
                    argsList.add("-Djava.io.tmpdir=" + temp.getAbsolutePath());
                    argsList.add("-Djava.endorsed.dirs=" + endorsed.getAbsolutePath());
                    argsList.add("-Dcatalina.base=" + home.getAbsolutePath());
                    argsList.add("-Dcatalina.home=" + home.getAbsolutePath());
                    argsList.add("-Dcatalina.ext.dirs=" + tlib.getAbsolutePath());
                    argsList.add("-Dopenejb.servicemanager.enabled=" + Boolean.getBoolean("openejb.servicemanager.enabled"));
                    argsList.add("-classpath");

                    if (commonsLoggingJar.exists()) {
                        argsList.add(bootstrapJar.getAbsolutePath() + ":" + juliJar.getAbsolutePath() + ":" + commonsLoggingJar.getAbsolutePath());

                    } else {
                        argsList.add(bootstrapJar.getAbsolutePath() + ":" + juliJar.getAbsolutePath());
                    }

                    argsList.add("org.apache.catalina.startup.Bootstrap");
                    argsList.add("start");

                    args = argsList.toArray(new String[argsList.size()]);
                }


                if (verbose) {
                    System.out.println(Join.join("\n", args));
                }
                server = Runtime.getRuntime().exec(args);

                Pipe.pipe(server);

            } catch (Exception e) {
                throw (RuntimeException)new RuntimeException("Cannot start the server.  Exception: "+e.getClass().getName()+": "+e.getMessage()).initCause(e);
            }
            if (DEBUG) {
                if (!connect(Integer.MAX_VALUE)) throw new RuntimeException("Could not connect to server");
            } else {
                if (!connect(tries)) throw new RuntimeException("Could not connect to server");
            }
        } else {
            if (verbose) System.out.println("[] FOUND STARTED SERVER");
        }
    }

    private static File getHome() {
        String openejbHome = System.getProperty("openejb.home");

        if (openejbHome != null) {
            return new File(openejbHome);
        } else {
            return null;
        }
    }

    public void stop() {
        if (!serverHasAlreadyBeenStarted) {
            try {
                System.out.println("[] STOP SERVER");

                int port;
                String command;
                if (!TOMCAT) {
                    port = 4200;
                    command = "Stop";
                } else {
                    port = 8005;
                    command = "SHUTDOWN";
                }

                Socket socket = new Socket("localhost", port);
                OutputStream out = socket.getOutputStream();
                out.write(command.getBytes());

                if (server != null) {
                    server.waitFor();
                    server = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean connect() {
        return connect(1);
    }

    private boolean connect(int tries) {
        if (verbose) System.out.println("[] CONNECT ATTEMPT " + (this.tries - tries));
        //System.out.println("CONNECT "+ tries);
        try {
            int port;
            if (!TOMCAT) {
                port = 4200;
            } else {
                port = 8005;
            }

            Socket socket = new Socket("localhost", port);
            OutputStream out = socket.getOutputStream();
            out.close();
            if (verbose) System.out.println("[] CONNECTED IN " + (this.tries - tries));
        } catch (Exception e) {
            //System.out.println(e.getMessage());
            if (tries < 2) {
                if (verbose) System.out.println("[] CONNECT ATTEMPTS FAILED ( " + (this.tries - tries) + " tries)");
                return false;
            } else {
                try {
                    Thread.sleep(2000);
                } catch (Exception e2) {
                    e.printStackTrace();
                }
                return connect(--tries);
            }
        }

        return true;
    }
}
