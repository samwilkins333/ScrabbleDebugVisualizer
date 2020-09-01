package com.swilkins.ScrabbleVisualizer.executable;

import com.sun.jdi.connect.Connector;
import com.swilkins.ScrabbleVisualizer.debug.DebugClassSource;
import com.swilkins.ScrabbleVisualizer.debug.Debugger;
import com.swilkins.ScrabbleVisualizer.debug.DebuggerControl;

import javax.swing.*;
import java.net.URL;
import java.util.Map;

import static com.swilkins.ScrabbleVisualizer.debug.ScrabbleBaseDebugger.ICON_DIMENSION;
import static com.swilkins.ScrabbleVisualizer.utility.Utilities.createImageIconFrom;
import static com.swilkins.ScrabbleVisualizer.utility.Utilities.inputStreamToString;

public class FibonacciDebugger extends Debugger {

  public FibonacciDebugger() throws Exception {
    super(Fibonacci.class, null);
  }

  @Override
  protected void configureDebuggerModel() {
    debuggerModel.addDebugClassSource(Fibonacci.class, new DebugClassSource(true, 6, 12) {
      @Override
      public String getContentsAsString() {
        return inputStreamToString(getClass().getResourceAsStream("Fibonacci.java"));
      }
    });
  }

  @Override
  protected void configureDebuggerView() {
    debuggerSourceView.setOptions(null);

    for (DebuggerControl control : DebuggerControl.values()) {
      JButton controlButton = debuggerSourceView.addDefaultControlButton(control);
      URL iconUrl = getClass().getResource(String.format("../resource/icons/%s.png", control.getLabel()));
      controlButton.setIcon(createImageIconFrom(iconUrl, ICON_DIMENSION));
      controlButton.setFocusPainted(false);
    }
  }

  @Override
  protected void configureDereferencers() {

  }

  @Override
  protected void configureVirtualMachineLaunch(Map<String, Connector.Argument> arguments) {
    System.out.println(arguments);
  }

}
