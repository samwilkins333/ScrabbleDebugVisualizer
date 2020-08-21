package com.swilkins.ScrabbleViz.executable;

import com.sun.jdi.*;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.*;
import com.swilkins.ScrabbleBase.Generation.Generator;
import com.swilkins.ScrabbleViz.debug.BreakpointManager;
import com.swilkins.ScrabbleViz.debug.Debugger;
import com.swilkins.ScrabbleViz.utility.Invokable;
import com.swilkins.ScrabbleViz.view.SourceCodeView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.swilkins.ScrabbleViz.utility.Utilities.unpackReference;

public class ScrabbleViz {
  private static SourceCodeView SOURCE_CODE_VIEW;
  private static TextArea VARIABLES_VIEW;
  private static final Class<?> mainClass = GeneratorTarget.class;

  private static final Object LOCK = new Object();

  public static void main(String[] args) throws IOException {
    JButton button = new JButton("Continue");
    button.setAlignmentX(Component.CENTER_ALIGNMENT);
    button.addActionListener(e -> {
      synchronized (LOCK) {
        LOCK.notifyAll();
      }
    });

    File file = new File("../lib/scrabble-base-jar-with-dependencies.jar");
    JarFile jarFile = new JarFile(file);
    JarEntry generator = jarFile.getJarEntry("com/swilkins/ScrabbleBase/Generation/Generator.java");
    SOURCE_CODE_VIEW = new SourceCodeView(jarFile.getInputStream(generator));

    JScrollPane scrollPane = new JScrollPane(SOURCE_CODE_VIEW);
    scrollPane.setWheelScrollingEnabled(false);

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(button);
    panel.add(scrollPane);

    VARIABLES_VIEW = new TextArea();
    VARIABLES_VIEW.setEditable(false);
    panel.add(VARIABLES_VIEW);

    Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
    JFrame frame = new JFrame("Candidate Generation Visualizer");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(dimension.width, dimension.height);
    Invokable onTerminate = () -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    frame.getContentPane().add(panel);
    frame.setVisible(true);

    new Thread(executeDebugger(onTerminate)).start();
  }

  public static Runnable executeDebugger(Invokable onTerminate) {
    return () -> {
      Debugger debugger = new Debugger(mainClass);
      BreakpointManager breakpointManager = debugger.getBreakpointManager();
      breakpointManager.register(15, GeneratorTarget.class, 0);
      breakpointManager.register(114, Generator.class, 0);
      breakpointManager.register(134, Generator.class, 0);
      breakpointManager.register(24, GeneratorTarget.class, 0);
      VirtualMachine vm;
      try {
        vm = debugger.connectAndLaunchVM();
        vm.eventRequestManager().createExceptionRequest(null, true, true).enable();
        debugger.enableClassPrepareRequest(vm, Generator.class.getName());
        debugger.enableClassPrepareRequest(vm, GeneratorTarget.class.getName());
        EventSet eventSet;
        while ((eventSet = vm.eventQueue().remove()) != null) {
          for (Event event : eventSet) {
            if (event instanceof ClassPrepareEvent) {
              debugger.setBreakPoints(vm, (ClassPrepareEvent) event);
            }
            if (event instanceof LocatableEvent) {
              Location location = ((LocatableEvent) event).location();
              if (breakpointManager.validate(location)) {
                SOURCE_CODE_VIEW.jumpToLine(location.lineNumber());
              }
            }
            if (event instanceof ExceptionEvent) {
              SOURCE_CODE_VIEW.setText("EXCEPTION\n" + unpackReference(((ExceptionEvent) event).thread(), ((ExceptionEvent) event).exception()) + "\n\n");
            }
            if (event instanceof BreakpointEvent) {
              displayUnpackedVariables(debugger, "BREAKPOINT", ((BreakpointEvent) event).thread());
              debugger.enableStepRequest(vm, (BreakpointEvent) event);
            }
            if (event instanceof StepEvent) {
              Location location = ((StepEvent) event).location();
              if (breakpointManager.validate(location)) {
                displayUnpackedVariables(debugger, "STEP", ((StepEvent) event).thread());
              }
            }
            vm.resume();
          }
        }
      } catch (VMDisconnectedException e) {
        if (!SOURCE_CODE_VIEW.getText().startsWith("EXCEPTION")) {
          onTerminate.invoke();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    };
  }

  private static void displayUnpackedVariables(Debugger debugger, String prompt, ThreadReference thread) throws AbsentInformationException, IncompatibleThreadStateException, ClassNotFoundException {
    StringBuilder displayText = new StringBuilder(prompt).append("\n");
    StackFrame frame = thread.frame(0);
    displayText.append(frame.location().toString()).append("\n\n");
    Map<String, Object> unpackedVariables = debugger.unpackVariables(frame, thread);
    if (unpackedVariables != null) {
      for (Map.Entry<String, Object> variable : unpackedVariables.entrySet()) {
        String resolved;
        if (variable.getValue() instanceof Object[]) {
          resolved = Arrays.deepToString((Object[]) variable.getValue());
        } else {
          resolved = variable.getValue().toString();
        }
        displayText.append(variable.getKey()).append(" = ").append(resolved).append("\n");
      }
      VARIABLES_VIEW.setText(displayText.toString());
    }
    synchronized (LOCK) {
      try {
        LOCK.wait();
      } catch (InterruptedException e) {
        System.exit(0);
      }
      VARIABLES_VIEW.setText("Running...");
    }
  }

}
