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
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;

import org.apache.axis.AxisFault;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.encoding.XMLType;
import org.apache.axis.utils.Options;
import org.apache.axis.encoding.ser.BeanDeserializerFactory;
import org.apache.axis.encoding.ser.BeanSerializerFactory;

/**
 * A command-line client that uses SOAP to ask Cougaar for a count of
 * all objects on a node-agent's blackboard.
 * <p>
 * To run on Windows:<pre>
 *   SET CP=-classpath %CIP%\lib\webaxis.jar;%CIP%\sys\axis_1_2_beta.jar;%CIP%\sys\mail.jar;%CIP%\sys\activation.jar
 *   SET CL=org.cougaar.lib.web.axis.blackboardCount.BlackboardCountClient
 *   SET URL=-lhttp://localhost:8800/axis/services
 *   java -classpath %CP% %CL% %URL% %*
 * </pre>
 * or on Linux/Unix Bash:<pre>
 *   CP="$CIP/lib/webaxis.jar:$CIP/sys/axis_1_2_beta.jar:$CIP/sys/activation.jar:$CIP/sys/mail.jar"
 *   CL="org.cougaar.lib.web.axis.blackboardCount.BlackboardCountClient"
 *   URL="-lhttp://localhost:8800/axis/services"
 *   java -classpath $CP $CL $URL $*
 * </pre>
 * <p>
 * The expected output is:<pre>
 *  getBlackboardCount(*)={
 *    org.cougaar.lib.web.axis.blackboardCount.BlackboardCountPlugin$TestOne=1,
 *    org.cougaar.lib.web.axis.blackboardCount.BlackboardCountPlugin$TestTwo=2,
 *  }
 * </pre>
 * <p>
 * Most of this is Axis/SOAP boilerplate, based on the Axis sample code:<pre>
 *   $AXIS_HOME/samples/stock/GetQuote.java
 * </pre>
 */
public class BlackboardCountClient {

  public static void main(String args[]) throws Exception {
    // parse options
    Options opts = new Options(args);
    String[] extraArgs = opts.getRemainingArgs();
    String surl = opts.getURL();
    String classFilter = 
      (extraArgs == null || extraArgs.length <= 0 ?
       "*" : extraArgs[0]);

    // invoke
    Map m = 
      BlackboardCountClient.getBlackboardCount(
        surl,
        classFilter);

    // print result
    System.out.print("getBlackboardCount("+classFilter+")={");
    for (Iterator iter = m.entrySet().iterator(); iter.hasNext(); ) {
      Map.Entry me = (Map.Entry) iter.next();
      System.out.println("  "+me.getKey()+"="+me.getValue()+",");
    }
    System.out.println("}");
  }

  // helper function; does all the real work
  //
  // see the Axis samples for more examples; this one is based on
  //   $AXIS_HOME/samples/stock/GetQuote.java 
  private static Map getBlackboardCount(
      String surl,
      String classFilter) throws Exception {

    URL url = new URL(surl);

    Service service = new Service();

    Call call = (Call) service.createCall();

    // register types:
    Class cl = ResultEntry.class;
    QName qn = new QName("urn:BeanService", "ResultEntry");
    call.registerTypeMapping(cl, qn,
        new BeanSerializerFactory(cl, qn),
        new BeanDeserializerFactory(cl, qn));

    cl = ResultMap.class;
    qn = new QName("urn:BeanService", "ResultMap");
    call.registerTypeMapping(cl, qn,
        new BeanSerializerFactory(cl, qn),
        new BeanDeserializerFactory(cl, qn));

    call.setTargetEndpointAddress(url);
    call.setOperationName(
        new QName("urn:Cougaar-blackboard-count", "getBlackboardCount"));
    call.addParameter(
        "classFilter", XMLType.XSD_STRING, ParameterMode.IN);
    call.setReturnType(qn);

    Object ret = call.invoke(new Object[] {classFilter});
    if (ret instanceof String) {
      System.out.println(
          "Received problem response from server: "+ret);
      throw new AxisFault("", (String)ret, null, null);
    }
    ResultMap rm = (ResultMap) ret;
    return rm.toMap();
  }
}
