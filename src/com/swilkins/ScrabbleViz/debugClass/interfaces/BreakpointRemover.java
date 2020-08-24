package com.swilkins.ScrabbleViz.debugClass.interfaces;

import com.sun.jdi.request.EventRequest;

public interface BreakpointRemover {

  void remove(EventRequest breakpointRequest);

}
