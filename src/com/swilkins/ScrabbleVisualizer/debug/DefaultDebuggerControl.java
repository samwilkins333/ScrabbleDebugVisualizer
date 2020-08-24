package com.swilkins.ScrabbleVisualizer.debug;

public enum DefaultDebuggerControl {

  RESUME("Resume"),
  STEP_OVER("Step Over"),
  STEP_INTO("Step Into"),
  STEP_OUT("Step Out"),
  TOGGLE_BREAKPOINT("Toggle Breakpoint");

  private final String label;

  DefaultDebuggerControl(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

}
