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

import org.cougaar.core.service.ServletService;
import org.cougaar.mts.std.AttributedMessage;

/**
 * SSL variation of {@link SOAPLinkProtocol}, which registers
 * in the WP as HTTPS.
 * <p>
 * <b>Known bugs:</b> This is broken if both the HTTP-based {@link
 * SOAPLinkProtocol} and this HTTPS-based SSLSOAPLinkProtocol are
 * loaded, since both attempt to register the same WSDD and attach
 * to the same {@link SOAPMTHook}.
 */
public class SSLSOAPLinkProtocol extends SOAPLinkProtocol {
  public SSLSOAPLinkProtocol() {
    super();
  }
  public String getProtocolType() {
    return "-SSLSOAP"; 
  }
  public String getProtocol() {
    return "https";
  }
  protected Boolean usesEncryptedSocket() {
    return Boolean.TRUE;
  }
  protected int computeCost(AttributedMessage message) {
    return super.computeCost(message) * 3;
  }
  public Class getProtocolClass() {
    return SSLSOAPLinkProtocol.class;
  }
}
