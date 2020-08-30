package com.swilkins.ScrabbleVisualizer.utility;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Utilities {

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
      e.printStackTrace();
      return null;
    }
  }

  public static ImageIcon createImageIconFrom(URL url, Dimension size) {
    ImageIcon icon = new ImageIcon(url);
    Image image = icon.getImage();
    Image scaled = image.getScaledInstance(size.width, size.height, Image.SCALE_SMOOTH);
    icon.setImage(scaled);
    return icon;
  }

  public static String longestCommonPrefix(List<String> a) {
    /* if size is 0, return empty string */
    if (a.isEmpty()) {
      return "";
    }

    int size = a.size();
    String first = a.get(0);
    if (size == 1)
      return first;

    /* sort the array of strings */
    a.sort(String::compareTo);

    /* find the minimum length from first and last string */
    int end = Math.min(first.length(), a.get(size - 1).length());

        /* find the common prefix between the first and
           last string */
    int i = 0;
    while (i < end && first.charAt(i) == a.get(size - 1).charAt(i))
      i++;

    return first.substring(0, i);
  }

}
