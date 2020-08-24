package com.swilkins.ScrabbleVisualizer.debug.interfaces;

import com.sun.jdi.request.EventRequest;

public interface BreakpointRemover {

  void remove(EventRequest breakpointRequest);

}
