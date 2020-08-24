package com.swilkins.ScrabbleVisualizer.debug;

public abstract class DebugClassSource {

  private final int[] compileTimeBreakpoints;

  public DebugClassSource(int... compileTimeBreakpoints) {
    this.compileTimeBreakpoints = compileTimeBreakpoints;
  }

  public abstract String getContentsAsString() throws Exception;

  public int[] getCompileTimeBreakpoints() {
    return compileTimeBreakpoints;
  }

}
