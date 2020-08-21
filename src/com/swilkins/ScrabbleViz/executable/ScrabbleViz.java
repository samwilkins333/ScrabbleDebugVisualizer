package com.swilkins.ScrabbleViz.executable;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.*;
import com.swilkins.ScrabbleBase.Generation.Generator;
import com.swilkins.ScrabbleViz.debug.BreakpointManager;
import com.swilkins.ScrabbleViz.debug.Debugger;
import com.swilkins.ScrabbleViz.utility.Invokable;
import com.swilkins.ScrabbleViz.view.SourceView;

import javax.swing.*;
import java.awt.*;
import java.awt.EventQueue;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.swilkins.ScrabbleViz.utility.Utilities.inputStreamToString;
import static com.swilkins.ScrabbleViz.utility.Utilities.toClass;

public class ScrabbleViz {
  private static SourceView sourceView;
  private static TextArea variableView;
  private static final Class<?> mainClass = GeneratorTarget.class;

  private static final Object displayLock = new Object();

  public static void main(String[] args) {
    EventQueue.invokeLater(() -> {
      Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
      JFrame frame = new JFrame("Candidate Generation Visualizer");
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setSize(dimension.width, dimension.height);
      Invokable onTerminate = () -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));

      try {
        populateChildren(frame.getContentPane());
      } catch (IOException e) {
        e.printStackTrace();
      }

      frame.setVisible(true);
      frame.setResizable(false);

      new Thread(executeDebugger(onTerminate)).start();
    });
  }

  private static void populateChildren(Container container) throws IOException {
    initializeSourceView();

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(sourceView);

    variableView = new TextArea();
    variableView.setEditable(false);

    JPanel controls = new JPanel();
    controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
    JButton resume = new JButton("Resume");
    resume.setAlignmentX(Component.CENTER_ALIGNMENT);
    resume.addActionListener(e -> {
      synchronized (displayLock) {
        displayLock.notifyAll();
      }
    });
    controls.add(resume);
    controls.add(new JButton("Step Over"));
    controls.add(new JButton("Step Into"));
    controls.add(new JButton("Step Out"));
    panel.add(controls);
    panel.add(variableView);

    container.add(panel);
  }

  private static Runnable executeDebugger(Invokable onTerminate) {
    return () -> {
      Debugger debugger = new Debugger();
      BreakpointManager breakpointManager = debugger.getBreakpointManager();
      breakpointManager.register(15, GeneratorTarget.class, 0);
      breakpointManager.register(114, Generator.class, 0);
      breakpointManager.register(134, Generator.class, 0);
      breakpointManager.register(24, GeneratorTarget.class, 0);
      VirtualMachine vm;
      EventSet eventSet;
      try {
        debugger.prepare(vm = connectAndLaunchVM());
        while ((eventSet = vm.eventQueue().remove()) != null) {
          for (Event event : eventSet) {
            if (event instanceof ClassPrepareEvent) {
              debugger.setBreakPoints(vm, (ClassPrepareEvent) event);
            } else if (event instanceof ExceptionEvent) {
              sourceView.reportException((ExceptionEvent) event);
            } else if (event instanceof BreakpointEvent) {
              tryDisplayVariables(debugger, "BREAKPOINT", (LocatableEvent) event);
              debugger.enableStepRequest(vm, (BreakpointEvent) event);
            } else if (event instanceof StepEvent) {
              tryDisplayVariables(debugger, "STEP", (LocatableEvent) event);
            }
            vm.resume();
          }
        }
      } catch (VMDisconnectedException e) {
        onTerminate.invoke();
      } catch (Exception e) {
        e.printStackTrace();
      }
    };
  }

  private static VirtualMachine connectAndLaunchVM() throws Exception {
    LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
    Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
    arguments.get("main").setValue(mainClass.getName());
    arguments.get("options").setValue("-cp \".:../lib/scrabble-base-jar-with-dependencies.jar\"");
    return launchingConnector.launch(arguments);
  }

  private static void initializeSourceView() throws IOException {
    sourceView = new SourceView();
    String raw;

    raw = inputStreamToString(ScrabbleViz.class.getResourceAsStream("GeneratorTarget.java"));
    sourceView.addSource(GeneratorTarget.class, raw);

    File file = new File("../lib/scrabble-base-jar-with-dependencies.jar");
    JarFile jarFile = new JarFile(file);
    JarEntry generator = jarFile.getJarEntry("com/swilkins/ScrabbleBase/Generation/Generator.java");
    raw = inputStreamToString(jarFile.getInputStream(generator));
    sourceView.addSource(Generator.class, raw);
  }

  private static void tryDisplayVariables(Debugger debugger, String prompt, LocatableEvent event) throws AbsentInformationException, IncompatibleThreadStateException, ClassNotFoundException {
    ThreadReference thread = event.thread();
    Location location = event.location();
    if (!debugger.getBreakpointManager().validate(location)) {
      return;
    }
    sourceView.highlightLine(toClass(location), location.lineNumber());
    StringBuilder variables = new StringBuilder(prompt).append("\n");
    StackFrame frame = thread.frame(0);
    variables.append(frame.location().toString()).append("\n\n");
    Map<String, Object> unpackedVariables = debugger.unpackVariables(frame, thread);
    for (Map.Entry<String, Object> variable : unpackedVariables.entrySet()) {
      String resolved;
      if (variable.getValue() instanceof Object[]) {
        resolved = Arrays.deepToString((Object[]) variable.getValue());
      } else {
        resolved = variable.getValue().toString();
      }
      variables.append(variable.getKey()).append(" = ").append(resolved).append("\n");
    }
    variableView.setText(variables.toString());
    synchronized (displayLock) {
      try {
        displayLock.wait();
      } catch (InterruptedException e) {
        System.exit(0);
      }
      variableView.setText("Running...");
    }
  }

}
