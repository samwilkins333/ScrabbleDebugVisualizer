package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.request.BreakpointRequest;
import com.swilkins.ScrabbleVisualizer.debug.interfaces.LocationGetter;

import java.util.*;

public class DebugClass {

  private final Class<?> clazz;
  private final DebugClassSource debugClassSource;
  private final LocationGetter locationGetter;
  private final Map<Integer, BreakpointRequest> breakpointRequestMap = new HashMap<>();
  private String cachedContentsString;

  public DebugClass(Class<?> clazz, DebugClassSource debugClassSource, LocationGetter locationGetter) {
    this.clazz = clazz;
    this.debugClassSource = debugClassSource;
    this.locationGetter = locationGetter;
  }

  public Class<?> getClazz() {
    return clazz;
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

  public void addBreakpointRequest(int lineNumber, BreakpointRequest breakpointRequest) {
    breakpointRequestMap.put(lineNumber, breakpointRequest);
  }

  public BreakpointRequest getBreakpointRequest(int lineNumber) {
    return breakpointRequestMap.get(lineNumber);
  }

  public Set<Integer> getEnabledBreakpoints() {
    Set<Integer> enabledBreakpoints = new HashSet<>(breakpointRequestMap.size());
    for (Map.Entry<Integer, BreakpointRequest> breakpointRequestEntry : breakpointRequestMap.entrySet()) {
      if (breakpointRequestEntry.getValue().isEnabled()) {
        enabledBreakpoints.add(breakpointRequestEntry.getKey());
      }
    }
    return Collections.unmodifiableSet(enabledBreakpoints);
  }

  public Location getLocationOf(int lineNumber) throws AbsentInformationException {
    List<Location> locations = locationGetter.getLocations(lineNumber);
    return !locations.isEmpty() ? locations.get(0) : null;
  }

  @Override
  public String toString() {
    return "DebugClass " + clazz.getName();
  }

}
