package com.swilkins.ScrabbleVisualizer.executable;

import com.sun.jdi.connect.Connector;
import com.swilkins.ScrabbleVisualizer.debug.DebugClassSource;
import com.swilkins.ScrabbleVisualizer.debug.Debugger;
import com.swilkins.ScrabbleVisualizer.view.DebuggerWatchView;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Map;
import java.util.function.BiConsumer;

import static com.swilkins.ScrabbleVisualizer.utility.Utilities.inputStreamToString;

public class FibonacciDebugger extends Debugger {

  public FibonacciDebugger(int index) throws Exception {
    super(Fibonacci.class, new DebuggerWatchView() {

      @Override
      public void initialize(Dimension initialDimension) {
        JPanel rawWatched = new JPanel();
        rawWatched.setLayout(new BoxLayout(rawWatched, BoxLayout.X_AXIS));
        rawWatched.setPreferredSize(initialDimension);
        rawWatched.setBackground(Color.WHITE);
        Dimension quarter = new Dimension(initialDimension.width / 2, initialDimension.height);
        rawWatchedName.setEditable(false);
        rawWatchedName.setHighlighter(null);
        rawWatchedName.setBackground(Color.WHITE);
        rawWatchedName.setPreferredSize(quarter);
        rawWatchedValue.setEditable(false);
        rawWatchedValue.setHighlighter(null);
        rawWatchedValue.setBackground(Color.WHITE);
        rawWatchedValue.setPreferredSize(quarter);
        rawWatched.add(rawWatchedName);
        rawWatched.add(rawWatchedValue);
        rawWatched.setBorder(new EmptyBorder(5, 5, 5, 5));
        add(rawWatched);
      }

      @Override
      public BiConsumer<Dimension, Integer> onSplitResize() {
        return null;
      }

      @Override
      protected void registerUpdaters() {

      }

      @Override
      public void clean() {

      }
    }, index);
  }

  @Override
  protected void configureDebuggerModel() {
    debuggerModel.addDebugClassSource(Fibonacci.class, new DebugClassSource(true, 14, 27) {
      @Override
      public String getContentsAsString() {
        return inputStreamToString(getClass().getResourceAsStream("Fibonacci.java"));
      }
    });
  }

  @Override
  protected void configureDereferencers() {

  }

  @Override
  protected void configureVirtualMachineLaunch(Map<String, Connector.Argument> arguments) {

  }

}
