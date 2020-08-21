package com.swilkins.ScrabbleViz.debug.exception;

public class MissingSourceException extends RuntimeException {

  public MissingSourceException(Class<?> clazz) {
    super(String.format("%s does not have a raw representation.", clazz.getName()));
  }

}
