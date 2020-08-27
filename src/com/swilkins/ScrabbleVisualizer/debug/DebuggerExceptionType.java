package com.swilkins.ScrabbleVisualizer.debug;

public enum DebuggerExceptionType {

  VIRTUAL_MACHINE("virtual machine"),
  DEBUGGER("debugger");

  private final String locationName;

  DebuggerExceptionType(String locationName) {
    this.locationName = locationName;
  }

  public String getLocationName() {
    return locationName;
  }

}
