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

import org.cougaar.util.Arguments;
import org.cougaar.util.UnaryPredicate;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.plugin.ComponentPlugin;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.util.UID;
import java.util.Enumeration;

/**
 * This component publishes a test {@link DistanceQuery} object that
 * the {@link DistanceQueryPlugin} will process.
 * <p>
 * Requires the {@link DistanceQueryPlugin} to be loaded. 
 * <p>
 * Load with:<pre>
 *  &lt;component
 *    class="org.cougaar.lib.web.axis.distance.DistanceQueryTest"&gt;
 *    &lt;argument&gt;fromZip=02138&lt;/argument&gt; 
 *    &lt;argument&gt;toZip=90210&lt;/argument&gt; 
 *  &lt;/component&gt;
 * </pre> 
 * <p>
 * The expected output is:<br><code>
 * .. Published query:
 *   (distance-query uid=NodeA/<i>int</i>
 *   fromZip=02138 toZip=90210 distance=0.0)<br>
 * .. Observed query answer:
 *   (distance-query uid=NodeA/<i>int</i>
 *   fromZip=02138 toZip=90210 distance=2594.585085768498)<br>
 * </code>
 */ 
public class DistanceQueryTest extends ComponentPlugin {

  private static final String DEFAULT_FROM_ZIP = "02138";
  private static final String DEFAULT_TO_ZIP = "90210";

  private static final UnaryPredicate DISTANCE_QUERY_PREDICATE =
    new DistanceQueryPredicate();

  private LoggingService log;
  private UIDService uidService;

  private IncrementalSubscription distSub;

  public void setLoggingService(LoggingService log) {
    this.log = log;
  }
  public void setUIDService(UIDService uidService) {
    this.uidService = uidService;
  }

  protected void setupSubscriptions() {

    // subscribe to our query response, which we'll create shortly
    distSub = (IncrementalSubscription) 
      blackboard.subscribe(DISTANCE_QUERY_PREDICATE);

    // check plugin parameters for "fromZip=" and "toZip="
    Arguments args = new Arguments(getParameters());
    String fromZip = args.getString("fromZip", DEFAULT_FROM_ZIP);
    String toZip = args.getString("toZip", DEFAULT_TO_ZIP);

    // publish our query
    UID uid = uidService.nextUID();
    DistanceQuery dq = new DistanceQuery(uid, fromZip, toZip);
    blackboard.publishAdd(dq);

    if (log.isShoutEnabled()) {
      log.shout("Published query: "+dq);
    }

    // in "execute()" we'll listen for a response
  }

  protected void execute() {
    // ignore distSub.getAddedList(), we create them

    // check for answers from the DistanceQueryPlugin
    for (Enumeration en = distSub.getChangedList();
         en.hasMoreElements();
         ) {
      DistanceQuery dq = (DistanceQuery) en.nextElement();
      if (log.isShoutEnabled()) {
        log.shout("Observed query answer: "+dq);
      }
    }
  }

  private static final class DistanceQueryPredicate
    implements UnaryPredicate {
    public boolean execute(Object o) {
      return (o instanceof DistanceQuery);
    }
  }
}
