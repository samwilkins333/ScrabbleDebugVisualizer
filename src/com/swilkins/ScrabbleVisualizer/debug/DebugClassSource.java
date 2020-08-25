package com.swilkins.ScrabbleVisualizer.debug;

import java.util.ArrayList;
import java.util.List;

public abstract class DebugClassSource {

  private final List<Integer> compileTimeBreakpoints = new ArrayList<>();
  private boolean cached;

  public DebugClassSource(boolean cached, int... compileTimeBreakpoints) {
    this.cached = cached;
    addCompileTimeBreakpointsHelper(compileTimeBreakpoints);
  }

  public abstract String getContentsAsString() throws Exception;

  public List<Integer> getCompileTimeBreakpoints() {
    return compileTimeBreakpoints;
  }

  public DebugClassSource addCompileTimeBreakpoints(int... compileTimeBreakpoints) {
    addCompileTimeBreakpointsHelper(compileTimeBreakpoints);
    return this;
  }

  private void addCompileTimeBreakpointsHelper(int... compileTimeBreakpoints) {
    for (int compileTimeBreakpoint : compileTimeBreakpoints) {
      this.compileTimeBreakpoints.add(compileTimeBreakpoint);
    }
  }

  public DebugClassSource setCached(boolean cached) {
    this.cached = cached;
    return this;
  }

  public boolean isCached() {
    return cached;
  }

}
