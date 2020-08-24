package com.swilkins.ScrabbleVisualizer.debug.interfaces;

import com.sun.jdi.Location;
import com.sun.jdi.request.BreakpointRequest;

public interface BreakpointRequester {

  BreakpointRequest request(Location location);

}
