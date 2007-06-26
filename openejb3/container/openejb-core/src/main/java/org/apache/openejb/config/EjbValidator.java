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
package org.apache.openejb.config;

import java.io.InputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.util.Vector;

import org.apache.openejb.OpenEJBException;
import org.apache.openejb.jee.EjbRef;
import org.apache.openejb.jee.EnvEntry;
import org.apache.openejb.jee.SecurityRoleRef;
import org.apache.openejb.jee.ResourceRef;
import org.apache.openejb.jee.EjbLocalRef;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.config.rules.CheckClasses;
import org.apache.openejb.config.rules.CheckMethods;
import org.apache.openejb.util.Messages;
import org.apache.openejb.util.Logger;
import org.apache.openejb.util.OpenEjbVersion;

public class EjbValidator {
    private static final String helpBase = "META-INF/org.apache.openejb.cli/";

    protected static final Messages _messages = new Messages("org.apache.openejb.config.rules");

    int LEVEL = 2;
    boolean PRINT_DETAILS = false;
    boolean PRINT_XML = false;
    boolean PRINT_WARNINGS = true;
    boolean PRINT_COUNT = false;

    private Vector sets = new Vector();

    /*------------------------------------------------------*/
    /*    Constructors                                      */
    /*------------------------------------------------------*/
    public EjbValidator() throws OpenEJBException {
    }

    public void addEjbSet(EjbSet set) {
        sets.add(set);
    }

    public EjbSet[] getEjbSets() {
        EjbSet[] ejbSets = new EjbSet[sets.size()];
        sets.copyInto(ejbSets);
        return ejbSets;
    }

    public EjbSet validateJar(final EjbModule ejbModule) {
        EjbSet set = null;

        try {
            set = new EjbSet(ejbModule.getJarLocation(), ejbModule.getEjbJar(), ejbModule.getClassLoader());
            ValidationRule[] rules = getValidationRules();
            for (int i = 0; i < rules.length; i++) {
                rules[i].validate(set);
            }
        } catch (Throwable e) {
            e.printStackTrace(System.out);
            ValidationError err = new ValidationError("cannot.validate");
            err.setCause(e);
            err.setDetails(e.getMessage());
            set.addError(err);
        }
        return set;
    }

    protected ValidationRule[] getValidationRules() {
        ValidationRule[] rules = new ValidationRule[]{
                new CheckClasses(),
                new CheckMethods()
        };
        return rules;
    }

    public void printResults(EjbSet set) {
        if (!set.hasErrors() && !set.hasFailures() && (!PRINT_WARNINGS || !set.hasWarnings())) {
            return;
        }
        System.out.println("------------------------------------------");
        System.out.println("JAR " + set.getJarPath());
        System.out.println("                                          ");

        printValidationExceptions(set.getErrors());
        printValidationExceptions(set.getFailures());

        if (PRINT_WARNINGS) {
            printValidationExceptions(set.getWarnings());
        }
    }

    protected void printValidationExceptions(ValidationException[] exceptions) {
        for (int i = 0; i < exceptions.length; i++) {
            System.out.print(" ");
            System.out.print(exceptions[i].getPrefix());
            System.out.print(" ... ");
            if (!(exceptions[i] instanceof ValidationError)) {
                System.out.print(exceptions[i].getComponentName());
                System.out.print(": ");
            }
            if (LEVEL > 2) {
                System.out.println(exceptions[i].getMessage(1));
                System.out.println();
                System.out.print('\t');
                System.out.println(exceptions[i].getMessage(LEVEL));
                System.out.println();
            } else {
                System.out.println(exceptions[i].getMessage(LEVEL));
            }
        }
        if (PRINT_COUNT && exceptions.length > 0) {
            System.out.println();
            System.out.print(" " + exceptions.length + " ");
            System.out.println(exceptions[0].getCategory());
            System.out.println();
        }

    }

    public void printResultsXML(EjbSet set) {
        if (!set.hasErrors() && !set.hasFailures() && (!PRINT_WARNINGS || !set.hasWarnings())) {
            return;
        }

        System.out.println("<jar>");
        System.out.print("  <path>");
        System.out.print(set.getJarPath());
        System.out.println("</path>");

        printValidationExceptionsXML(set.getErrors());
        printValidationExceptionsXML(set.getFailures());

        if (PRINT_WARNINGS) {
            printValidationExceptionsXML(set.getWarnings());
        }
        System.out.println("</jar>");
    }

    protected void printValidationExceptionsXML(ValidationException[] exceptions) {
        for (int i = 0; i < exceptions.length; i++) {
            System.out.print("    <");
            System.out.print(exceptions[i].getPrefix());
            System.out.println(">");
            if (!(exceptions[i] instanceof ValidationError)) {
                System.out.print("      <ejb-name>");
                System.out.print(exceptions[i].getComponentName());
                System.out.println("</ejb-name>");
            }
            System.out.print("      <summary>");
            System.out.print(exceptions[i].getMessage(1));
            System.out.println("</summary>");
            System.out.println("      <description><![CDATA[");
            System.out.println(exceptions[i].getMessage(3));
            System.out.println("]]></description>");
            System.out.print("    </");
            System.out.print(exceptions[i].getPrefix());
            System.out.println(">");
        }
    }

    public void displayResults(EjbSet[] sets) {
        if (PRINT_XML) {
            System.out.println("<results>");
            for (int i = 0; i < sets.length; i++) {
                printResultsXML(sets[i]);
            }
            System.out.println("</results>");
        } else {
            for (int i = 0; i < sets.length; i++) {
                printResults(sets[i]);
            }
            for (int i = 0; i < sets.length; i++) {
                if (sets[i].hasErrors() || sets[i].hasFailures()) {
                    if (LEVEL < 3) {
                        System.out.println();
                        System.out.println("For more details, use the -vvv option");
                    }
                    i = sets.length;
                }
            }
        }

    }

    /*------------------------------------------------------*/
    /*    Static methods                                    */
    /*------------------------------------------------------*/

    private static void printVersion() {
        /*
         * Output startup message
         */

        OpenEjbVersion versionInfo = OpenEjbVersion.get();
        System.out.println("OpenEJB EJB Validation Tool " + versionInfo.getVersion() + "    build: " + versionInfo.getDate() + "-" + versionInfo.getTime());
        System.out.println("" + versionInfo.getUrl());
    }

    private static void printHelp() {
        String header = "OpenEJB EJB Validation Tool ";
        OpenEjbVersion versionInfo = OpenEjbVersion.get();
        header += versionInfo.getVersion();

        System.out.println(header);

        try {
            InputStream in = Thread.currentThread().getContextClassLoader().getResource(helpBase + "validate.help").openConnection().getInputStream();

            int b = in.read();
            while (b != -1) {
                System.out.write(b);
                b = in.read();
            }
        } catch (java.io.IOException e) {
        }
    }

    private static void printExamples() {
        String header = "OpenEJB EJB Validation Tool ";
        OpenEjbVersion versionInfo = OpenEjbVersion.get();
        header += versionInfo.getVersion();

        System.out.println(header);

        try {
            InputStream in = Thread.currentThread().getContextClassLoader().getResource(helpBase + "validate.examples").openConnection().getInputStream();

            int b = in.read();
            while (b != -1) {
                System.out.write(b);
                b = in.read();
            }
        } catch (java.io.IOException e) {
        }
    }

    public static void main(String args[]) {
        try {
            File directory = SystemInstance.get().getHome().getDirectory("lib");
            SystemInstance.get().getClassPath().addJarsToPath(directory);
            File directory1 = SystemInstance.get().getHome().getDirectory("dist");
            SystemInstance.get().getClassPath().addJarsToPath(directory1);
        } catch (Exception e) {

        }
        Logger.initialize(SystemInstance.get().getProperties());

        try {
            EjbValidator v = new EjbValidator();

            if (args.length == 0) {
                printHelp();
                return;
            }

            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-v")) {
                    v.LEVEL = 1;
                } else if (args[i].equals("-vv")) {
                    v.LEVEL = 2;
                } else if (args[i].equals("-vvv")) {
                    v.LEVEL = 3;
                } else if (args[i].equals("-nowarn")) {
                    v.PRINT_WARNINGS = false;
                } else if (args[i].equals("-xml")) {
                    v.PRINT_XML = true;
                } else if (args[i].equals("--help")) {
                    printHelp();
                } else if (args[i].equals("-examples")) {
                    printExamples();
                } else if (args[i].equals("-version")) {
                    printVersion();
                } else {

                    for (; i < args.length; i++) {
                        try {
                            EjbJarUtils ejbJarUtils = new EjbJarUtils(args[i]);
                            String jarLocation = ejbJarUtils.getJarLocation();
                            ClassLoader classLoader = null;
                            try {
                                File jarFile = new File(jarLocation);
                                URL[] classpath = new URL[]{jarFile.toURL()};
                                classLoader = new URLClassLoader(classpath, EjbValidator.class.getClassLoader());
                            } catch (MalformedURLException e) {
                                throw new OpenEJBException("Unable to create a classloader to load classes from '" + jarLocation + "'", e);
                            }
                            final EjbModule ejbModule = new EjbModule(classLoader, ejbJarUtils.getJarLocation(), ejbJarUtils.getEjbJar(), ejbJarUtils.getOpenejbJar());
                            EjbSet set = v.validateJar(ejbModule);
                            v.addEjbSet(set);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            EjbSet[] sets = v.getEjbSets();
            v.displayResults(sets);

            for (int i = 0; i < sets.length; i++) {
                if (sets[i].hasErrors() || sets[i].hasFailures()) {
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());

        }
    }

}