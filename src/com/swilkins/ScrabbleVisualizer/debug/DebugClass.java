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

  public Class<?> getClazz() {
    return clazz;
  }

  public void setCached(boolean cached) {
    if (cached) {
      if (cachedContentsString == null) {
        cachedContentsString = debugClassSource.getContentsAsString();
      }
    } else {
      this.cachedContentsString = null;
    }
  }

  public String getContentsAsString() {
    if (this.cachedContentsString == null) {
      return debugClassSource.getContentsAsString();
    }
    return cachedContentsString;
  }

  public Set<Integer> getEnabledBreakpoints() {
    Set<Integer> enabledBreakpoints = new HashSet<>(breakpointRequestMap.size());
    for (Map.Entry<Integer, BreakpointRequest> breakpointEntry : breakpointRequestMap.entrySet()) {
      if (breakpointEntry.getValue().isEnabled()) {
        enabledBreakpoints.add(breakpointEntry.getKey());
      }
    }
    return enabledBreakpoints;
  }

  public boolean requestBreakpointAt(int lineNumber) throws AbsentInformationException {
    List<Location> locations = operations.getLocationGetter().getLocations(lineNumber);
    if (locations.isEmpty()) {
      return false;
    }
    BreakpointRequest request = operations.getBreakpointRequester().request(locations.get(0));
    breakpointRequestMap.put(lineNumber, request);
    request.enable();
    return true;
  }

  public boolean hasBreakpointAt(int lineNumber) {
    return breakpointRequestMap.containsKey(lineNumber);
  }

  public boolean disableBreakpointAt(int lineNumber) {
    BreakpointRequest request = breakpointRequestMap.get(lineNumber);
    if (request != null && request.isEnabled()) {
      request.disable();
      return true;
    }
    return false;
  }

  public boolean removeBreakpointAt(int lineNumber) {
    BreakpointRequest request = breakpointRequestMap.remove(lineNumber);
    if (request == null) {
      return false;
    }
    operations.getBreakpointRemover().remove(request);
    return true;
  }

}
