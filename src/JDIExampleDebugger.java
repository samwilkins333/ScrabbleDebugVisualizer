import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

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
    int[] breakPointLines = {8, 10};
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
            debuggerInstance.displayVariables((BreakpointEvent) event);
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

  public void displayVariables(LocatableEvent event) throws IncompatibleThreadStateException, AbsentInformationException {
    StackFrame stackFrame = event.thread().frame(0);
    if (stackFrame.location().toString().contains(debugClass.getName())) {
      Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
      System.out.printf("Variables at %s > \n", stackFrame.location().toString());
      for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
        System.out.println(entry.getKey());
        if (entry.getValue().type() instanceof ArrayType) {
          ArrayReference array = (ArrayReference)entry.getValue();
          for (int i = 0; i < array.length(); i++) {
            Value item = array.getValue(i);
            if (item.type() instanceof ArrayType) {
              ArrayReference nested = (ArrayReference)item;
              for (int j = 0; j < nested.length(); j++) {
                Value nestedItem = nested.getValue(j);
                if (nestedItem instanceof StringReference) {
                  System.out.println(((StringReference)nestedItem).value());
                }
              }
            } else {
              System.out.println(item);
            }
          }
        } else {
          System.out.println(entry.getValue());
        }
      }
    }
  }

}
