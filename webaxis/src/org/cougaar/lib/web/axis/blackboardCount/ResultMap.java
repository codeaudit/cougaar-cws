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

import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;

/**
 * A table of "classname -&gt; int" data returned by
 * {@link BlackboardCount#getBlackboardCount}.
 * <p>
 * The Axis BeanSerializer does not directly support {@link Map}s,
 * so this is a wrapper.
 */
public final class ResultMap {

  private Map m;

  /** constructor for bean deserializer */
  public ResultMap() {}

  /** constructor for our plugin */
  public ResultMap(Map m) {
    this.m = m;
  }

  /** get the object, avoid bean serializer "get*" method. */
  public Map toMap() {
    return m;
  }

  public ResultEntry[] getEntries() {
    // convert to entries, since the bean serializer doesn't
    // understand Maps
    int n = (m == null ? 0 : m.size()) ;
    ResultEntry[] entries = 
      new ResultEntry[n];
    if (n > 0) {
      int j = 0;
      Iterator iter = m.entrySet().iterator();
      for (int i = 0; i < n; i++) {
        Map.Entry me = (Map.Entry) iter.next();
        Object key = me.getKey();
        Object value = me.getValue();
        if (key instanceof Class) {
          key = ((Class) key).getName();
        }
        if (key instanceof String && value instanceof Number) {
          entries[j++] = new ResultEntry(
              (String) key,
              ((Number) value).intValue());
        }
      }
    }
    return entries;
  }

  public void setEntries(ResultEntry[] entries) {
    // convert to map
    int n = (entries == null ? 0 : entries.length);
    m = new HashMap(n);
    for (int i = 0; i < n; i++) {
      ResultEntry re = (ResultEntry)
        entries[i];
      if (re == null) {
        continue;
      }
      m.put(re.getClassname(), new Integer(re.getCount()));
    }
  }

  public String toString() {
    return "(ResultMap entries="+m+")";
  }
}
