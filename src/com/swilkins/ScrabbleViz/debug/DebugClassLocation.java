package com.swilkins.ScrabbleViz.debug;

public class DebugClassLocation {
  private DebugClass debugClass;
  private int lineNumber;

  public DebugClassLocation(DebugClass debugClass, int lineNumber) {
    this.debugClass = debugClass;
    this.lineNumber = lineNumber;
  }

  public DebugClass getDebugClass() {
    return debugClass;
  }

  public int getLineNumber() {
    return lineNumber;
  }

}
