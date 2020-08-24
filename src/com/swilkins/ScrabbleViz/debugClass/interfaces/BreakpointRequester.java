package com.swilkins.ScrabbleViz.debugClass.interfaces;

import com.sun.jdi.Location;
import com.sun.jdi.request.BreakpointRequest;

public interface BreakpointRequester {

  BreakpointRequest request(Location location);

}
