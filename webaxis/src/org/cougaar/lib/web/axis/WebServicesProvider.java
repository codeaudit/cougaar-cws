/*
 * <copyright>
 *  
 *  Copyright 1997-2004 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects
 *  Agency (DARPA).
 * 
 *  You can redistribute this software and/or modify it under the
 *  terms of the Cougaar Open Source License as published on the
 *  Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *  
 * </copyright>
 */

package org.cougaar.lib.web.axis;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.axis.EngineConfiguration;
import org.apache.axis.WSDDEngineConfiguration;
import org.apache.axis.deployment.wsdd.WSDDDeployment;
import org.apache.axis.deployment.wsdd.WSDDDocument;
import org.apache.axis.server.AxisServer;
import org.apache.axis.transport.http.AxisServlet;
import org.apache.axis.utils.XMLUtils;

import org.cougaar.core.component.Component;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.node.NodeControlService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.ServletService;
import org.cougaar.core.service.WebServicesService;
import org.cougaar.util.GenericStateModelAdapter;

import org.w3c.dom.Document;

/**
 * This component advertises the {@link WebServicesService}
 * that uses the <a href="http://ws.apache.org/axis">Axis</a>
 * SOAP engine and a "/axis/services" {@link ServletService}
 * path.
 * <p>
 * Load with:<pre>
 *  &lt;component
 *    class="org.cougaar.lib.web.axis.WebServicesProvider"/&gt;
 * </pre>
 * <p>
 * Axis requires the following configuration files to be
 * installed in the $TOMCAT_HOME directory.  These are currently
 * included in the Cougaar "webtomcat" module:<pre>
 *   $CIP/webtomcat/data/webapps/ROOT/WEB-INF/server-config.wsdd
 *   $CIP/webtomcat/data/webapps/ROOT/WEB-INF/attachments
 * </pre> 
 */
public final class WebServicesProvider
extends GenericStateModelAdapter
implements Component
{

  private static final String AXIS_SERVLET_PATH = "/axis/services";

  private static final String ATTR_AXIS_ENGINE = "AxisEngine";

  private ServiceBroker sb;
  private ServiceBroker rootsb;

  private LoggingService log;
  private ServletService servletService;

  private ServiceProvider wssp;

  private AxisServer axisEngine;

  public void setServiceBroker(ServiceBroker sb) {
    this.sb = sb;
  }

  public void load() {
    super.load();

    log = (LoggingService)
      sb.getService(this, LoggingService.class, null);

    // get the node-level service broker if it's available
    NodeControlService ncs = (NodeControlService)
      sb.getService(this, NodeControlService.class, null);
    if (ncs != null) {
      rootsb = ncs.getRootServiceBroker();
      sb.releaseService(this, NodeControlService.class, ncs);
    }

    servletService = (ServletService)
      sb.getService(this, ServletService.class, null);
    if (servletService == null) {
      throw new RuntimeException("Unable to obtain ServletService");
    }

    final Servlet axisServlet = new AxisServlet();

    // create proxy servlet so we can get our hands on the engine
    Servlet proxyServlet = new Servlet() {
      public void init(
          ServletConfig config) throws ServletException {
        // pass in the real config; another option is to pass in a
        // proxy, to keep the engine private to our servlet.
        axisServlet.init(config);
        // note that the axisServlet "init" creates the axis engine,
        // so we save it now
        WebServicesProvider.this.saveAxisEngine(config);
      }
      public ServletConfig getServletConfig() {
        return axisServlet.getServletConfig();
      }
      public void service(
          ServletRequest req, ServletResponse res
          ) throws ServletException, IOException {
        // create a request proxy to replace Cougaar's empty
        // content-path with Axis's expected "/axis" content-path.
        // This proxy also makes room for future enhancements. 
        ServletRequest reqProxy =
          (req instanceof HttpServletRequest ?
           (new RequestProxy((HttpServletRequest) req)) :
           req);
        axisServlet.service(reqProxy, res);
      }
      public String getServletInfo() {
        return axisServlet.getServletInfo();
      }
      public void destroy() {
        axisServlet.destroy();
      }
    };

    try {
      servletService.register(AXIS_SERVLET_PATH, proxyServlet);
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to register \""+AXIS_SERVLET_PATH+"\"", e);
    }

    wssp = new WSSP();
    ServiceBroker the_sb = (rootsb == null ? sb : rootsb);
    the_sb.addService(WebServicesService.class, wssp);

    if (log.isInfoEnabled()) {
      String localHost;
      try {
        InetAddress localAddr = InetAddress.getLocalHost();
        localHost = localAddr.getHostName();
      } catch (Exception e) {
        localHost = "localhost";
      }
      int httpPort = servletService.getHttpPort();
      int httpsPort = servletService.getHttpsPort();
      String url =
        "http"+(httpPort > 0 ? "" : "s")+
        "://"+localHost+":"+
        (httpPort > 0 ? httpPort : httpsPort)+
        AXIS_SERVLET_PATH;
      log.info(
          "Advertised WebServicesService, listening on "+
          url);
    }
  }

  public void unload() {
    super.unload();

    ServiceBroker the_sb = (rootsb == null ? sb :rootsb);
    the_sb.revokeService(WebServicesService.class, wssp);
    wssp = null;

    if (servletService != null) {
      // this unregisters our servlet
      sb.releaseService(this, ServletService.class, servletService);
      servletService = null;
    }

    if (log != null) {
      sb.releaseService(this, LoggingService.class, log);
      log = null;
    }
  }

  /**
   * After AxisServlet init, get the Axis engine from the config.
   */
  private void saveAxisEngine(ServletConfig config) {
    // get our axisEngine from the servlet context.
    //
    // This engine be used in subsequent multithreaded client
    // "processWSDD" calls.  For init we're single-threaded, so no
    // lock is required.
    ServletContext context = config.getServletContext();
    synchronized (context) {
     Object contextObject = context.getAttribute(ATTR_AXIS_ENGINE);
     if (contextObject instanceof AxisServer) {
       axisEngine = (AxisServer) contextObject;
     }
    }

    if (axisEngine == null) {
      if (log.isErrorEnabled()) {
        log.error(
            "Axis servlet \"init("+config+")\" lacks axis engine"+
            " attribute \""+ATTR_AXIS_ENGINE+"\"");
      }
    } else {
      if (log.isInfoEnabled()) {
        log.info("Initialized Axis, found engine: "+axisEngine);
      }
    }
  }

  private void processWSDD(String s) {
    processWSDD(new ByteArrayInputStream(s.getBytes()));
  }
  private void processWSDD(InputStream is) {
    // from org.apache.axis.utils.Admin "processWSDD":
    Document doc;
    try {
      doc = XMLUtils.newDocument(is);
    } catch (Exception e) {
      throw new RuntimeException("Unable to parse XML", e);
    }
    processWSDD(doc);
  }
  private void processWSDD(Document doc) {
    if (axisEngine == null) {
      throw new RuntimeException(
          "Unable to processWSDD, axisEngine is null!");
    }

    // from org.apache.axis.utils.Admin "processWSDD":
    try {
      WSDDDocument wsddDoc = new WSDDDocument(doc);
      EngineConfiguration config = axisEngine.getConfig();
      if (config instanceof WSDDEngineConfiguration) {
        WSDDDeployment deployment =
          ((WSDDEngineConfiguration) config).getDeployment();
        wsddDoc.deploy(deployment);
      }
      axisEngine.refreshGlobalOptions();
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed processWSDD(\n"+docToString(doc)+"\n)", e);
    }

    // don't do:
    //   axisEngine.saveConfiguration();
    // since we don't want to modify the server-config.wsdd with
    // these internal webservices.   Actually, Axis should see
    // that the file is read-only anyways.
    
    if (log.isInfoEnabled()) {
      log.info("Successfully processed WSDD:\n"+docToString(doc));
    }
  }
  
  private static final String docToString(Document doc) {
    try {
      return XMLUtils.DocumentToString(doc);
    } catch (Exception e) {
      return e.getMessage();
    }
  }

  private class WSSP implements ServiceProvider {
    private final WebServicesService SERVICE_INSTANCE =
      new WSSI();

    public Object getService(
        ServiceBroker sb, Object requestor, Class serviceClass) {
      if (WebServicesService.class.isAssignableFrom(serviceClass)) {
        return SERVICE_INSTANCE;
      } else {
        return null;
      }
    }

    public void releaseService(
        ServiceBroker sb, Object requestor,
        Class serviceClass, Object service) {
      // ideally we'd undeploy any deployments created by any given
      // service instance, but it'd be a bit awkward to implement...
    }

    private final class WSSI implements WebServicesService {
      public void processWSDD(String s) {
        WebServicesProvider.this.processWSDD(s);
      }
      public void processWSDD(InputStream is) {
        WebServicesProvider.this.processWSDD(is);
      }
      public void processWSDD(Document doc) {
        WebServicesProvider.this.processWSDD(doc);
      }
    }
  }

  /**
   * Dumb proxy for the HttpServletRequest that forwards all calls
   * except "getContextPath()", which is hard-coded to return "/axis".
   * <p>
   * We only do this so the "/axis/services" page's "?wsdl" links
   * will work, otherwise the links are printed as "/services"
   * without the "/axis" prefix.
   */
  private static final class RequestProxy implements HttpServletRequest {
    private final HttpServletRequest req;
    public RequestProxy(HttpServletRequest req) {
      this.req = req;
    }
    public String getContextPath() {
      // Cougaar's "ROOT" is "/", which confuses Axis, so here we
      // hard-code the context-path to the standard Axis "webapps"
      // directory name of "/axis".
      return "/axis";
    }
    // forward the rest!

    // ServletRequest:
    public Object getAttribute(String name) {
      return req.getAttribute(name);
    }
    public Enumeration getAttributeNames() {
      return req.getAttributeNames();
    }
    public String getCharacterEncoding() {
      return req.getCharacterEncoding();
    }
    public void setCharacterEncoding(
        String env) throws UnsupportedEncodingException {
      req.setCharacterEncoding(env);
    }
    public int getContentLength() {
      return req.getContentLength();
    }
    public String getContentType() {
      return req.getContentType();
    }
    public ServletInputStream getInputStream() throws IOException {
      return req.getInputStream();
    }
    public String getParameter(String name) {
      return req.getParameter(name);
    }
    public Enumeration getParameterNames() {
      return req.getParameterNames();
    }
    public String[] getParameterValues(String name) {
      return req.getParameterValues(name);
    }
    public Map getParameterMap() {
      return req.getParameterMap();
    }
    public String getProtocol() {
      return req.getProtocol();
    }
    public String getScheme() {
      return req.getScheme();
    }
    public String getServerName() {
      return req.getServerName();
    }
    public int getServerPort() {
      return req.getServerPort();
    }
    public BufferedReader getReader() throws IOException {
      return req.getReader();
    }
    public String getRemoteAddr() {
      return req.getRemoteAddr();
    }
    public String getRemoteHost() {
      return req.getRemoteHost();
    }
    public void setAttribute(String name, Object o) {
      req.setAttribute(name, o);
    }
    public void removeAttribute(String name) {
      req.removeAttribute(name);
    }
    public Locale getLocale() {
      return req.getLocale();
    }
    public Enumeration getLocales() {
      return req.getLocales();
    }
    public boolean isSecure() {
      return req.isSecure();
    }
    public RequestDispatcher getRequestDispatcher(String path) {
      return req.getRequestDispatcher(path);
    }
    /** @deprecated */
    public String getRealPath(String path) {
      return req.getRealPath(path);
    }
    // HttpServletRequest:
    public String getAuthType() {
      return req.getAuthType();
    }
    public Cookie[] getCookies() {
      return req.getCookies();
    }
    public long getDateHeader(String name) {
      return req.getDateHeader(name);
    }
    public String getHeader(String name) {
      return req.getHeader(name); 
    }
    public Enumeration getHeaders(String name) {
      return req.getHeaders(name); 
    }
    public Enumeration getHeaderNames() {
      return req.getHeaderNames();
    }
    public int getIntHeader(String name) {
      return req.getIntHeader(name);
    }
    public String getMethod() {
      return req.getMethod();
    }
    public String getPathInfo() {
      return req.getPathInfo();
    }
    public String getPathTranslated() {
      return req.getPathTranslated();
    }
    public String getQueryString() {
      return req.getQueryString();
    }
    public String getRemoteUser() {
      return req.getRemoteUser();
    }
    public boolean isUserInRole(String role) {
      return req.isUserInRole(role);
    }
    public java.security.Principal getUserPrincipal() {
      return req.getUserPrincipal();
    }
    public String getRequestedSessionId() {
      return req.getRequestedSessionId();
    }
    public String getRequestURI() {
      return req.getRequestURI();
    }
    public StringBuffer getRequestURL() {
      return req.getRequestURL();
    }
    public String getServletPath() {
      return req.getServletPath();
    }
    public HttpSession getSession(boolean create) {
      return req.getSession(create);
    }
    public HttpSession getSession() {
      return req.getSession();
    }
    public boolean isRequestedSessionIdValid() {
      return req.isRequestedSessionIdValid();
    }
    public boolean isRequestedSessionIdFromCookie() {
      return req.isRequestedSessionIdFromCookie();
    }
    public boolean isRequestedSessionIdFromURL() {
      return req.isRequestedSessionIdFromURL();
    }
    /** @deprecated */
    public boolean isRequestedSessionIdFromUrl() {
      return req.isRequestedSessionIdFromUrl();
    }
  }
}
