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

package org.cougaar.lib.web.axis.distance;

import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.plugin.ComponentPlugin;
import org.cougaar.core.thread.SchedulableStatus;
import org.cougaar.util.Arguments;
import org.cougaar.util.UnaryPredicate;

import org.apache.axis.AxisFault;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.client.async.AsyncCall;
import org.apache.axis.client.async.IAsyncCallback;
import org.apache.axis.client.async.IAsyncResult;
import org.apache.axis.client.async.Status;
import org.apache.axis.encoding.XMLType;
import org.apache.axis.utils.Options;
import org.apache.axis.encoding.ser.BeanDeserializerFactory;
import org.apache.axis.encoding.ser.BeanSerializerFactory;

/**
 * This component subscribes to {@link DistanceQuery} objects
 * and sets the "distance" between the two ZIP codes by asking
 * a remote SOAP server.
 * <p> 
 * Load with:<pre>
 *  &lt;component
 *    class="org.cougaar.lib.web.axis.distance.DistanceQueryPlugin"&gt;
 *    &lt;argument&gt;async=false&lt;/argument&gt;
 *  &lt;/component&gt;
 * </pre> 
 * <p>
 * Change the above to "async=true" to enable asynchronous SOAP
 * calls, which avoids blocking Cougaar's pooled threads during
 * the SOAP I/O.
 * <p>
 * This example uses the <a href=
 *   "http://webservices.imacination.com/distance/"
 *   >imacination.com</a> "ZIP Distance Calculator" web service.
 */
public class DistanceQueryPlugin extends ComponentPlugin {

  private static final String DEFAULT_URL =
    "http://webservices.imacination.com/distance/Distance.jws";

  // if the plugin lacks an "async=boolean" parameter, use
  // this default:
  private static final boolean DEFAULT_ASYNC = true;

  private static final UnaryPredicate DISTANCE_QUERY_PREDICATE =
    new DistanceQueryPredicate();

  private boolean async;

  // if async, this is the pending result queue:
  private final List queue = new ArrayList();

  private LoggingService log;

  private IncrementalSubscription distSub;

  public void setLoggingService(LoggingService log) {
    this.log = log;
  }

  protected void setupSubscriptions() {
    Arguments args = new Arguments(getParameters());
    async = args.getBoolean("async", DEFAULT_ASYNC);

    distSub = (IncrementalSubscription) 
      blackboard.subscribe(DISTANCE_QUERY_PREDICATE);
  }

  protected void execute() {
    // handle new distance requests
    for (Enumeration en = distSub.getAddedList();
        en.hasMoreElements();
        ) {
      DistanceQuery dq = (DistanceQuery) en.nextElement();
      if (log.isInfoEnabled()) {
        log.info("Handle added "+dq);
      }
      if (async) {
        // send asynchronous distance lookup, look for an
        // answer the next time we "execute()"
        getDistanceAsync(dq);
        continue;
      }
      // do blocking SOAP call, set distance now
      double distance = getDistance(dq);
      setDistance(dq, distance);
    }

    if (async) {
      // check for asynchronous callback results
      DQResult dqr;
      while ((dqr = nextResult()) != null) {
        DistanceQuery dq = dqr.getDistanceQuery();
        double distance = dqr.getDistance();
        setDistance(dq, distance);
      }
    }
  }

  private void setDistance(DistanceQuery dq, double distance) {
    if (log.isInfoEnabled()) {
      log.info("Setting distance to "+distance+" for "+dq);
    }
    dq.setDistance(distance);
    blackboard.publishChange(dq);
  }

  // blocking distance lookup
  private double getDistance(DistanceQuery dq) {
    try {
      Call call = prepareCall();
      Object[] params = createParams(dq);

      if (log.isInfoEnabled()) {
        log.info("Invoking blocking SOAP call");
      }

      // invoke, tell the thread service that we'll block
      Object ret;
      try {
        SchedulableStatus.beginNetIO("SOAP call");
        ret = call.invoke(params);
      } finally {
        SchedulableStatus.endBlocking();
      }

      if (ret instanceof Double) {
        return ((Double) ret).doubleValue();
      }
      throw new RuntimeException(
        "Expecting a \"double\" response, not "+ret);
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("getDistance("+dq+") failed", e);
      }
      return -1;
    }
  }

  // non-blocking
  private void getDistanceAsync(DistanceQuery dq) {
    try {
      Call call = prepareCall();
      Object[] params = createParams(dq);

      if (log.isInfoEnabled()) {
        log.info("Submitting asynchronous SOAP call for "+dq);
      }

      IAsyncCallback iac = new MyAsyncCallback(dq);
      AsyncCall ac = new AsyncCall(call, iac);
      IAsyncResult result = ac.invoke(params);

      // no result yet, "execute()" later
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("getDistance("+dq+") failed", e);
      }
      setDistance(dq, -1);
    }
  }

  // axis boilerplate.
  //
  // There are other ways to do this, e.g.
  //   - ask the service for its WSLD
  //   - create proxy classes
  //   - etc
  // See the Axis examples for pointers, e.g.
  //   $AXIS_HOME/samples/stock/GetQuote*.java
  private Call prepareCall() throws Exception {
    String surl = DEFAULT_URL;
    URL url = new URL(surl);

    Service service = new Service();

    Call call = (Call) service.createCall();

    call.setTargetEndpointAddress(url);
    //call.setTimeout(new Integer(however_many_millis));
    call.setOperationName(new QName("urn:DistanceService", "getDistance"));
    call.addParameter("fromZip", XMLType.XSD_STRING, ParameterMode.IN);
    call.addParameter("toZip", XMLType.XSD_STRING, ParameterMode.IN);
    call.setReturnType(XMLType.XSD_DOUBLE);

    return call;
  }
  private Object[] createParams(DistanceQuery dq) {
    return new Object[] {dq.getFromZip(), dq.getToZip()};
  }

  private DQResult nextResult() {
    synchronized (queue) {
      if (queue.isEmpty()) {
        return null;
      }
      // take the head of the queue.
      // don't bother to optimize with a linked list.
      return (DQResult) queue.remove(0);
    }
  }

  private static final class DistanceQueryPredicate
    implements UnaryPredicate {
      public boolean execute(Object o) {
        return (o instanceof DistanceQuery);
      }
    }

  private static final class DQResult {
    private final DistanceQuery dq;
    private final double distance;
    public DQResult(DistanceQuery dq, double distance) {
      this.dq = dq;
      this.distance = distance;
    }
    public DistanceQuery getDistanceQuery() { return dq; }
    public double getDistance() { return distance; }
  }

  private final class MyAsyncCallback
    implements IAsyncCallback {
      private final DistanceQuery dq;
      public MyAsyncCallback(DistanceQuery dq) {
        this.dq = dq;
      }
      public void onCompletion(IAsyncResult result) {
        if (log.isInfoEnabled()) {
          log.info("Receiving asynchronous SOAP result for "+dq);
        }
        double distance = extractDistance(result);
        synchronized (queue) {
          queue.add(new DQResult(dq, distance));
        }
        // request "execute()" in the plugin's thread.
        // We need to do this to do the "publishChange", plus
        // (in general) to avoid blocking the SOAP callback thread.
        blackboard.signalClientActivity();
      }
      private double extractDistance(IAsyncResult result) {
        Status status = result.getStatus();
        if (status == Status.COMPLETED) {
          Object o = result.getResponse();
          if (o instanceof Double) {
            // good!
            return ((Double) o).doubleValue();
          }
        }
        if (log.isErrorEnabled()) {
          String s =
            (status == Status.COMPLETED ?
             ("unexpected return type: "+result.getResponse()) :
             (status == Status.EXCEPTION ?
              "exception" :
              "invalid status: "+status));
          Throwable t =
            (status == Status.EXCEPTION ?
             result.getException() : null);
          log.error("getDistance("+dq+") failed: "+s, t);
        }
        return -1;
      }
    }
}
