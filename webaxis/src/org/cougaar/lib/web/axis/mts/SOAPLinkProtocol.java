/* 
 * <copyright> 
 *  Copyright 1999-2004 Cougaar Software, Inc.
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
 
package org.cougaar.lib.web.axis.mts;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;

import javax.activation.DataSource;
import javax.activation.DataHandler;
import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;
import javax.xml.rpc.ServiceException;

import org.apache.axis.AxisFault;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.encoding.XMLType;
import org.apache.axis.utils.Options;
import org.apache.axis.attachments.OctetStream;
import org.apache.axis.attachments.OctetStreamDataSource;
import org.apache.axis.encoding.ser.BeanDeserializerFactory;
import org.apache.axis.encoding.ser.BeanSerializerFactory;
import org.apache.axis.encoding.ser.JAFDataHandlerDeserializerFactory;
import org.apache.axis.encoding.ser.JAFDataHandlerSerializerFactory;

import org.cougaar.core.component.ServiceAvailableEvent;
import org.cougaar.core.component.ServiceAvailableListener;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.mts.MessageAttributes;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.ServletService;
import org.cougaar.core.service.WebServicesService;
import org.cougaar.core.thread.SchedulableStatus;
import org.cougaar.mts.base.CommFailureException;
import org.cougaar.mts.base.DestinationLink;
import org.cougaar.mts.base.LinkProtocol; // javadoc only
import org.cougaar.mts.base.MisdeliveredMessageException;
import org.cougaar.mts.base.NameLookupException;
import org.cougaar.mts.base.RPCLinkProtocol;
import org.cougaar.mts.base.UnregisteredNameException;
import org.cougaar.mts.std.AttributedMessage;

/**
 * This component is a SOAP-based {@link LinkProtocol} that uses
 * the {@link WebServicesService} to receive messages and Axis
 * {@link Call}s to send messages.
 * <p>
 * Load with:<pre>
 *   &lt;component
 *     class='org.cougaar.lib.web.axis.mts.SOAPLinkProtocol'
 *     insertionpoint='Node.AgentManager.Agent.MessageTransport.Component'/&gt;
 * </pre>
 * <p>
 * The current code is only partially an "open-messaging" format,
 * since the SOAP XML contains serialized Java Objects that are
 * equivalent to the objects passed by the RMI-based LinkProtocol.
 * <p>
 * Performance is about 5x slower than RMI.  In a two-node localhost
 * "ping" test sending 2k messages back and forth as fast as possible,
 * the time to send a message in milliseconds were:
 *   mean=15, stddev=14, min=9, max=122
 * as compared to RMI: 
 *   mean= 3, stddev= 2, min=2, max= 54
 * This matches Axis performance metrics found in several research
 * papers, e.g. <a
 * href="http://www.mathematik.uni-ulm.de/sai/ws03/webserv/PerfWS.pdf"
 * >Performance of Web Services</a>.
 */
public class SOAPLinkProtocol extends RPCLinkProtocol {

  /** 
   * Our servlet path registered by the WebServicesService.
   */
  private static final String SERVLET_URI = "/axis/services";

  /**
   * Maximum byte size for messages that can be sent as SOAPData,
   * where larger messages must be sent as attachments.
   * <p>
   * The SOAP XML parser throws an OutOfMemoryError for large
   * messages (&gt; ~5mb) sent in the XML as SOAPData.  Also,
   * attachments are more efficient for larger messages, since
   * the XML parser adds base64 encoding (+33% bloat) and
   * inefficient string buffering.  We could always use attachments,
   * for smaller messages this seems wasteful, since attachments
   * are copied to the disk and require file I/O to read them, plus
   * periodic Axis directly cleanup costs.
   * <p>
   * Here we use 64k, which minimal testing has found to be a
   * pretty good tradeoff. 
   */
  private static final int BIG_MESSAGE_LENGTH = 1<<16;

  /**
   * Our WSDD to register in the {@link WebServicesService}.
   * <p>
   * This is a giant string, but we use ".class.getName()" to make
   * sure the referenced classnames are correct.
   */
  private static final String MT_WSDD =
    "<deployment name=\"test\" xmlns=\"http://xml.apache.org/axis/wsdd/\" \n"+
    "    xmlns:java=\"http://xml.apache.org/axis/wsdd/providers/java\">\n"+
    "  <service name=\"urn:Cougaar-MTS\" provider=\"java:RPC\">\n"+
    "    <parameter name=\"className\"\n"+
    "      value=\""+
    SOAPMTHook.class.getName()+
    "\"/>\n"+
    "    <parameter name=\"allowedMethods\"\n"+
    "      value=\"rerouteMessage rerouteMessageAsAttachment "+
    "getMessageAddress\"/>\n"+
    "    <parameter name=\"wsdlServicePort\" value=\"SOAPMT\"/>\n"+
    "    <operation name=\"rerouteMessage\"\n"+
    "      returnQName=\"returnqname\" returnType=\"SOAPData\">\n"+
    "      <parameter name=\"small_message\" type=\"SOAPData\"/>\n"+
    "    </operation>\n"+
    "    <operation name=\"rerouteMessageAsAttachment\"\n"+
    "      returnQName=\"returnqname\" returnType=\"SOAPData\">\n"+
    "      <parameter name=\"big_message\" type=\"DataHandler\"/>\n"+
    "    </operation>\n"+
    "    <operation name=\"getMessageAddress\"\n"+
    "      returnQName=\"returnqname\" returnType=\"SOAPData\"/>\n"+
    "    <beanMapping qname=\"myNS:SOAPData\"\n"+
    "      xmlns:myNS=\"urn:BeanService\"\n"+
    "      languageSpecificType=\"java:"+
    SOAPData.class.getName()+
    "\"/>\n"+
    "    <typeMapping\n"+
    "      deserializer=\""+
    JAFDataHandlerDeserializerFactory.class.getName()+
    "\"\n"+
    "      languageSpecificType=\"java:"+
    DataHandler.class.getName()+
    "\"\n"+
    "      qname=\"DataHandler\"\n"+
    "      serializer=\""+
    JAFDataHandlerSerializerFactory.class.getName()+
    "\"\n"+
    "      encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"/>\n"+
    "  </service>\n"+
    "</deployment>";

  /**
   * The preferred ServletService API to get the http/https port,
   * which we obtain through reflection to avoid a "webserver"
   * module dependency.
   */
  private static final String ROOT_SERVLET_SERVICE_CLASS =
    "org.cougaar.lib.web.service.RootServletService";

  private LoggingService logger;
  private WebServicesService webServicesService;

  private boolean servant_made = false;

  public void load() {
    super.load();
    logger = getLoggingService();
    if (logger.isDebugEnabled()) {
      logger.debug("Loading");
    }

    // when an agent registers on our node, our RPCLinkProtocol
    // base class will call "findOrMakeNodeServant()", which
    // will call "registerWebService()" to deploy our WSDD and
    // register us to receive messages.  Also, we will register
    // in the WP so other nodes can find us.
  }

  /**
   * If we registered a WSDD, unregister here.
   */
  public void unload() {
    ServiceBroker sb = getServiceBroker();
    if (webServicesService != null) {
      sb.releaseService(
          this, WebServicesService.class, webServicesService);
      webServicesService = null;
    }
    super.unload();
  }

  /** @return the naming service "AddressEntry" type */
  public String getProtocolType() {
    return "-SOAP"; 
  }

  //
  // protected methods for SSLSOAPLinkProtocol use:
  //

  /** @return HTTP */
  protected String getProtocol() {
    return "http";
  }
  /** @return servlet path */
  protected String getPath() {
    return SERVLET_URI;
  }
  /** @return true of the socket is encrypted */
  protected Boolean usesEncryptedSocket() {
    return Boolean.FALSE;
  }
  /**
   * @return estimated cost for HTTP-based SOAP, which is simply
   * hard-coded to be more than the RMI &amp; HTTP LinkProtocols.
   */
  protected int computeCost(AttributedMessage message) {
    return 1500;
  }
  /** @return outgoing link to the target address */
  protected DestinationLink createDestinationLink(
      MessageAddress addr) {
    return new SOAPDestinationLink(addr); 
  }

  /**
   * A local agent has registered in the MTS, so deploy our WSDD,
   * advertise ourselves in the WP, and get ready to receive messages.
   * <p>
   * If we've already registered then this is a no-op.
   * <p>
   * The one snag is that the node may register early on, before
   * the WebServicesService is available, so we use a
   * ServiceAvailabiltyListener in case the service isn't available
   * yet.
   */
  protected void findOrMakeNodeServant() {
    if (logger.isDebugEnabled()) {
      logger.debug("findOrMakeNodeServant, servant_made="+servant_made);
    }

    if (servant_made) {
      // already created node webservice
      return;
    }

    final ServiceBroker sb = getServiceBroker();

    // use the servlet service to get our local servlet port
    int port = -1; 
    Class ssClass;
    try {
      ssClass = Class.forName(ROOT_SERVLET_SERVICE_CLASS);
    } catch (Exception e) {
      ssClass = ServletService.class;
    }
    Object ss = sb.getService(this, ssClass, null);
    if (ss != null) {
      // port = ss.get<Protocol>Port();
      try {
        String s = getProtocol();
        s = Character.toUpperCase(s.charAt(0))+s.substring(1);
        s = "get"+s+"Port";
        Method m = ssClass.getMethod(s, null);
        Object ret = m.invoke(ss, null);
        port = ((Integer) ret).intValue();
      } catch (Exception e) {
        if (logger.isWarnEnabled()) {
          logger.warn("Unable to get "+getProtocol()+" port", e);
        }
      }
      sb.releaseService(this, ssClass, ss); 
    }
    if (port < 0) {
      if (logger.isWarnEnabled()) {
        logger.warn(
            getProtocol()+" port is disabled,"+
            " not registering to receive SOAP messages");
      }
      servant_made = true;
      return;
    }

    // get the WebServicesService.  If it's not available yet, set a
    // callback to tell us when it's available.
    if (sb.hasService(WebServicesService.class)) {
      registerWebService();
    } else {
      sb.addServiceListener(new ServiceAvailableListener() {
        public void serviceAvailable(ServiceAvailableEvent ae) {
          Class cl = ae.getService();
          if (WebServicesService.class.isAssignableFrom(cl)) {
            sb.removeServiceListener(this);
            registerWebService();
          }
        }
      });
    }

    //
    // ideally we could return here if we haven't registered
    // our WSDD yet, but our RPCLinkProtocol needs us to
    // set our "nodeURI" now to support the WP, so we must
    // do this now.
    //

    // set our node's servlet URI for later binding in the white pages,
    // so all other nodes can find us
    MessageAddress node_addr = getNameSupport().getNodeMessageAddress();
    String node_name = node_addr.toAddress();
    URI nodeURI;
    try {
      InetAddress me = InetAddress.getLocalHost();
      nodeURI = new URI(
          getProtocol()+"://"+
          me.getHostName()+':'+port+
          getPath());
      setNodeURI(nodeURI);
    } catch (Exception e) {
      if (logger.isErrorEnabled()) {
        logger.error("createURI failed", e);
      }
      nodeURI = null;
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Registered in WP with URI: "+nodeURI);
    }

    servant_made = true;
  }

  /**
   * Handle IP address change.
   * <p>
   * Servlets handle the new-address case automatically, so this is
   * a no-op.
   */
  protected void remakeNodeServant() {
  }

  /**
   * Register our WebService that will handle the messages on the
   * receiving end.
   */
  private void registerWebService() {
    if (logger.isDebugEnabled()) {
      logger.debug("registerWebServices");
    }

    // get webservices api
    ServiceBroker sb = getServiceBroker();
    webServicesService = (WebServicesService) 
      sb.getService(this, WebServicesService.class, null);
    if (webServicesService == null) {
      throw new RuntimeException("Unable to obtain WebServicesService");
    }

    // set a static in the SOAPMTHook, to forward method calls into
    // this link protocol
    SOAPMT mt = new SOAPMT() {
      public SOAPData rerouteMessage(SOAPData small_message) throws Exception {
        AttributedMessage message = (AttributedMessage)
          small_message.toObject();
        Object result = SOAPLinkProtocol.this.receiveMessage(message);
        return new SOAPData(result);
      }
      public SOAPData rerouteMessageAsAttachment(
          DataHandler big_message) throws Exception {
        AttributedMessage message = (AttributedMessage)
          readFromDataHandler(big_message);
        Object result = SOAPLinkProtocol.this.receiveMessage(message);
        return new SOAPData(result);
      }
      public SOAPData getMessageAddress() {
        MessageAddress addr = SOAPLinkProtocol.this.getMessageAddress();
        return new SOAPData(addr);
      }
    };
    SOAPMTHook.setStatic(mt);

    // register our wsdd
    if (logger.isDebugEnabled()) {
      logger.debug("processWSDD(\n"+MT_WSDD+"\n):");
    }
    try {
      webServicesService.processWSDD(MT_WSDD);
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to processWSDD(\n"+MT_WSDD+"\n)", e);
    }

    // ready to receive messages
  }

  private Object receiveMessage(AttributedMessage message) {
    Object result;
    try {
      // deliver the message by obtaining the
      // MessageDeliverer from the LinkProtocol
      result = getDeliverer().deliverMessage(message, message.getTarget());
      // the result should be MessageAttributes!
    } catch (MisdeliveredMessageException e) {
      result = e;
    } catch (Exception e) {
      result = new CommFailureException(e);
    }
    return result;
  }

  private MessageAddress getMessageAddress() {
    return getNameSupport().getNodeMessageAddress();
  }

  private Object readFromDataHandler(
      DataHandler dh) throws Exception {
    String filename = dh.getName();

    // read object from file
    Object obj = null;
    FileInputStream fis = new FileInputStream(filename);
    ObjectInputStream ois = new ObjectInputStream(fis);
    obj = ois.readObject();
    ois.close();

    return obj;
  }
  /**
   * Our per-destination outgoing link, where we make our call.
   */
  protected class SOAPDestinationLink extends Link {

    // our generic SOAP client-sie Service engine instance.
    //
    // By saving this instead of creating an instance per "invoke",
    // we avoid about 5 millis per call, which would be 33% of the
    // 15 millis messaging delay for the average 2k localhost
    // message.
    //
    // We must specify the full package name to avoid confusion with
    // our RPCLinkProtocol's inner "Service" interface
    private org.apache.axis.client.Service service;

    public SOAPDestinationLink(MessageAddress target) {
      super(target);
    }

    public Class getProtocolClass() {
      return SOAPLinkProtocol.this.getClass();
    }

    /**
     * We found this URI in the WP, decode it if necessary.
     * <p>
     * For us this is the Axis URI, which is fine as-is.
     */
    protected Object decodeRemoteRef(URI ref) throws Exception {
      return (ref == null ? null : ref.toURL());
    }

    /**
     * Send the message to the target Agent's SOAPLinkProtocol
     * via SOAP.
     */
    protected MessageAttributes forwardByProtocol(
        Object remote_ref,
        AttributedMessage message) 
      throws NameLookupException, UnregisteredNameException, 
    CommFailureException, MisdeliveredMessageException {
      try {
        // loopback:
        MessageAddress target = message.getTarget();
        if (getRegistry().isLocalClient(target)) {
          return getDeliverer().deliverMessage(message, target);
        }
        // send remote:
        Object response = sendMessage((URL) remote_ref, message);
        if (response instanceof MessageAttributes) {
          return (MessageAttributes) response;
        } else if (response instanceof MisdeliveredMessageException) {
          decache();
          throw (MisdeliveredMessageException) response;
        } else {
          throw new CommFailureException((Exception) response);
        }
      } catch (Exception e) {
        //e.printStackTrace();
        throw new CommFailureException(e);
      }
    }


    /**
     * This method streams serialized java objects over SOAP.
     */
    private Object sendMessage(URL url, AttributedMessage message) 
      throws IOException, ClassNotFoundException, UnknownHostException {
        // write object to byte array
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(message);
        oos.close();
        byte[] messageBytes = bos.toByteArray();
        int messageLength = 
          (messageBytes == null ? 0 : messageBytes.length);

        // choose to send either inlined or as an attachment
        boolean isBigMessage =
          (messageLength >= BIG_MESSAGE_LENGTH);

        if (logger.isDetailEnabled()) {
          logger.detail(
              "sendMessage("+url+", "+message+
              ") length="+messageLength);
        }

        Object sendObj;
        if (isBigMessage) {
          // wrap inside DataHandler to send as attachement
          sendObj = new DataHandler(
              new OctetStreamDataSource(
                "source", new OctetStream(messageBytes)));
        } else {
          // send inline as xml encoded binary
          sendObj = new SOAPData(messageBytes);
        }

        if (service == null) {
          service = new org.apache.axis.client.Service();
        }

        Call call;
        try {
          call = (Call) service.createCall();
        } catch (ServiceException se) {
          throw new RuntimeException("Unable to create SOAP call", se);
        }

        // register type mappings
        QName dataHandlerQN = 
          (isBigMessage ?
           (new QName("urn:Cougaar-MTS", "DataHandler")) :
           null);
        QName dataQN =
          new QName("urn:BeanService", "SOAPData");
        Class cl = SOAPData.class;
        QName qn = dataQN;
        call.registerTypeMapping(cl, qn,
            new BeanSerializerFactory(cl, qn),
            new BeanDeserializerFactory(cl, qn));
        if (isBigMessage) {
          cl = DataHandler.class;
          qn = dataHandlerQN;
          call.registerTypeMapping(cl, qn,
              JAFDataHandlerSerializerFactory.class,
              JAFDataHandlerDeserializerFactory.class);
        }
        call.setTargetEndpointAddress(url);

        if (isBigMessage) {
          call.setOperationName(
              new QName(
                "urn:Cougaar-MTS",
                "rerouteMessageAsAttachment"));
          call.addParameter(
              "big_message", dataHandlerQN, ParameterMode.IN);
        } else {
          call.setOperationName(
              new QName("urn:Cougaar-MTS", "rerouteMessage"));
          call.addParameter("small_message", dataQN, ParameterMode.IN);
        }
        call.setReturnType(dataQN);

        // invoke
        Object ret;
        try {
          SchedulableStatus.beginNetIO("SOAP call");
          ret = call.invoke(new Object[] {sendObj});
        } finally {
          SchedulableStatus.endBlocking();
        }

        if (ret instanceof SOAPData) {
          // usual case, MTS works
          Object o = ((SOAPData) ret).toObject();
          if (o instanceof MessageAttributes) {
            // good case, should be typical case
            return (MessageAttributes) o;
          } else if (o instanceof MisdeliveredMessageException) {
            // remote MTS exception, rethrow
            throw (MisdeliveredMessageException) o;
          } else {
            throw new IllegalArgumentException(
                "Invalid data type: "+
                (o == null ? "null" : o.getClass().getName()));
          }
        }

        // check for Axis/SOAP error
        if (ret instanceof String) {
          // SOAP failure, e.g. I/O??  Treat as CommFailure
          throw new IOException("SOAP failure: "+ret);
        }

        // other case?  error!
        throw new RuntimeException(
            "Invalid SOAP return type: "+
            (ret == null ? "null" : ret.getClass().getName()));
      }
  }
}
