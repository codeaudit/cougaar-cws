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

package org.cougaar.lib.web.axis.mts;

import javax.activation.DataHandler;

/**
 * A {@link SOAPMT} implementation that forwards all requests
 * to a static {@link SOAPMT} instance.
 * <p>
 * The static is used because the WSDD registered in the
 * {@link org.cougaar.core.service.WebServicesService} specifies
 * the SOAPMT <i>classname</i> instead of our {@link
 * SOAPLinkProtocol} instance.  We use the static to hook
 * the two together, otherwise the SOAP engine wouldn't have
 * a pointer back into our Cougaar LinkProtocol instance.
 * The static is fine, since link protocols typically have
 * a per-node receiver instance (i.e. a "NodeServant").
 * <p>
 * This is analagous to the webtomcat module's "HookServlet".
 * <p>
 * Note that we can't use a ThreadLocal, since the classname
 * is only instantiated when a call is received, not when we
 * register the WSDD.
 */
public class SOAPMTHook implements SOAPMT {

  private static SOAPMT mt;

  // package-protected so only the SOAPLinkProtocol can call it:
  static void setStatic(SOAPMT x) {
    mt = x;
  }

  // forward everything:

  public SOAPData rerouteMessage(SOAPData small_message)
    throws Exception{
      return mt.rerouteMessage(small_message);
  }

  public SOAPData rerouteMessageAsAttachment(DataHandler big_message)
    throws Exception {
      return mt.rerouteMessageAsAttachment(big_message);
  }

  public SOAPData getMessageAddress()
    throws Exception {
    return mt.getMessageAddress();
  }
}
