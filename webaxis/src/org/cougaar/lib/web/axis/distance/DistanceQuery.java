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

import org.cougaar.core.util.UID;
import org.cougaar.core.util.UniqueObject;

/**
 * A blackboard query object that the {@link DistanceQueryPlugin}
 * subscribes to and fills in the "distance" result.
 */
public final class DistanceQuery implements UniqueObject {

  private final UID uid;
  private final String fromZip;
  private final String toZip;
  private double distance;

  public DistanceQuery(
      UID uid,
      String fromZip,
      String toZip) {
    this.uid = uid;
    this.fromZip = fromZip;
    this.toZip = toZip;

    // make sure they're not null
    String s =
      (uid == null ? "uid" :
       fromZip == null ? "fromZip" :
       toZip == null ? "toZip" :
       null);
    if (s != null) {
      throw new IllegalArgumentException("null "+s);
    }
  }

  public UID getUID() {
    return uid;
  }
  public void setUID(UID uid) {
    throw new UnsupportedOperationException("already set uid!)");
  }

  public String getFromZip() {
    return fromZip;
  }

  public String getToZip() {
    return toZip;
  }

  public double getDistance() {
    return distance;
  }
  public void setDistance(double distance) {
    this.distance = distance;
  }

  public int hashCode() {
    return uid.hashCode();
  }
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o instanceof DistanceQuery) {
      return uid.equals(((DistanceQuery) o).uid);
    } else {
      return false;
    }
  }

  public String toString() {
    return
      "(distance-query uid="+uid+
      " fromZip="+fromZip+" toZip="+toZip+
      " distance="+distance+")";
  }
}
