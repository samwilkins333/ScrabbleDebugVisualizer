package com.swilkins.ScrabbleVisualizer.utility;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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

}
