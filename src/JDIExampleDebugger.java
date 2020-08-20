import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;

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
    JDIExampleDebugger<JDIExampleDebuggee> debuggerInstance = new JDIExampleDebugger<>();
    debuggerInstance.setDebugClass(JDIExampleDebuggee.class);
    int[] breakPointLines = {9, 15};
    debuggerInstance.setBreakPointLines(breakPointLines);
    Scanner scanner = new Scanner(System.in);
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
            System.out.println("BREAKPOINT");
            event.request().disable();
            Map<String, Object> unpackedVariables = debuggerInstance.unpackVariables((BreakpointEvent) event);
            if (unpackedVariables != null) {
              System.out.println(unpackedVariables);
            }
            try {
              System.out.println("Please enter anything to continue execution...");
              scanner.nextLine();
            } catch(IllegalStateException | NoSuchElementException e) {
              System.out.println("System.in was closed; exiting");
              System.exit(0);
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
        List<Object> collector = new ArrayList<>();
        ArrayReference arrayReference = (ArrayReference)value;
        for (int i = 0; i < arrayReference.length(); i++) {
          collector.add(unpackReference(thread, arrayReference.getValue(i)));
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
