package com.swilkins.ScrabbleVisualizer.executable;

import com.swilkins.ScrabbleVisualizer.debug.ScrabbleVisualizer;

import java.awt.*;

public class DebuggerHarness {

  public static void main(String[] args) {
    EventQueue.invokeLater(() -> {
      try {
        new ScrabbleVisualizer().start();
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

}
