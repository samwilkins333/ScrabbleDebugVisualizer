package com.swilkins.ScrabbleViz.utility;

import com.sun.jdi.Location;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public final class Utilities {

  public static Class<?> toClass(Location location) {
    Class<?> result;
    String className = location.toString().split(":")[0];
    try {
      result = Class.forName(className);
    } catch (ClassNotFoundException e) {
      result = null;
    }
    return result;
  }

  public static String inputStreamToString(InputStream debugSourceStream) {
    try {

      final int bufferSize = 1024;
      final char[] buffer = new char[bufferSize];
      final StringBuilder out = new StringBuilder();
      Reader in = new InputStreamReader(debugSourceStream, StandardCharsets.UTF_8);
      int charsRead;
      while ((charsRead = in.read(buffer, 0, buffer.length)) > 0) {
        out.append(buffer, 0, charsRead);
      }
      return out.toString();
    } catch (IOException e) {
      return null;
    }
  }

}
