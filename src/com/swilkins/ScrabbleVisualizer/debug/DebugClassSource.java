package com.swilkins.ScrabbleVisualizer.debug;

import java.util.ArrayList;
import java.util.List;

public abstract class DebugClassSource {

  private final List<Integer> compileTimeBreakpoints = new ArrayList<>();

  public DebugClassSource(int... compileTimeBreakpoints) {
    addCompileTimeBreakpointsHelper(compileTimeBreakpoints);
  }

  public abstract String getContentsAsString() throws Exception;

  public List<Integer> getCompileTimeBreakpoints() {
    return compileTimeBreakpoints;
  }

  public void addCompileTimeBreakpoints(int... compileTimeBreakpoints) {
    addCompileTimeBreakpointsHelper(compileTimeBreakpoints);
  }

  private void addCompileTimeBreakpointsHelper(int... compileTimeBreakpoints) {
    for (int compileTimeBreakpoint : compileTimeBreakpoints) {
      this.compileTimeBreakpoints.add(compileTimeBreakpoint);
    }
  }

}
