package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.request.BreakpointRequest;

import java.util.*;

public class DebugClass {

  private final Class<?> clazz;
  private final DebugClassSource debugClassSource;
  private final DebugClassOperations operations;
  private final Map<Integer, BreakpointRequest> breakpointRequestMap = new HashMap<>();
  private String cachedContentsString;

  public DebugClass(Class<?> clazz, DebugClassSource debugClassSource, DebugClassOperations operations) {
    this.clazz = clazz;
    this.debugClassSource = debugClassSource;
    this.operations = operations;
  }

  public void setCached(boolean cached) {
    if (cached) {
      if (cachedContentsString == null) {
        cachedContentsString = getContentsAsStringHelper();
      }
    } else {
      this.cachedContentsString = null;
    }
  }

  public String getContentsAsString() {
    if (this.cachedContentsString == null) {
      return getContentsAsStringHelper();
    }
    return cachedContentsString;
  }

  private String getContentsAsStringHelper() {
    try {
      return debugClassSource.getContentsAsString();
    } catch (Exception e) {
      String message = String.format(
              "%s representing %s failed to get contents as String. (%s)",
              getClass().getName(),
              clazz.getName(),
              e
      );
      throw new IllegalArgumentException(message);
    }
  }

  public Set<Integer> getBreakpoints() {
    Set<Integer> enabledBreakpoints = new HashSet<>(breakpointRequestMap.size());
    for (Map.Entry<Integer, BreakpointRequest> breakpointEntry : breakpointRequestMap.entrySet()) {
      if (breakpointEntry.getValue().isEnabled()) {
        enabledBreakpoints.add(breakpointEntry.getKey());
      }
    }
    return enabledBreakpoints;
  }

  public void requestBreakpointAt(int lineNumber) throws AbsentInformationException {
    List<Location> locations = operations.getLocationGetter().getLocations(lineNumber);
    if (!locations.isEmpty()) {
      BreakpointRequest request = operations.getBreakpointRequester().request(locations.get(0));
      breakpointRequestMap.put(lineNumber, request);
      request.enable();
    }
  }

  public boolean hasBreakpointAt(int lineNumber) {
    return breakpointRequestMap.containsKey(lineNumber);
  }

  public void removeBreakpointAt(int lineNumber) {
    BreakpointRequest request = breakpointRequestMap.remove(lineNumber);
    if (request != null) {
      operations.getBreakpointRemover().remove(request);
    }
  }

}
