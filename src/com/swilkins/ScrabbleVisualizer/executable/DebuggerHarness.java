package com.swilkins.ScrabbleVisualizer.executable;

import java.awt.*;

public class DebuggerHarness {

  public static void main(String[] args) {
    EventQueue.invokeLater(() -> {
      try {
        new FibonacciDebugger().setVisible(true);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

}
