package com.swilkins.ScrabbleViz.debug.interfaces;

import com.sun.jdi.request.EventRequest;

public interface BreakpointRemover {

  void remove(EventRequest breakpointRequest);

}
