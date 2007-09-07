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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.config;

import org.apache.openejb.OpenEJBException;
import org.apache.openejb.util.*;
import org.xml.sax.XMLReader;
import org.xml.sax.InputSource;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.bind.*;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.sax.SAXSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @version $Rev$ $Date$
 */
public class JaxbUnmarshaller {

    public static final org.apache.openejb.util.Messages messages = new Messages("org.apache.openejb.util.resources");
    public static final Logger logger = Logger.getInstance(LogCategory.OPENEJB_STARTUP, "org.apache.openejb.util.resources");
    
    private final File xmlFile;

    private javax.xml.bind.Unmarshaller unmarshaller;

    public JaxbUnmarshaller(Class<?> type, String xmlFileName) throws OpenEJBException {
        this.xmlFile = new File(xmlFileName);
        try {
            JAXBContext ctx = JAXBContext.newInstance(type);
            unmarshaller = ctx.createUnmarshaller();
        } catch (JAXBException e) {
            throw new OpenEJBException("Could not create a JAXBContext for class " + type.getName(), e);
        }
    }

    public static Object unmarshal(Class<?> clazz, String xmlFile, String jarLocation) throws OpenEJBException {
        return new JaxbUnmarshaller(clazz, xmlFile).unmarshal(jarLocation);
    }


    public static Object unmarshal(Class<?> clazz, String xmlFile) throws OpenEJBException {
        try {
            if (xmlFile.startsWith("jar:")) {
                URL url = new URL(xmlFile);
                xmlFile = url.getFile();
            }
            if (xmlFile.startsWith("file:")) {
                URL url = new URL(xmlFile);
                xmlFile = url.getFile();
            }
        } catch (MalformedURLException e) {
            throw new OpenEJBException("Unable to resolve location " + xmlFile, e);
        }

        String jarLocation = null;
        int jarSeparator = xmlFile.indexOf("!");
        if (jarSeparator > 0) {
            jarLocation = xmlFile.substring(0, jarSeparator);
            xmlFile = xmlFile.substring(jarSeparator + 2);
        } else {
            File file = new File(xmlFile);
            xmlFile = file.getName();
            jarLocation = file.getParent();
        }

        return new JaxbUnmarshaller(clazz, xmlFile).unmarshal(jarLocation);
    }

    public Object unmarshal(String location) throws OpenEJBException {
        File file = new File(location);
        if (file.isDirectory()) {
            return unmarshalFromDirectory(file);
        } else {
            return unmarshalFromJar(file);
        }
    }

    public Object unmarshalFromJar(File jarFile) throws OpenEJBException {
        String jarLocation = jarFile.getPath();
        String file = xmlFile.getName();

        JarFile jar = JarUtils.getJarFile(jarLocation);
        JarEntry entry = jar.getJarEntry(xmlFile.getPath().replaceAll("\\\\", "/"));

        if (entry == null)
            throw new OpenEJBException(messages.format("xml.cannotFindFile", file, jarLocation));

        Reader reader = null;
        InputStream stream = null;

        try {
            stream = jar.getInputStream(entry);
            reader = new InputStreamReader(stream);
            return unmarshalObject(reader, file, jarLocation);
        } catch (IOException e) {
            throw new OpenEJBException(messages.format("xml.cannotRead", file, jarLocation, e.getLocalizedMessage()), e);
        } finally {
            try {
                if (stream != null) stream.close();
                if (reader != null) reader.close();
                if (jar != null) jar.close();
            } catch (Exception e) {
                throw new OpenEJBException(messages.format("file.0020", jarLocation, e.getLocalizedMessage()), e);
            }
        }
    }

    public Object unmarshalFromDirectory(File directory) throws OpenEJBException {
        String file = xmlFile.getPath();

        Reader reader = null;
        InputStream stream = null;

        try {
            File fullPath = new File(directory, file);
            stream = new FileInputStream(fullPath);
            reader = new InputStreamReader(stream);
            return unmarshalObject(reader, file, directory.getPath());
        } catch (FileNotFoundException e) {
            throw new OpenEJBException(messages.format("xml.cannotFindFile", file, directory.getPath()), e);
        } finally {
            try {
                if (stream != null) stream.close();
                if (reader != null) reader.close();
            } catch (Exception e) {
                throw new OpenEJBException(messages.format("file.0020", directory.getPath(), e.getLocalizedMessage()), e);
            }
        }
    }

    public Object unmarshal(URL url) throws OpenEJBException {
        String fileName = xmlFile.getName();

        Reader reader = null;
        InputStream stream = null;

        try {
            URL fullURL = url;
            if (!url.toExternalForm().endsWith(fileName)) {
                fullURL = new URL(url, fileName);
            }
            stream = fullURL.openConnection().getInputStream();
            reader = new InputStreamReader(stream);
            return unmarshalObject(reader, fileName, fullURL.getPath());
        } catch (MalformedURLException e) {
            throw new OpenEJBException(messages.format("xml.cannotFindFile", fileName, url.getPath()), e);
        } catch (IOException e) {
            throw new OpenEJBException(messages.format("xml.cannotRead", fileName, url.getPath(), e.getLocalizedMessage()), e);
        } finally {
            try {
                if (stream != null) stream.close();
                if (reader != null) reader.close();
            } catch (Exception e) {
                throw new OpenEJBException(messages.format("file.0020", url.getPath(), e.getLocalizedMessage()), e);
            }
        }
    }

    private Object unmarshalObject(Reader reader, String file, String jarLocation) throws OpenEJBException {
        try {
            // create a new XML parser
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(true);
            SAXParser parser = factory.newSAXParser();

            // Create a filter to intercept events
            NamespaceFilter xmlFilter = new NamespaceFilter(parser.getXMLReader());

            // Be sure the filter has the JAXB content handler set (or it wont
            // work)
            xmlFilter.setContentHandler(unmarshaller.getUnmarshallerHandler());

            SAXSource source = new SAXSource(xmlFilter, new InputSource(reader));

            Object object = unmarshaller.unmarshal(source);
            if (object instanceof JAXBElement) {
                JAXBElement<?> element = (JAXBElement) object;
                object = element.getValue();
            }
            return object;
        } catch (JAXBException e) {
            e.printStackTrace();
            throw new OpenEJBException(messages.format("xml.cannotUnmarshal", file, jarLocation, e.getLocalizedMessage()), e);
        } catch (ParserConfigurationException e) {
            throw new OpenEJBException(messages.format("xml.cannotUnmarshal", file, jarLocation, e.getLocalizedMessage()), e);
        } catch (SAXException e) {
            throw new OpenEJBException(messages.format("xml.cannotUnmarshal", file, jarLocation, e.getLocalizedMessage()), e);
        }
    }

    public static class NamespaceFilter extends XMLFilterImpl {

        public NamespaceFilter(XMLReader xmlReader) {
            super(xmlReader);
        }

        public void startElement(String arg0, String arg1, String arg2, Attributes arg3) throws SAXException {
            if (arg0.equals("http://www.openejb.org/openejb-jar/1.1")){
                super.startElement(arg0, arg1, arg2, arg3);
            } else {
                super.startElement("http://java.sun.com/xml/ns/javaee", arg1, arg2, arg3);
            }
        }
    }

}
