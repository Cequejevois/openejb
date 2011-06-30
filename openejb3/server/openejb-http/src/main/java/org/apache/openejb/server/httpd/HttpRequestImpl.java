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
package org.apache.openejb.server.httpd;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import org.apache.openejb.util.ArrayEnumeration;
import org.apache.openejb.util.OpenEjbVersion;

/**
 * A class to take care of HTTP Requests.  It parses headers, content, form and url
 * parameters.
 */
public class HttpRequestImpl implements HttpRequest {
    private static final String FORM_URL_ENCODED = "application/x-www-form-urlencoded";
    private static final String TRANSFER_ENCODING = "Transfer-Encoding";
    private static final String CHUNKED = "chunked";
    protected static final String EJBSESSIONID = "EJBSESSIONID";

    /**
     * 5.1.1    Method
     */
    private Method method;

    /**
     * 5.1.2    Request-URI
     */
    private URI uri;

    /**
     * the headers for this page
     */
    private final Map<String,String> headers = new HashMap<String,String>();

    /**
     * the form parameters for this page
     */
    private final Map<String,String> formParams = new HashMap<String,String>();

    /**
     * the URL (or query) parameters for this page
     */
    private final Map<String,String> queryParams = new HashMap<String,String>();

    /**
     * All form and query parameters.  Query parameters override form parameters.
     */
    private final Map<String,String> parameters = new HashMap<String,String>();

    /**
     * Cookies sent from the client
     */
    private List<Cookie> cookies;

    /**
     * the content of the body of the request
     */
    private byte[] body;
    private InputStream in;
    private int length;
    private String contentType;

    /**
     * the address the request came in on
     */
    private final URI socketURI;

    /**
     * Request scoped data which is set and used by application code.
     */
    private final Map<String,Object> attributes = new HashMap<String,Object>();
    private String sessionId;

    public HttpRequestImpl(URI socketURI) {
        this.socketURI = socketURI;
    }

    /**
     * Gets a header based the header name passed in.
     *
     * @param name The name of the header to get
     * @return The value of the header
     */
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override public Enumeration<String> getHeaderNames() {
        return new ArrayEnumeration(new ArrayList<String>(headers.keySet()));
    }

    @Override public Enumeration<String> getHeaders(String s) {
        return new ArrayEnumeration(Arrays.asList(headers.get(s)));
    }

    @Override public int getIntHeader(String s) {
        return Integer.parseInt(headers.get(s));
    }

    /**
     * Gets a form parameter based on the name passed in.
     *
     * @param name The name of the form parameter to get
     * @return The value of the parameter
     */
    public String getFormParameter(String name) {
        return formParams.get(name);
    }

    public Map<String,String> getFormParameters() {
        return new HashMap<String,String>(formParams);
    }

    public Map<String,String> getQueryParameters() {
        return new HashMap<String,String>(queryParams);
    }

    /**
     * Gets a URL (or query) parameter based on the name passed in.
     *
     * @param name The name of the URL (or query) parameter
     * @return The value of the URL (or query) parameter
     */
    public String getQueryParameter(String name) {
        return queryParams.get(name);
    }

    /**
     * Gets the request method.
     * @return the request method
     */
    public String getMethod() {
        return method.name();
    }

    @Override public Part getPart(String s) throws IOException, ServletException {
        throw new UnsupportedOperationException();
    }

    @Override public Collection<Part> getParts() throws IOException, ServletException {
        throw new UnsupportedOperationException();
    }

    @Override public String getPathInfo() {
        return "/";
    }

    @Override public String getPathTranslated() {
        return getRequestURI();
    }

    @Override public String getQueryString() {
        try {
            return uri.toURL().getQuery();
        } catch (MalformedURLException e) {
            return "";
        }
    }

    @Override public String getRemoteUser() {
        return "OpenEJB";
    }

    @Override public String getRequestedSessionId() {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        return sessionId;
    }

    @Override public String getRequestURI() {
        return getURI().toString();
    }

    @Override public StringBuffer getRequestURL() {
        return new StringBuffer(getRequestURI());
    }

    @Override public String getServletPath() {
        return "/";
    }

    /**
     * Gets the URI for the current URL page.
     *
     * @return the URI
     */
    public URI getURI() {
        return uri;
    }

    public int getContentLength() {
        return length;
    }

    public String getContentType() {
        return contentType;
    }

    @Override public DispatcherType getDispatcherType() {
        throw new UnsupportedOperationException();
    }

    public ServletInputStream getInputStream() throws IOException {
        return new ServletInputStream() {
            @Override public int read() throws IOException {
                return in.read();
            }
        };
    }

    @Override public String getLocalAddr() {
        return "127.0.0.1";
    }

    @Override public Locale getLocale() {
        return Locale.getDefault();
    }

    @Override public Enumeration<Locale> getLocales() {
        return new ArrayEnumeration(Arrays.asList(Locale.getAvailableLocales()));
    }

    @Override public String getLocalName() {
        return getLocale().getDisplayName();
    }

    @Override public int getLocalPort() {
        return 4204;
    }

    /*------------------------------------------------------------*/
    /*  Methods for reading in and parsing a request              */
    /*------------------------------------------------------------*/
    /**
     * parses the request into the 3 different parts, request, headers, and body
     *
     * @param input the data input for this page
     * @throws java.io.IOException if an exception is thrown
     */
    protected void readMessage(InputStream input) throws IOException {
        DataInput in = new DataInputStream(input);

        readRequestLine(in);
        readHeaders(in);
        readBody(in);

        parameters.putAll(this.getFormParameters());
        parameters.putAll(this.getQueryParameters());

        //temp-debug-------------------------------------------
        // System.out.println("******************* HEADERS ******************");
        // for (Map.Entry<String, String> entry : headers.entrySet()) {
        //    System.out.println(entry);
        // }
        // System.out.println("**********************************************");
        // System.out.println(new String(body));
        // System.out.println("**********************************************");
        //end temp-debug---------------------------------------
    }

    /**
     * reads and parses the request line
     *
     * @param in the input to be read
     * @throws java.io.IOException if an exception is thrown
     */
    private void readRequestLine(DataInput in) throws IOException {
        String line;
        try {
            line = in.readLine();
//            System.out.println(line);
        } catch (Exception e) {
            throw new IOException("Could not read the HTTP Request Line :"
                    + e.getClass().getName()
                    + " : "
                    + e.getMessage());
        }

        StringTokenizer lineParts = new StringTokenizer(line, " ");
        /* [1] Parse the method */
        parseMethod(lineParts);
        /* [2] Parse the URI */
        parseURI(lineParts);
    }

    /**
     * parses the method for this page
     *
     * @param lineParts a StringTokenizer of the request line
     * @throws java.io.IOException if an exeption is thrown
     */
    private void parseMethod(StringTokenizer lineParts) throws IOException {
        String token;
        try {
            token = lineParts.nextToken();
        } catch (Exception e) {
            throw new IOException("Could not parse the HTTP Request Method :"
                    + e.getClass().getName()
                    + " : "
                    + e.getMessage());
        }

        if (token.equalsIgnoreCase("GET")) {
            method = Method.GET;
        } else if (token.equalsIgnoreCase("POST")) {
            method = Method.POST;
        } else {
            method = Method.UNSUPPORTED;
            throw new IOException("Unsupported HTTP Request Method :" + token);
        }
    }

    /**
     * parses the URI into the different parts
     *
     * @param lineParts a StringTokenizer of the URI
     * @throws java.io.IOException if an exeption is thrown
     */
    private void parseURI(StringTokenizer lineParts) throws IOException {
        String token;
        try {
            token = lineParts.nextToken();
        } catch (Exception e) {
            throw new IOException("Could not parse the HTTP Request Method :"
                    + e.getClass().getName()
                    + " : "
                    + e.getMessage());
        }

        try {
            uri = new URI(socketURI.toString()+token);
        } catch (URISyntaxException e) {
            throw new IOException("Malformed URI :" + token + " Exception: " + e.getMessage());
        }

        parseQueryParams(uri.getQuery());
    }

    /**
     * parses the URL (or query) parameters
     *
     * @param query the URL (or query) parameters to be parsed
     */
    private void parseQueryParams(String query) {
        if (query == null)
            return;
        StringTokenizer parameters = new StringTokenizer(query, "&");

        while (parameters.hasMoreTokens()) {
            StringTokenizer param = new StringTokenizer(parameters.nextToken(), "=");

            /* [1] Parse the Name */
            if (!param.hasMoreTokens())
                continue;
            String name = null;
            try {
                name = URLDecoder.decode(param.nextToken(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                name = null;
            }
            if (name == null)
                continue;

            String value;
            /* [2] Parse the Value */
            if (!param.hasMoreTokens()){
                value = "";
            } else {
                try {
                    value = URLDecoder.decode(param.nextToken(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    value = "";
                }
            }

            //System.out.println("[] "+name+" = "+value);
            queryParams.put(name, value);
        }
    }

    /**
     * reads the headers from the data input sent from the browser
     *
     * @param in the data input sent from the browser
     * @throws java.io.IOException if an exeption is thrown
     */
    private void readHeaders(DataInput in) throws IOException {
//        System.out.println("\nREQUEST");
        while (true) {
            // Header Field
            String hf;

            try {
                hf = in.readLine();
                //System.out.println(hf);
            } catch (Exception e) {
                throw new IOException("Could not read the HTTP Request Header Field :"
                        + e.getClass().getName()
                        + " : "
                        + e.getMessage());
            }

            if (hf == null || hf.equals("")) {
                break;
            }

            /* [1] parse the name */
            int colonIndex = hf.indexOf((int) ':');
            String name = hf.substring(0, colonIndex);
            if (name == null)
                break;

            /* [2] Parse the Value */
            String value = hf.substring(colonIndex + 1, hf.length());
            if (value == null)
                break;
            value = value.trim();
            headers.put(name, value);
        }
        
        // Update the URI to be what the client sees the the server as.
        String host = headers.get("Host");
        if( host!=null ) {
            String hostName;
            int port = uri.getPort();
            int idx = host.indexOf(":");
            if( idx >= 0 ) {
                hostName = host.substring(0, idx);
                try {
                    port = Integer.parseInt(host.substring(idx+1));
                }
                catch (NumberFormatException ignore) {
                }
            } else {
                hostName = host;
            }
            
            try {
                uri = new URI(uri.getScheme(),
                        uri.getUserInfo(), hostName, port,
                        uri.getPath(), uri.getQuery(),
                        uri.getFragment());                
            } catch (URISyntaxException ignore) {
            }            
        }
        
        //temp-debug-------------------------------------------
        //java.util.Iterator myKeys = headers.keySet().iterator();
        //String temp = null;
        //while(myKeys.hasNext()) {
        //    temp = (String)myKeys.next();
        //    System.out.println("Test: " + temp + "=" + headers.get(temp));
        //}
        //end temp-debug---------------------------------------
    }

    /**
     * reads the body from the data input passed in
     *
     * @param in the data input with the body of the page
     * @throws java.io.IOException if an exception is thrown
     */
    private void readBody(DataInput in) throws IOException {
        //System.out.println("Body Length: " + body.length);
        // Content-type: application/x-www-form-urlencoded
        // or multipart/form-data
        length = parseContentLength();

        contentType = getHeader(HttpRequest.HEADER_CONTENT_TYPE);

        if (method == Method.POST && FORM_URL_ENCODED.equals(contentType)) {
            String rawParams;

            try {
                body = readContent(in);
                rawParams = new String(body);
            } catch (Exception e) {
                throw (IOException)new IOException("Could not read the HTTP Request Body: " + e.getMessage()).initCause(e);
            }

            StringTokenizer parameters = new StringTokenizer(rawParams, "&");
            String name;
            String value;

            while (parameters.hasMoreTokens()) {
                StringTokenizer param = new StringTokenizer(parameters.nextToken(), "=");

                /* [1] Parse the Name */
                name = URLDecoder.decode(param.nextToken(), "UTF-8");
                if (name == null)
                    break;

                /* [2] Parse the Value */
                if (param.hasMoreTokens()) {
                    value = URLDecoder.decode(param.nextToken(), "UTF-8");
                } else {
                    value = ""; //if there is no token set value to blank string
                }

                if (value == null)
                    value = "";

                formParams.put(name, value);
                    //System.out.println(name + ": " + value);
            }
        } else if (method == Method.POST && CHUNKED.equals(headers.get(TRANSFER_ENCODING))) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    // read the size line which is in hex
                    String sizeString = line.split(";", 2)[0];
                    int size = Integer.parseInt(sizeString, 16);

                    // if size is 0 we are done
                    if (size == 0) break;

                    // read the chunk and append to byte array
                    byte[] chunk = new byte[size];
                    in.readFully(chunk);
                    out.write(chunk);

                    // read off the trailing new line characters after the chunk
                    in.readLine();
                }
                body = out.toByteArray();
                this.in = new ByteArrayInputStream(body);
            } catch (Exception e) {
                throw (IOException)new IOException("Unable to read chunked body").initCause(e);
            }
        } else if (method == Method.POST){
            // TODO This really is terrible
            body = readContent(in);
            this.in = new ByteArrayInputStream(body);
        } else {
            body = new byte[0];
            this.in = new ByteArrayInputStream(body);
        }

    }

    private byte[] readContent(DataInput in) throws IOException {
        if (length >= 0) {
            byte[] body = new byte[length];
            in.readFully(body);
            return body;
        } else {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            try {
                boolean atLineStart = true;
                while (true) {
                    byte b = in.readByte();

                    if (b == '\r') {
                        // read the next byte
                        out.write(b);
                        b = in.readByte();
                    }

                    if (b == '\n') {
                        if (atLineStart) {
                            // blank line signals end of data
                            break;
                        }
                        atLineStart = true;
                    } else {
                        atLineStart = false;
                    }
                    out.write(b);
                }
            } catch (EOFException e) {
                // done reading
            }
            byte[] body = out.toByteArray();
            return body;
        }
    }

    private int parseContentLength() {
        // Content-length: 384
        String len = getHeader(HttpRequest.HEADER_CONTENT_LENGTH);
        //System.out.println("readRequestBody Content-Length: " + len);

        int length = -1;
        if (len != null) {
            try {
                length = Integer.parseInt(len);
            } catch (Exception e) {
                //don't care
            }
        }
        return length;
    }

    @Override public boolean authenticate(HttpServletResponse httpServletResponse) throws IOException, ServletException {
        return true;
    }

    @Override public String getAuthType() {
        return "BASIC";
    }

    @Override public String getContextPath() {
        return "/"; // TODO: check
    }

    @Override public Cookie[] getCookies() {
        if (cookies != null) return cookies.toArray(new Cookie[cookies.size()]);

        cookies = new ArrayList<Cookie>();

        String cookieHeader = getHeader(HEADER_COOKIE);
        if (cookieHeader == null) return cookies.toArray(new Cookie[cookies.size()]);

        StringTokenizer tokens = new StringTokenizer(cookieHeader, ";");
        while (tokens.hasMoreTokens()) {
            StringTokenizer token = new StringTokenizer(tokens.nextToken(), "=");
            String name = token.nextToken();
            String value = token.nextToken();
            cookies.add(new Cookie(name, value));
        }
        return cookies.toArray(new Cookie[cookies.size()]);
    }

    @Override public long getDateHeader(String s) {
        return Long.parseLong(headers.get(s));
    }

    protected String getCookie(String name) {
        for (Cookie cookie : getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public HttpSession getSession(boolean create) {
        return null;
    }

    @Override public Principal getUserPrincipal() {
        return new Principal() {
            @Override public String getName() {
                return "openejb";
            }
        };
    }

    @Override public boolean isRequestedSessionIdFromCookie() {
        return true;
    }

    @Override public boolean isRequestedSessionIdFromUrl() {
        return true;
    }

    @Override public boolean isRequestedSessionIdFromURL() {
        return true;
    }

    @Override public boolean isRequestedSessionIdValid() {
        return true;
    }

    @Override public boolean isUserInRole(String s) {
        return true;
    }

    @Override public void login(String s, String s1) throws ServletException {
        // no-op
    }

    @Override public void logout() throws ServletException {
        // no-op
    }

    public HttpSession getSession() {
        return getSession(true);
    }

    @Override public AsyncContext getAsyncContext() {
        throw new UnsupportedOperationException();
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override public Enumeration<String> getAttributeNames() {
        throw new UnsupportedOperationException();
    }

    @Override public String getCharacterEncoding() {
        return "UTF-8"; // TODO: modify it
    }

    public void setAttribute(String name, Object value){
        attributes.put(name, value);
    }

    @Override public void setCharacterEncoding(String s) throws UnsupportedEncodingException {
        throw new UnsupportedOperationException();
    }

    @Override public AsyncContext startAsync() {
        throw new UnsupportedOperationException();
    }

    @Override public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
        throw new UnsupportedOperationException();
    }

    public String getParameter(String name) {
        return parameters.get(name);
    }

    @Override public Map<String, String[]> getParameterMap() {
        throw new UnsupportedOperationException();
    }

    @Override public Enumeration<String> getParameterNames() {
        throw new UnsupportedOperationException();
    }

    @Override public String[] getParameterValues(String s) {
        throw new UnsupportedOperationException();
    }

    @Override public String getProtocol() {
        return "http";
    }

    @Override public BufferedReader getReader() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override public String getRealPath(String s) {
        throw new UnsupportedOperationException();
    }

    public Map<String,String> getParameters() {
        return new HashMap<String,String>(parameters);
    }

    public String getRemoteAddr() {
        return "127.0.0.1";
    }

    @Override public String getRemoteHost() {
        return getRemoteAddr();
    }

    @Override public int getRemotePort() {
        return 4204;
    }

    @Override public RequestDispatcher getRequestDispatcher(String s) {
        throw new UnsupportedOperationException();
    }

    @Override public String getScheme() {
        return "http";
    }

    @Override public String getServerName() {
        return "OpenEJB " + OpenEjbVersion.get().getVersion();
    }

    @Override public int getServerPort() {
        return 4204;
    }

    @Override public ServletContext getServletContext() {
        return null;
    }

    @Override public boolean isAsyncStarted() {
        return false;
    }

    @Override public boolean isAsyncSupported() {
        return false;
    }

    @Override public boolean isSecure() {
        return false;
    }

    @Override public void removeAttribute(String s) {
        throw new UnsupportedOperationException();
    }
}