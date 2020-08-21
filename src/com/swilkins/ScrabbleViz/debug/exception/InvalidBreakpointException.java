package com.swilkins.ScrabbleViz.debug.exception;

public class InvalidBreakpointException extends RuntimeException {

  public InvalidBreakpointException(Class<?> clazz, int lineNumber) {
    super(String.format("%s does not have a mapping to line %d.", clazz.getName(), lineNumber));
  }

}
