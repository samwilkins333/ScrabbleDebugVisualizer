package com.swilkins.ScrabbleViz.debug;

import com.sun.jdi.Location;

import java.util.HashMap;
import java.util.Map;

public class BreakpointManager extends HashMap<Integer, Map<Class<?>, Integer>> {

  public void register(int lineNumber, Class<?> clazz, Integer stepRequestType) {
    Map<Class<?>, Integer> existing = get(lineNumber);
    if (existing == null) {
      put(lineNumber, existing = new HashMap<>());
    }
    existing.put(clazz, stepRequestType);
  }

  public Integer actionFor(int lineNumber, Class<?> clazz) {
    return get(lineNumber).get(clazz);
  }

}
