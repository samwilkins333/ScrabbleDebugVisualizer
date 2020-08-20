import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.event.Event;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.ExceptionRequest;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class CandidateGenerationVisualizer<T> {
  private static JTextPane VARIABLES_DISPLAY;

  private Class<T> debugClass;
  private int[] breakPointLines;

  public void setDebugClass(Class<T> debugClass) {
    this.debugClass = debugClass;
  }

  public void setBreakPointLines(int[] breakPointLines) {
    this.breakPointLines = breakPointLines;
  }

  public VirtualMachine connectAndLaunchVM() throws Exception {
    LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
    Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
    arguments.get("main").setValue(debugClass.getName());
    arguments.get("options").setValue("-cp \".:../lib/scrabble-base-jar-with-dependencies.jar\"");
    return launchingConnector.launch(arguments);
  }

  public static void main(String[] args) {
    Object syncObject = new Object();

    JFrame frame = new JFrame("Candidate Generation Visualizer");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
    frame.setSize(dimension.width,dimension.height);
    JButton button = new JButton("Continue");
    button.addActionListener(e -> {
      synchronized (syncObject) {
        syncObject.notifyAll();
      }
    });
    button.setAlignmentX(Component.CENTER_ALIGNMENT);
    VARIABLES_DISPLAY = new JTextPane();
    VARIABLES_DISPLAY.setEditable(false);
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(button);
    panel.add(VARIABLES_DISPLAY);
    frame.getContentPane().add(panel);
    frame.setVisible(true);

    new Thread(getDebuggerAsChild(syncObject)).start();
  }

  public static Runnable getDebuggerAsChild(Object syncObject) {
    return () -> {
      CandidateGenerationVisualizer<JDIExampleDebuggee> debuggerInstance = new CandidateGenerationVisualizer<>();
      debuggerInstance.setDebugClass(JDIExampleDebuggee.class);
      int[] breakPointLines = {12, 20};
      debuggerInstance.setBreakPointLines(breakPointLines);
      VirtualMachine vm;
      try {
        vm = debuggerInstance.connectAndLaunchVM();
        debuggerInstance.enableClassPrepareRequest(vm);
        EventSet eventSet;
        while ((eventSet = vm.eventQueue().remove()) != null) {
          for (Event event : eventSet) {
            if (event instanceof ClassPrepareEvent) {
              debuggerInstance.setBreakPoints(vm, (ClassPrepareEvent)event);
            }
            if (event instanceof ExceptionEvent) {
              VARIABLES_DISPLAY.setText("EXCEPTION\n" + unpackReference(((ExceptionEvent) event).thread(), ((ExceptionEvent) event).exception()) + "\n\n");
            }
            if (event instanceof BreakpointEvent) {
              event.request().disable();
              StringBuilder displayText = new StringBuilder("BREAKPOINT\n");
              StackFrame frame = ((BreakpointEvent) event).thread().frame(0);
              displayText.append(frame.location().toString()).append("\n\n");
              Map<String, Object> unpackedVariables = debuggerInstance.unpackVariables(frame, ((BreakpointEvent) event).thread());
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
                VARIABLES_DISPLAY.setText(displayText.toString());
              }
              synchronized (syncObject) {
                try {
                  syncObject.wait();
                } catch(InterruptedException e) {
                  System.exit(0);
                }
                VARIABLES_DISPLAY.setText("Running...");
              }
            }
            vm.resume();
          }
        }
      } catch (VMDisconnectedException e) {
        if (!VARIABLES_DISPLAY.getText().startsWith("EXCEPTION")) {
          VARIABLES_DISPLAY.setText("Virtual Machine is cleanly disconnected");
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    };
  }

  public void enableClassPrepareRequest(VirtualMachine vm) {
    ExceptionRequest exceptionRequest = vm.eventRequestManager().createExceptionRequest(null, true, true);
    exceptionRequest.enable();
    ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
    classPrepareRequest.addClassFilter(debugClass.getName());
    classPrepareRequest.enable();
  }

  public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event) throws AbsentInformationException {
    ClassType classType = (ClassType) event.referenceType();
    for (int lineNumber : breakPointLines) {
      Location location = classType.locationsOfLine(lineNumber).get(0);
      BreakpointRequest breakpointRequest = vm.eventRequestManager().createBreakpointRequest(location);
      breakpointRequest.enable();
    }
  }

  public Map<String, Object> unpackVariables(StackFrame frame, ThreadReference thread) throws AbsentInformationException {
    if (frame.location().toString().contains(debugClass.getName())) {
      Map<String, Object> unpackedVariables = new HashMap<>();
      for (Map.Entry<LocalVariable, Value> entry : frame.getValues(frame.visibleVariables()).entrySet()) {
        unpackedVariables.put(entry.getKey().name(), unpackReference(thread, entry.getValue()));
      }
      return unpackedVariables;
    }
    return null;
  }

  private static Object unpackReference(ThreadReference thread, Value value) {
      if (value instanceof ArrayReference) {
        ArrayReference arrayReference = (ArrayReference)value;
        Object[] collector = new Object[arrayReference.length()];
        for (int i = 0; i < arrayReference.length(); i++) {
          collector[i] = (unpackReference(thread, arrayReference.getValue(i)));
        }
        return collector;
      } else if (value instanceof StringReference) {
        return ((StringReference) value).value();
      } else if (value instanceof ObjectReference) {
        ObjectReference ref = (ObjectReference) value;
        Method toString = ref.referenceType()
                .methodsByName("toString", "()Ljava/lang/String;").get(0);
        try {
          Value returned = ref.invokeMethod(thread, toString, Collections.emptyList(), 0);
          if (returned instanceof StringReference) {
            return ((StringReference) returned).value();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      } else if (value instanceof PrimitiveValue) {
        PrimitiveValue primitiveValue = (PrimitiveValue)value;
        String subType = value.type().name();
        if (subType.equals("char")) {
          return primitiveValue.charValue();
        }
        if (subType.equals("boolean")) {
          return primitiveValue.booleanValue();
        }
        if (subType.equals("byte")) {
          return primitiveValue.byteValue();
        }
        if (subType.equals("double")) {
          return primitiveValue.doubleValue();
        }
        if (subType.equals("float")) {
          return primitiveValue.floatValue();
        }
        if (subType.equals("int")) {
          return primitiveValue.intValue();
        }
        if (subType.equals("long")) {
          return primitiveValue.longValue();
        }
        if (subType.equals("short")) {
          return primitiveValue.shortValue();
        }
      }
      return value;
  }

}
