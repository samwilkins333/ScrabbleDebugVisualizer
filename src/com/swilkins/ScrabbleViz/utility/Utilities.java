package com.swilkins.ScrabbleViz.utility;

import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public final class Utilities {

  public static String inputStreamToString(InputStream inputStream) {
    String generatorSourceString;
    try {

      final int bufferSize = 1024;
      final char[] buffer = new char[bufferSize];
      final StringBuilder out = new StringBuilder();
      Reader in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
      int charsRead;
      while ((charsRead = in.read(buffer, 0, buffer.length)) > 0) {
        out.append(buffer, 0, charsRead);
      }
      generatorSourceString = out.toString();
    } catch (IOException e) {
      generatorSourceString = null;
      e.printStackTrace();
    }
    return generatorSourceString;
  }

  public static Class<?> toClass(Location location) throws ClassNotFoundException {
    return Class.forName(location.toString().split(":")[0]);
  }

  public static IntegerValue toValue(int value) {
    return new IntegerValue() {
      @Override
      public int value() {
        return value;
      }

      @Override
      public boolean booleanValue() {
        return false;
      }

      @Override
      public byte byteValue() {
        return (byte) value;
      }

      @Override
      public char charValue() {
        return (char) value;
      }

      @Override
      public short shortValue() {
        return (short) value;
      }

      @Override
      public int intValue() {
        return value;
      }

      @Override
      public long longValue() {
        return value;
      }

      @Override
      public float floatValue() {
        return value;
      }

      @Override
      public double doubleValue() {
        return value;
      }

      @Override
      public Type type() {
        return null;
      }

      @Override
      public VirtualMachine virtualMachine() {
        return null;
      }

      @Override
      public int compareTo(@NotNull IntegerValue o) {
        return 0;
      }

    };
  }

}
