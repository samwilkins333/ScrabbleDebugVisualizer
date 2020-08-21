package com.swilkins.ScrabbleViz.debug;

import com.sun.jdi.Location;

import java.util.*;

import static com.swilkins.ScrabbleViz.utility.Utilities.toClass;

public class BreakpointManager extends HashMap<Integer, Map<Class<?>, Integer>> {
  private final Set<Class<?>> classes = new HashSet<>();

  public void register(int lineNumber, Class<?> clazz, Integer stepRequestType) {
    Map<Class<?>, Integer> existing = get(lineNumber);
    if (existing == null) {
      put(lineNumber, existing = new HashMap<>());
    }
    classes.add(clazz);
    existing.put(clazz, stepRequestType);
  }

  public Integer actionFor(int lineNumber, Class<?> clazz) {
    return get(lineNumber).get(clazz);
  }

  public boolean validate(Location location) throws ClassNotFoundException {
    return classes.contains(toClass(location));
  }

  public Set<Class<?>> getClasses() {
    return Collections.unmodifiableSet(classes);
  }

}
