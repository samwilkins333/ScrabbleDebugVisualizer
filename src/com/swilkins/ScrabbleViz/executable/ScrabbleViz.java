package com.swilkins.ScrabbleViz.executable;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import com.swilkins.ScrabbleBase.Generation.Generator;
import com.swilkins.ScrabbleViz.debugClass.DebugClass;
import com.swilkins.ScrabbleViz.debugClass.DebugClassLocation;
import com.swilkins.ScrabbleViz.debugClass.DebugClassSource;
import com.swilkins.ScrabbleViz.debugClass.DebugClassViewer;
import com.swilkins.ScrabbleViz.utility.Invokable;
import com.swilkins.ScrabbleViz.view.WatchView;

import javax.swing.*;
import java.awt.EventQueue;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.swilkins.ScrabbleViz.utility.Unpackers.unpackVariables;
import static com.swilkins.ScrabbleViz.utility.Utilities.inputStreamToString;
import static com.swilkins.ScrabbleViz.utility.Utilities.toClass;

public class ScrabbleViz {
  private static DebugClassViewer debugClassViewer;
  private static WatchView watchView;
  private static final Class<?> mainClass = GeneratorTarget.class;

  private static VirtualMachine vm;
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
    initializeDebugClassViewer();

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    Dimension constraints = new Dimension(dimension.width, dimension.height / 3);
    debugClassViewer.setPreferredSize(constraints);
    debugClassViewer.setMinimumSize(constraints);
    debugClassViewer.setMaximumSize(constraints);
    debugClassViewer.setSize(constraints);

    panel.add(debugClassViewer);

    watchView = new WatchView(new Dimension(dimension.width / 3, dimension.height / 3));

    JPanel controls = new JPanel();
    controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
    JButton resume = new JButton("Resume");
    resume.addActionListener(e -> {
      deleteActiveStepRequest();
      signalDebugger();
    });
    controls.add(resume);

    JButton controlButton;

    controlButton = new JButton("Step Over");
    controlButton.addActionListener(e -> activateStepRequest(StepRequest.STEP_OVER));
    controls.add(controlButton);

    controlButton = new JButton("Step Into");
    controlButton.addActionListener(e -> activateStepRequest(StepRequest.STEP_INTO));
    controls.add(controlButton);

    controlButton = new JButton("Step Out");
    controlButton.addActionListener(e -> activateStepRequest(StepRequest.STEP_OUT));
    controls.add(controlButton);

    controlButton = new JButton("Toggle Breakpoint");
    controlButton.addActionListener(e -> {
      try {
        debugClassViewer.toggleBreakpointAt(debugClassViewer.getSelectedLocation());
      } catch (AbsentInformationException ex) {
        debugClassViewer.reportException(ex);
      }
    });
    controls.add(controlButton);

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
        activeStepRequest.disable();
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
      EventSet eventSet;
      try {
        vm = connectAndLaunchVM();
        EventRequestManager eventRequestManager = vm.eventRequestManager();
        debugClassViewer.submitDebugClassSources(eventRequestManager);
        debugClassViewer.enableExceptionReporting(eventRequestManager, false);
        while ((eventSet = vm.eventQueue().remove()) != null) {
          for (Event event : eventSet) {
            if (event instanceof LocatableEvent) {
              threadReference = ((LocatableEvent) event).thread();
            }
            if (event instanceof ClassPrepareEvent) {
              debugClassViewer.createDebugClassFor(eventRequestManager, (ClassPrepareEvent) event);
            } else if (event instanceof ExceptionEvent) {
              debugClassViewer.reportVirtualMachineException((ExceptionEvent) event);
            } else if (event instanceof BreakpointEvent) {
              deleteActiveStepRequest();
              suspendAndVisit((LocatableEvent) event);
            } else if (event instanceof StepEvent) {
              Class<?> clazz = toClass(((StepEvent) event).location());
              if (clazz != null && debugClassViewer.getDebugClassFor(clazz) != null) {
                suspendAndVisit((LocatableEvent) event);
              }
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

  private static void initializeDebugClassViewer() throws IOException {
    debugClassViewer = new DebugClassViewer(null);

    debugClassViewer.addDebugClassSource(
            GeneratorTarget.class,
            new DebugClassSource(22) {
              @Override
              public String getContentsAsString() {
                InputStream debugClassStream = ScrabbleViz.class.getResourceAsStream("GeneratorTarget.java");
                return inputStreamToString(debugClassStream);
              }
            }
    );

    File file = new File("../lib/scrabble-base-jar-with-dependencies.jar");
    JarFile jarFile = new JarFile(file);
    JarEntry generator = jarFile.getJarEntry("com/swilkins/ScrabbleBase/Generation/Generator.java");
    debugClassViewer.addDebugClassSource(
            Generator.class,
            new DebugClassSource(203) {
              @Override
              public String getContentsAsString() {
                try {
                  InputStream debugClassStream = jarFile.getInputStream(generator);
                  return inputStreamToString(debugClassStream);
                } catch (IOException e) {
                  return null;
                }
              }
            }
    );
  }

  private static void suspendAndVisit(LocatableEvent event) throws AbsentInformationException, IncompatibleThreadStateException, ClassNotFoundException {
    ThreadReference thread = event.thread();
    Location location = event.location();
    Class<?> clazz = toClass(location);

    DebugClass debugClass = debugClassViewer.getDebugClassFor(clazz);
    if (debugClass == null) {
      return;
    }
    DebugClassLocation selectedLocation = new DebugClassLocation(debugClass, location.lineNumber());
    debugClassViewer.setSelectedLocation(selectedLocation);
    watchView.updateFrom(location, unpackVariables(thread));
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