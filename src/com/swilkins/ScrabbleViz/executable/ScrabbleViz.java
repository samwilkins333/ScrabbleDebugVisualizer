package com.swilkins.ScrabbleViz.executable;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.StepRequest;
import com.swilkins.ScrabbleBase.Generation.Generator;
import com.swilkins.ScrabbleViz.debug.BreakpointManager;
import com.swilkins.ScrabbleViz.view.SourceCodeView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.swilkins.ScrabbleViz.utility.Utilities.toClass;
import static com.swilkins.ScrabbleViz.utility.Utilities.unpackReference;

public class ScrabbleViz {
  private static SourceCodeView SOURCE_CODE_VIEW;
  private static TextArea VARIABLES_VIEW;

  private final Class<?> mainClass;
  private BreakpointManager breakpointManager = new BreakpointManager();

  public ScrabbleViz(Class<?> mainClass) {
    this.mainClass = mainClass;
  }

  public VirtualMachine connectAndLaunchVM() throws Exception {
    LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
    Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
    arguments.get("main").setValue(mainClass.getName());
    arguments.get("options").setValue("-cp \".:../lib/scrabble-base-jar-with-dependencies.jar\"");
    return launchingConnector.launch(arguments);
  }

  private static final Object LOCK = new Object();

  public static void main(String[] args) throws IOException {
    JFrame frame = new JFrame("Candidate Generation Visualizer");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
    frame.setSize(dimension.width, dimension.height);
    JButton button = new JButton("Continue");
    button.addActionListener(e -> {
      synchronized (LOCK) {
        LOCK.notifyAll();
      }
    });
    button.setAlignmentX(Component.CENTER_ALIGNMENT);
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
    frame.getContentPane().add(panel);
    frame.setVisible(true);

    new Thread(executeDebugger(frame)).start();
  }

  public static Runnable executeDebugger(JFrame frame) {
    return () -> {
      ScrabbleViz debugger = new ScrabbleViz(GeneratorTarget.class);
      debugger.breakpointManager.register(15, GeneratorTarget.class, 0);
      debugger.breakpointManager.register(114, Generator.class, 0);
      debugger.breakpointManager.register(134, Generator.class, 0);
      debugger.breakpointManager.register(24, GeneratorTarget.class, 0);
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
              if (debugger.breakpointManager.validate(location)) {
                SOURCE_CODE_VIEW.jumpToLine(location.lineNumber());
              }
            }
            if (event instanceof ExceptionEvent) {
              SOURCE_CODE_VIEW.setText("EXCEPTION\n" + unpackReference(((ExceptionEvent) event).thread(), ((ExceptionEvent) event).exception()) + "\n\n");
            }
            if (event instanceof BreakpointEvent) {
              displayUnpackedVariables("BREAKPOINT", debugger, ((BreakpointEvent) event).thread());
              debugger.enableStepRequest(vm, (BreakpointEvent) event);
            }
            if (event instanceof StepEvent) {
              Location location = ((StepEvent) event).location();
              if (debugger.breakpointManager.validate(location)) {
                displayUnpackedVariables("STEP", debugger, ((StepEvent) event).thread());
              }
            }
            vm.resume();
          }
        }
      } catch (VMDisconnectedException e) {
        if (!SOURCE_CODE_VIEW.getText().startsWith("EXCEPTION")) {
          frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    };
  }

  private static void displayUnpackedVariables(
          String prompt,
          ScrabbleViz debugger,
          ThreadReference thread
  ) throws AbsentInformationException, IncompatibleThreadStateException, ClassNotFoundException {
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

  public void enableClassPrepareRequest(VirtualMachine vm, String classFilter) {
    ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
    classPrepareRequest.addClassFilter(classFilter);
    classPrepareRequest.enable();
  }

  public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event) throws AbsentInformationException, ClassNotFoundException {
    ClassType classType = (ClassType) event.referenceType();
    Class<?> clazz = Class.forName(classType.name());
    for (int lineNumber : breakpointManager.keySet()) {
      if (breakpointManager.actionFor(lineNumber, clazz) != null) {
        List<Location> possibleLocations = classType.locationsOfLine(lineNumber);
        if (!possibleLocations.isEmpty()) {
          Location location = possibleLocations.get(0);
          BreakpointRequest breakpointRequest = vm.eventRequestManager().createBreakpointRequest(location);
          breakpointRequest.enable();
        }
      }
    }
  }

  public void enableStepRequest(VirtualMachine vm, BreakpointEvent event) throws ClassNotFoundException {
    Location location = event.location();
    Integer action;
    if ((action = breakpointManager.actionFor(location.lineNumber(), toClass(location))) != 0) {
      StepRequest request = vm.eventRequestManager().createStepRequest(event.thread(), StepRequest.STEP_LINE, action);
      request.enable();
    }
  }

  public Map<String, Object> unpackVariables(StackFrame frame, ThreadReference thread) throws AbsentInformationException, ClassNotFoundException {
    if (breakpointManager.validate(frame.location())) {
      Map<String, Object> unpackedVariables = new HashMap<>();
      for (Map.Entry<LocalVariable, Value> entry : frame.getValues(frame.visibleVariables()).entrySet()) {
        unpackedVariables.put(entry.getKey().name(), unpackReference(thread, entry.getValue()));
      }
      return unpackedVariables;
    }
    return null;
  }

}
