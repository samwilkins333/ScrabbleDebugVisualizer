package com.swilkins.ScrabbleVisualizer.debug;

import java.util.Objects;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DebugClassLocation that = (DebugClassLocation) o;
    return lineNumber == that.lineNumber &&
            debugClass.equals(that.debugClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(debugClass, lineNumber);
  }

}
