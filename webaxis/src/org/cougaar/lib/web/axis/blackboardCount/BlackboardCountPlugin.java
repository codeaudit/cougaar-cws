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

package org.cougaar.lib.web.axis.blackboardCount;

import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.cougaar.util.GenericStateModelAdapter;
import org.cougaar.util.UnaryPredicate;
import org.cougaar.core.blackboard.BlackboardClient;
import org.cougaar.core.component.Component;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.BlackboardQueryService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.service.WebServicesService;
import org.cougaar.core.util.UID;
import org.cougaar.core.util.UniqueObject;

/**
 * This component advertises the SOAP "BlackboardCount" and
 * replies to "getBlackboardCount" requests by querying the
 * blackboard.
 * <p>
 * Load with:<pre>
 *  &lt;component
 *    class="org.cougaar.lib.web.axis.WebServicesProvider"/gt;
 *  &lt;component
 *    class="org.cougaar.lib.web.axis.blackboardCount.BlackboardCountPlugin"/&gt;
 * </pre> 
 * <p>
 * Use the {@link BlackboardCountClient} to invoke this plugin's
 * SOAP service from a command-line client. 
 * <p>
 * Note that only one instance of this plugin can be loaded per
 * JVM, since the {@link WebServicesService} is static and our
 * {@link BlackboardCountHook} is static.  To work around this,
 * a developer could maintain a Map in the "hook" and require
 * the SOAP caller to specify the agent name, or advertise separate
 * SOAP paths with separate "hook" implementations.
 */
public class BlackboardCountPlugin
  extends GenericStateModelAdapter
  implements Component, BlackboardCount
{

  /**
   * Axis WSDD, basically copied from StockQuote deploy.wsdd.
   * <p>
   * This is a giant string, but we use ".class.getName()" to make
   * sure the referenced classnames are correct.
   */ 
  private static final String BLACKBOARD_COUNT_WSDD =
      "<deployment name=\"test\" xmlns=\"http://xml.apache.org/axis/wsdd/\" \n"+
      "    xmlns:java=\"http://xml.apache.org/axis/wsdd/providers/java\">\n"+
      "  <service name=\"urn:Cougaar-blackboard-count\" provider=\"java:RPC\">\n"+
      "    <parameter name=\"className\" value=\""+
      BlackboardCountHook.class.getName()+
      "\"/>\n"+
      "    <parameter name=\"allowedMethods\" value=\"getBlackboardCount\"/>\n"+
      "    <parameter name=\"wsdlServicePort\" value=\"BlackboardCount\"/>\n"+
      "    <beanMapping qname=\"myNS:ResultMap\"\n"+
      "      xmlns:myNS=\"urn:BeanService\"\n"+
      "      languageSpecificType=\"java:"+
      ResultMap.class.getName()+
      "\"/>\n"+
      "    <beanMapping qname=\"myNS:ResultEntry\"\n"+
      "      xmlns:myNS=\"urn:BeanService\"\n"+
      "      languageSpecificType=\"java:"+
      ResultEntry.class.getName()+
      "\"/>\n"+
      "  </service>\n"+
      "</deployment>";

  /**
   * List of common class packages, allowing the client to say:<pre>
   *   getBlackboardCount("Relay");
   * </pre>instead of:<pre>
   *   getBlackboardCount("org.cougaar.core.relay.Relay");
   * </pre> 
   */
  private static final String[] STANDARD_PACKAGES = {
    "",
    "org.cougaar.lib.web.axis.blackboardCount.BlackboardCountPlugin$",
    "org.cougaar.core.util.",
    "org.cougaar.core.relay.",
    "org.cougaar.planning.ldm.plan.",
    "org.cougaar.planning.ldm.asset.",
  };

  private ServiceBroker sb;

  private LoggingService log;
  private BlackboardQueryService blackboard;

  public void setServiceBroker(ServiceBroker sb) {
    this.sb = sb;
  }

  public void load() {
    super.load();

    log = (LoggingService)
      sb.getService(this, LoggingService.class, null);

    // get the blackboard query service so we can do our count
    blackboard = (BlackboardQueryService)
      sb.getService(this, BlackboardQueryService.class, null);
    if (blackboard == null) {
      throw new RuntimeException("Unable to obtain BlackboardQueryService");
    }

    WebServicesService wss = (WebServicesService)
      sb.getService(this, WebServicesService.class, null);
    if (wss == null) {
      throw new RuntimeException("Unable to obtain WebServicesService");
    }

    // create dummy blackboard objects, since this plugin is
    // typically loaded in the node-agent, which usually has
    // an empty blackboard
    createBlackboardTestObjects();

    // set a pointer to our plugin.
    //
    // this allows Axis to call "new BlackboardCountHook()" yet still
    // have the method calls invoke our plugin instance's methods.
    BlackboardCountHook.setStatic(this);

    // tell Axis to process the WSDD.
    //
    // note: Axis tends to be silent about some WSDD errors until
    // the first "/axis/services" servlet attempt
    if (log.isInfoEnabled()) { 
      log.info("processWSDD(\n"+BLACKBOARD_COUNT_WSDD+"\n)");
    }
    try {
      wss.processWSDD(BLACKBOARD_COUNT_WSDD);
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to processWSDD(\n"+BLACKBOARD_COUNT_WSDD+"\n)", e);
    }

    log.shout(
        "Created \"BlackboardCount\" web service.\n"+
        "Check the \"/axis/services\" servlet to see if the WSDL is listed.\n"+
        "To invoke, run $CIP/configs/axis/getBlackboardCount.sh\n"+
        "The expected output is:\n"+
        "  getBlackboardCount(*)={\n"+
        "    "+getClass().getName()+"$TestOne=1,\n"+
        "    "+getClass().getName()+"$TestTwo=2,\n"+
        "  }");
  }

  public ResultMap getBlackboardCount(String s) {
    String classFilter = (s == null ? "*" : s.trim());

    if (log.isInfoEnabled()) {
      log.shout("getBlackboardCount("+classFilter+")");
    }

    // query the blackboard
    UnaryPredicate pred = createPredicate(classFilter);
    Collection c = blackboard.query(pred);

    // convert objects to "class -> int" table
    Map m = createClassCountMap(c);

    // wrap in SOAP-friendly object
    return new ResultMap(m);
  }

  private UnaryPredicate createPredicate(String classFilter) {
    if ("*".equals(classFilter)) {
      return new UnaryPredicate() {
        public boolean execute(Object o) {
          return true;
        }
      };
    }
    Class cl = null;
    for (int i = 0; i < STANDARD_PACKAGES.length; i++) {
      try { 
        String s = STANDARD_PACKAGES[i]+classFilter;
        cl = Class.forName(s);
        break;
      } catch (Exception e) {
        // ignore, try next package
        cl = null;
      }
    }
    if (cl == null) {
      throw new RuntimeException(
          "Unknown class type: "+classFilter);
    }
    final Class cl2 = cl;
    return new UnaryPredicate() {
      public boolean execute(Object o) {
        return o != null && cl2.isAssignableFrom(o.getClass());
      }
    };
  }

  /** convert objects to "class -&gt; int" table */
  private Map createClassCountMap(Collection c) {
    int n = (c == null ? 0 : c.size());
    Map ret = new HashMap(n);
    if (n > 0) {
      Iterator iter = c.iterator();
      for (int i = 0; i < n; i++) {
        Object o = iter.next();
        Class cl = (o == null ? null : o.getClass());
        String clname = (cl == null ? "null" : cl.getName());
        Counter counter = (Counter) ret.get(clname);
        if (counter == null) {
          counter = new Counter();
          ret.put(clname, counter);
        }
        counter.count++;
      }
      for (Iterator i2 = ret.entrySet().iterator();
          i2.hasNext();
          ) {
        Map.Entry me = (Map.Entry) i2.next();
        Counter counter = (Counter) me.getValue();
        me.setValue(new Integer(counter.count));
      }
    }
    return ret;
  }

  /**
   * publish something on the node's blackboard,
   * since it is often empty.
   */
  private void createBlackboardTestObjects() {
    UIDService uidService = (UIDService)
      sb.getService(this, UIDService.class, null);
    BlackboardClient bc = new BlackboardClient() {
      public String getBlackboardClientName() {
        return getClass().getName();
      }
      public long currentTimeMillis() { return -1; }
    }; 
    BlackboardService bs = (BlackboardService)
      sb.getService(bc, BlackboardService.class, null);
    if (bs != null) {
      try {
        bs.openTransaction();
        bs.publishAdd(new TestOne(uidService.nextUID()));
        bs.publishAdd(new TestTwo(uidService.nextUID()));
        bs.publishAdd(new TestTwo(uidService.nextUID()));
      } finally {
        bs.closeTransaction();
      }
      sb.releaseService(bc, BlackboardService.class, bs);
    }
    sb.releaseService(this, UIDService.class, uidService);
  }

  private static final class Counter {
    public int count;
  }

  // trivial test objects for "createBlackboardTestObjects()"
  private static abstract class TestBase implements UniqueObject {
    private final UID uid;
    public TestBase(UID uid) {
      this.uid = uid;
      if (uid == null) {
        throw new IllegalArgumentException("null uid");
      }
    }
    public UID getUID() { return uid; }
    public void setUID(UID uid) {
      throw new UnsupportedOperationException("UID already set");
    }
    public String toString() {
      String s = getClass().getName();
      int i = s.lastIndexOf('.');
      if (i >= 0 && i < s.length()) {
        s = s.substring(i+1);
      }
      return "("+s+" uid="+uid+")";
    }
    public int hashCode() {
      return uid.hashCode();
    }
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      } else if (o instanceof TestBase) {
        return uid.equals(((TestBase) o).uid);
      } else {
        return false;
      }
    }
  }
  private static class TestOne extends TestBase {
    public TestOne(UID uid) {
      super(uid);
    }
  }
  private static class TestTwo extends TestBase {
    public TestTwo(UID uid) {
      super(uid);
    }
  }
}
