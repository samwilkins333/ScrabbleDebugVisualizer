package com.swilkins.ScrabbleViz.debugClass;

public abstract class DebugClassSource {
  private final int[] compileTimeBreakpoints;

  public DebugClassSource(int... compileTimeBreakpoints) {
    this.compileTimeBreakpoints = compileTimeBreakpoints;
  }

  public abstract String getContentsAsString();

  public int[] getCompileTimeBreakpoints() {
    return compileTimeBreakpoints;
  }

}
