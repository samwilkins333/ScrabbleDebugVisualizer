package com.swilkins.ScrabbleViz.debug;

import com.swilkins.ScrabbleViz.debug.interfaces.BreakpointRemover;
import com.swilkins.ScrabbleViz.debug.interfaces.BreakpointRequester;
import com.swilkins.ScrabbleViz.debug.interfaces.LocationGetter;

public class DebugClassOperations {

  private final LocationGetter locationGetter;
  private final BreakpointRequester breakpointRequester;
  private final BreakpointRemover breakpointRemover;

  public DebugClassOperations(
          LocationGetter locationGetter,
          BreakpointRequester breakpointRequester,
          BreakpointRemover breakpointRemover
  ) {
    this.locationGetter = locationGetter;
    this.breakpointRequester = breakpointRequester;
    this.breakpointRemover = breakpointRemover;
  }

  public LocationGetter getLocationGetter() {
    return locationGetter;
  }

  public BreakpointRequester getBreakpointRequester() {
    return breakpointRequester;
  }

  public BreakpointRemover getBreakpointRemover() {
    return breakpointRemover;
  }

}
