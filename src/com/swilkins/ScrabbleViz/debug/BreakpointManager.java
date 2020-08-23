package com.swilkins.ScrabbleViz.debug;

import com.sun.jdi.Location;

import java.util.*;

import static com.swilkins.ScrabbleViz.debug.BreakpointManager.Breakpoint;

public class BreakpointManager extends HashMap<Class<?>, Map<Integer, Breakpoint>> {
  private final Set<String> classNames = new HashSet<>();

  public static class Breakpoint {
    private final int lineNumber;
    private final String annotation;

    public Breakpoint(int lineNumber, String annotation) {
      this.lineNumber = lineNumber;
      this.annotation = annotation;
    }

    public int getLineNumber() {
      return lineNumber;
    }

    public String getAnnotation() {
      return annotation;
    }

  }

  public void setBreakpointAt(Class<?> clazz, int lineNumber, String annotation) {
    Map<Integer, Breakpoint> existing = get(clazz);
    if (existing == null) {
      existing = new HashMap<>();
      put(clazz, existing);
    }
    existing.put(lineNumber, new Breakpoint(lineNumber, annotation));
    classNames.add(clazz.getName());
  }

  public Breakpoint getBreakpointAt(Class<?> clazz, int lineNumber) {
    Map<Integer, Breakpoint> existing = get(clazz);
    if (existing != null) {
      return existing.get(lineNumber);
    }
    return null;
  }

  public boolean validate(Location location) {
    return classNames.contains(location.toString().split(":")[0]);
  }

  public boolean validate(Class<?> clazz, int lineNumber) {
    return getBreakpointAt(clazz, lineNumber) != null;
  }

  public Set<String> getClassNames() {
    return Collections.unmodifiableSet(classNames);
  }

}
