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
import com.swilkins.ScrabbleViz.view.WatchView;

import javax.swing.*;
import java.awt.EventQueue;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.swilkins.ScrabbleViz.utility.Utilities.inputStreamToString;
import static com.swilkins.ScrabbleViz.utility.Utilities.toClass;

public class ScrabbleViz {
  private static SourceView sourceView;
  private static WatchView watchView;
  private static final Class<?> mainClass = GeneratorTarget.class;

  private static final Object displayLock = new Object();

  public static void main(String[] args) {
    EventQueue.invokeLater(() -> {
      Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
      JFrame frame = new JFrame(ScrabbleViz.class.getSimpleName());
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setSize(dimension.width, dimension.height);

      try {
        populateChildren(frame, dimension);
      } catch (IOException e) {
        e.printStackTrace();
      }

      frame.setVisible(true);
      frame.setResizable(false);
    });
  }

  private static void populateChildren(JFrame frame, Dimension dimension) throws IOException {
    initializeSourceView();

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    Dimension constraints = new Dimension(dimension.width, dimension.height / 3);
    sourceView.setPreferredSize(constraints);
    sourceView.setMinimumSize(constraints);
    sourceView.setMaximumSize(constraints);
    sourceView.setSize(constraints);

    panel.add(sourceView);

    watchView = new WatchView(new Dimension(dimension.width / 3, dimension.height / 3));

    JPanel controls = new JPanel();
    controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
    JButton resume = new JButton("Resume");
    resume.addActionListener(e -> {
      synchronized (displayLock) {
        displayLock.notifyAll();
      }
    });
    JButton start = new JButton("Start");
    start.addActionListener(e -> {
      Invokable onTerminated = () -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
      new Thread(executeDebugger(onTerminated)).start();
      controls.remove(start);
      controls.validate();
    });
    controls.add(start);
    controls.add(resume);
    controls.add(new JButton("Step Over"));
    controls.add(new JButton("Step Into"));
    controls.add(new JButton("Step Out"));
    panel.add(controls);
    panel.add(watchView);

    frame.getContentPane().add(panel);
  }

  private static Runnable executeDebugger(Invokable onTerminate) {
    return () -> {
      Debugger debugger = new Debugger();
      BreakpointManager breakpointManager = debugger.getBreakpointManager();
      breakpointManager.register(22, GeneratorTarget.class, 0);
      breakpointManager.register(196, Generator.class, 0);
      breakpointManager.register(203, Generator.class, 0);
      breakpointManager.register(258, Generator.class, 0);
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
              tryDisplayVariables(debugger, (LocatableEvent) event);
              debugger.enableStepRequest(vm, (BreakpointEvent) event);
            } else if (event instanceof StepEvent) {
              tryDisplayVariables(debugger, (LocatableEvent) event);
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
    sourceView = new SourceView(null, null, Color.decode("#00FFFF"));
    String raw;

    raw = inputStreamToString(ScrabbleViz.class.getResourceAsStream("GeneratorTarget.java"));
    sourceView.addSource(GeneratorTarget.class, raw);

    File file = new File("../lib/scrabble-base-jar-with-dependencies.jar");
    JarFile jarFile = new JarFile(file);
    JarEntry generator = jarFile.getJarEntry("com/swilkins/ScrabbleBase/Generation/Generator.java");
    raw = inputStreamToString(jarFile.getInputStream(generator));
    sourceView.addSource(Generator.class, raw);
  }

  private static void tryDisplayVariables(Debugger debugger, LocatableEvent event) throws AbsentInformationException, IncompatibleThreadStateException, ClassNotFoundException {
    ThreadReference thread = event.thread();
    Location location = event.location();
    if (!debugger.getBreakpointManager().validate(location)) {
      return;
    }
    sourceView.highlightLine(toClass(location), location.lineNumber());
    watchView.updateFrom(location, debugger.unpackVariables(thread));
    synchronized (displayLock) {
      try {
        displayLock.wait();
      } catch (InterruptedException e) {
        System.exit(0);
      }
      watchView.clean();
    }
  }

}
