package com.swilkins.ScrabbleVisualizer.executable;

import com.swilkins.ScrabbleVisualizer.debug.ScrabbleBaseDebugger;
import com.swilkins.ScrabbleVisualizer.debug.ScrabbleBaseVisualizer;

import java.awt.*;

public class DebuggerHarness {

  public static void main(String[] args) {
    EventQueue.invokeLater(() -> {
      try {
        new ScrabbleBaseDebugger(new ScrabbleBaseVisualizer()).setVisible(true);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

}
