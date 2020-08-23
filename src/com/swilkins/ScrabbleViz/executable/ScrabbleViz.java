package com.swilkins.ScrabbleViz.executable;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.*;
import com.sun.jdi.request.StepRequest;
import com.swilkins.ScrabbleBase.Generation.Generator;
import com.swilkins.ScrabbleViz.debug.BreakpointManager;
import com.swilkins.ScrabbleViz.debug.BreakpointManager.Breakpoint;
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

  private static VirtualMachine vm;
  private static Debugger debugger;
  private static ThreadReference threadReference;
  private static StepRequest activeStepRequest;

  private static final Object suspensionLock = new Object();
  private static final Object stepRequestLock = new Object();

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

    sourceView.addLocationChangedListener((clazz, lineNumber) -> {
      Breakpoint breakpoint = debugger.getBreakpointManager().getBreakpointAt(clazz, lineNumber);
      String annotation = breakpoint != null ? breakpoint.getAnnotation() : "No breakpoint.";
      watchView.setAnnotation(annotation != null ? annotation : "No annotation provided.");
    });

    JPanel controls = new JPanel();
    controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
    JButton resume = new JButton("Resume");
    resume.addActionListener(e -> {
      deleteActiveStepRequest();
      signalDebugger();
    });
    controls.add(resume);

    JButton stepButton;

    stepButton = new JButton("Step Over");
    stepButton.addActionListener(e -> activateStepRequest(StepRequest.STEP_OVER));
    controls.add(stepButton);

    stepButton = new JButton("Step Into");
    stepButton.addActionListener(e -> activateStepRequest(StepRequest.STEP_INTO));
    controls.add(stepButton);

    stepButton = new JButton("Step Out");
    stepButton.addActionListener(e -> activateStepRequest(StepRequest.STEP_OUT));
    controls.add(stepButton);

    panel.add(controls);
    panel.add(watchView);

    frame.getContentPane().add(panel);
    Invokable onTerminated = () -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    new Thread(executeDebugger(onTerminated)).start();
  }

  private static void activateStepRequest(int stepRequestDepth) {
    synchronized (stepRequestLock) {
      if (activeStepRequest == null || activeStepRequest.depth() != stepRequestDepth) {
        deleteActiveStepRequest();
        activeStepRequest = vm.eventRequestManager().createStepRequest(threadReference, StepRequest.STEP_LINE, stepRequestDepth);
        activeStepRequest.enable();
      }
    }
    signalDebugger();
  }

  private static void deleteActiveStepRequest() {
    synchronized (stepRequestLock) {
      if (activeStepRequest != null) {
        vm.eventRequestManager().deleteEventRequest(activeStepRequest);
        activeStepRequest = null;
      }
    }
  }

  public static void signalDebugger() {
    synchronized (suspensionLock) {
      suspensionLock.notifyAll();
    }
  }

  private static Runnable executeDebugger(Invokable onTerminate) {
    return () -> {
      debugger = new Debugger();
      BreakpointManager breakpointManager = debugger.getBreakpointManager();
      breakpointManager.setBreakpointAt(GeneratorTarget.class, 22, "Completed preliminary setup.");
      breakpointManager.setBreakpointAt(Generator.class, 203, "The algorithm found a valid candidate!");
      EventSet eventSet;
      try {
        debugger.prepare(vm = connectAndLaunchVM());
        while ((eventSet = vm.eventQueue().remove()) != null) {
          for (Event event : eventSet) {
            if (event instanceof LocatableEvent) {
              threadReference = ((LocatableEvent) event).thread();
            }
            if (event instanceof ClassPrepareEvent) {
              debugger.setBreakPoints(vm, (ClassPrepareEvent) event);
            } else if (event instanceof ExceptionEvent) {
              sourceView.reportException((ExceptionEvent) event);
            } else if (event instanceof BreakpointEvent) {
              deleteActiveStepRequest();
              visit(debugger, (LocatableEvent) event);
            } else if (event instanceof StepEvent) {
              if (debugger.getBreakpointManager().validate(((StepEvent) event).location())) {
                synchronized (stepRequestLock) {
                  if (activeStepRequest.depth() == 1) {
                    deleteActiveStepRequest();
                  }
                }
              }
              visit(debugger, (LocatableEvent) event);
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

  private static void visit(Debugger debugger, LocatableEvent event) throws AbsentInformationException, IncompatibleThreadStateException, ClassNotFoundException {
    ThreadReference thread = event.thread();
    Location location = event.location();
    if (!debugger.getBreakpointManager().validate(location)) {
      return;
    }
    sourceView.setSource(toClass(location), debugger.getBreakpointManager());
    sourceView.setLine(location.lineNumber());
    watchView.updateFrom(location, debugger.unpackVariables(thread));
    synchronized (suspensionLock) {
      try {
        suspensionLock.wait();
      } catch (InterruptedException e) {
        System.exit(0);
      }
      watchView.clean();
    }
  }

}
