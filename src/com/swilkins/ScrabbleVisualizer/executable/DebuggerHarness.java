package com.swilkins.ScrabbleVisualizer.executable;

import com.swilkins.ScrabbleVisualizer.debug.ScrabbleBaseDebugger;

import java.awt.*;

public class DebuggerHarness {

  public static void main(String[] args) {
    EventQueue.invokeLater(() -> {
      try {
        new ScrabbleBaseDebugger().setVisible(true);
//        new FibonacciDebugger(1).setVisible(true);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

}
