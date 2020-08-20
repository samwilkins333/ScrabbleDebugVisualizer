import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;

import javax.swing.*;
import java.util.*;

public class JDIExampleDebugger<T> {

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
    return launchingConnector.launch(arguments);
  }

  public static void main(String[] args) {
    Object syncObject = new Object();

    JFrame frame = new JFrame("My First GUI");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(300,300);
    JButton button = new JButton("Continue");
    button.addActionListener(e -> {
      synchronized (syncObject) {
        syncObject.notifyAll();
      }
    });
    frame.getContentPane().add(button);
    frame.setVisible(true);

    new Thread(getDebuggerAsChild(syncObject)).start();
  }

  public static Runnable getDebuggerAsChild(Object syncObject) {
    return () -> {
      JDIExampleDebugger<JDIExampleDebuggee> debuggerInstance = new JDIExampleDebugger<>();
      debuggerInstance.setDebugClass(JDIExampleDebuggee.class);
      int[] breakPointLines = {9, 15};
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
            if (event instanceof BreakpointEvent) {
              event.request().disable();
              Map<String, Object> unpackedVariables = debuggerInstance.unpackVariables((BreakpointEvent) event);
              if (unpackedVariables != null) {
                for (Map.Entry<String, Object> variable : unpackedVariables.entrySet()) {
                  System.out.printf("%s = ", variable.getKey());
                  String resolved;
                  if (variable.getValue() instanceof Object[]) {
                    resolved = Arrays.deepToString((Object[]) variable.getValue());
                  } else {
                    resolved = variable.getValue().toString();
                  }
                  System.out.println(resolved);
                }
              }
              synchronized (syncObject) {
                try {
                  syncObject.wait();
                } catch(InterruptedException e) {
                  System.exit(0);
                }
              }
            }
            vm.resume();
          }
        }
      } catch (VMDisconnectedException e) {
        System.out.println("Virtual Machine is disconnected");
      } catch (Exception e) {
        e.printStackTrace();
      }
    };
  }

  public void enableClassPrepareRequest(VirtualMachine vm) {
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

  public Map<String, Object> unpackVariables(LocatableEvent event) throws IncompatibleThreadStateException, AbsentInformationException {
    ThreadReference thread;
    StackFrame stackFrame = (thread = event.thread()).frame(0);
    if (stackFrame.location().toString().contains(debugClass.getName())) {
      Map<String, Object> unpackedVariables = new HashMap<>();
      Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
      System.out.printf("Variables at %s > \n", stackFrame.location().toString());
      for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
        unpackedVariables.put(entry.getKey().name(), unpackReference(thread, entry.getValue()));
      }
      return unpackedVariables;
    }
    return null;
  }

  private Object unpackReference(ThreadReference thread, Value value) {
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
            System.out.println(((StringReference) returned).value());
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
