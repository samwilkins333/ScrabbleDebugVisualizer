package com.swilkins.ScrabbleViz.debug;

import com.sun.jdi.Location;
import com.sun.jdi.request.BreakpointRequest;

import java.util.*;

import static com.swilkins.ScrabbleViz.debug.BreakpointManager.Breakpoint;

public class BreakpointManager extends HashMap<Class<?>, Map<Integer, Breakpoint>> {
  private final Set<String> classNames = new HashSet<>();

  public static class Breakpoint {
    private final int lineNumber;
    private final String annotation;
    private BreakpointRequest request;

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

    public BreakpointRequest getRequest() {
      return request;
    }

    public void setRequest(BreakpointRequest request) {
      this.request = request;
    }

  }

  public void createBreakpointAt(Class<?> clazz, int lineNumber, String annotation) {
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

  public Breakpoint removeBreakpointAt(Class<?> clazz, int lineNumber) {
    Map<Integer, Breakpoint> existing = get(clazz);
    if (existing != null) {
      return existing.remove(lineNumber);
    }
    return null;
  }

  public boolean contains(Location location) {
    return classNames.contains(location.toString().split(":")[0]);
  }

  public boolean contains(Class<?> clazz, int lineNumber) {
    return getBreakpointAt(clazz, lineNumber) != null;
  }

  public Set<String> getClassNames() {
    return Collections.unmodifiableSet(classNames);
  }

  @Override
  public String toString() {
    StringBuilder breakpoints = new StringBuilder().append(size()).append("\n");
    for (Map.Entry<Class<?>, Map<Integer, Breakpoint>> forClass : entrySet()) {
      String className = forClass.getKey().getName();
      for (Map.Entry<Integer, Breakpoint> entry : forClass.getValue().entrySet()) {
        breakpoints.append(className).append(":").append(entry.getKey()).append("\n");
      }
    }
    breakpoints.deleteCharAt(breakpoints.lastIndexOf("\n"));
    return breakpoints.toString();
  }
}
