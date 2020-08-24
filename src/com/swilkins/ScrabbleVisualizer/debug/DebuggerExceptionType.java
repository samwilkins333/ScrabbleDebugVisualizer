package com.swilkins.ScrabbleVisualizer.debug;

public enum DebuggerExceptionType {

  VIRTUAL_MACHINE("virtual machine"),
  DEBUGGER("debugger");

  private String locationName;

  DebuggerExceptionType(String locationName) {

  }

  public String getLocationName() {
    return locationName;
  }

}
