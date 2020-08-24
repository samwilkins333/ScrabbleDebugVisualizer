package com.swilkins.ScrabbleViz.executable;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import com.swilkins.ScrabbleBase.Generation.Generator;
import com.swilkins.ScrabbleViz.debug.*;
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

public class ScrabbleViz extends Debugger {
  private static WatchView watchView;

  private static ThreadReference threadReference;
  private static StepRequest activeStepRequest;

  private static final Object suspensionLock = new Object();
  private static final Object stepRequestLock = new Object();

  private static Dimension screenSize;

  public ScrabbleViz() throws Exception {
    super(GeneratorTarget.class);
  }

  public static void main(String[] args) {
    EventQueue.invokeLater(() -> {
      screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      JFrame frame = new JFrame(ScrabbleViz.class.getSimpleName());
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setSize(screenSize.width, screenSize.height);
      frame.setResizable(false);

      try {
        new ScrabbleViz().populateWatchEnvironment(frame);
      } catch (Exception e) {
        e.printStackTrace();
      }

      frame.setVisible(true);
    });
  }

  @Override
  public void configureVirtualMachineLaunch(Map<String, Connector.Argument> arguments) {
    arguments.get("options").setValue("-cp \".:../lib/scrabble-base-jar-with-dependencies.jar\"");
  }

  @Override
  public void configureView() {
    view.setOptions(new DebuggerViewOptions(Color.WHITE, Color.BLACK));

    Dimension topThird = new Dimension(screenSize.width, screenSize.height / 3);
    view.setPreferredSize(topThird);
    view.setMinimumSize(topThird);
    view.setMaximumSize(topThird);
    view.setSize(topThird);
  }

  @Override
  public void configureModel() throws IOException {
    model.addDebugClassSource(
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
    model.addDebugClassSource(
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

  private void populateWatchEnvironment(JFrame frame) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(view);

    watchView = new WatchView(new Dimension(screenSize.width / 3, screenSize.height / 3));

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
        view.toggleBreakpointAt(view.getSelectedLocation());
      } catch (AbsentInformationException ex) {
        view.reportException(ex);
      }
    });
    controls.add(controlButton);

    panel.add(controls);
    panel.add(watchView);

    frame.getContentPane().add(panel);
    Invokable onTerminated = () -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    new Thread(executeDebugger(onTerminated)).start();
  }

  private Runnable executeDebugger(Invokable onTerminate) {
    return () -> {
      EventSet eventSet;
      try {
        EventRequestManager eventRequestManager = virtualMachine.eventRequestManager();
        model.submitDebugClassSources(eventRequestManager);
        model.enableExceptionReporting(eventRequestManager, false);
        while ((eventSet = virtualMachine.eventQueue().remove()) != null) {
          for (Event event : eventSet) {
            if (event instanceof LocatableEvent) {
              threadReference = ((LocatableEvent) event).thread();
            }
            if (event instanceof ClassPrepareEvent) {
              model.createDebugClassFor(eventRequestManager, (ClassPrepareEvent) event);
            } else if (event instanceof ExceptionEvent) {
              view.reportVirtualMachineException((ExceptionEvent) event);
            } else if (event instanceof BreakpointEvent) {
              deleteActiveStepRequest();
              suspendAndVisit((LocatableEvent) event);
            } else if (event instanceof StepEvent) {
              Class<?> clazz = toClass(((StepEvent) event).location());
              if (clazz != null && model.getDebugClassFor(clazz) != null) {
                suspendAndVisit((LocatableEvent) event);
              }
            }
            virtualMachine.resume();
          }
        }
      } catch (VMDisconnectedException e) {
        onTerminate.invoke();
      } catch (Exception e) {
        e.printStackTrace();
      }
    };
  }

  private void activateStepRequest(int stepRequestDepth) {
    synchronized (stepRequestLock) {
      if (activeStepRequest == null || activeStepRequest.depth() != stepRequestDepth) {
        deleteActiveStepRequest();
        activeStepRequest = virtualMachine.eventRequestManager().createStepRequest(threadReference, StepRequest.STEP_LINE, stepRequestDepth);
        activeStepRequest.enable();
      }
    }
    signalDebugger();
  }

  private void deleteActiveStepRequest() {
    synchronized (stepRequestLock) {
      if (activeStepRequest != null) {
        activeStepRequest.disable();
        virtualMachine.eventRequestManager().deleteEventRequest(activeStepRequest);
        activeStepRequest = null;
      }
    }
  }

  public void signalDebugger() {
    synchronized (suspensionLock) {
      suspensionLock.notifyAll();
    }
  }

  private void suspendAndVisit(LocatableEvent event) throws AbsentInformationException, IncompatibleThreadStateException {
    ThreadReference thread = event.thread();
    Location location = event.location();
    Class<?> clazz = toClass(location);

    DebugClass debugClass = model.getDebugClassFor(clazz);
    if (debugClass == null) {
      return;
    }
    DebugClassLocation selectedLocation = new DebugClassLocation(debugClass, location.lineNumber());
    view.setSelectedLocation(selectedLocation);
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