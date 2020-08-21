package com.swilkins.ScrabbleViz.debug;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.StepRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.swilkins.ScrabbleViz.utility.Utilities.toClass;
import static com.swilkins.ScrabbleViz.utility.Utilities.unpackReference;

public class Debugger {

  private final Class<?> mainClass;
  private BreakpointManager breakpointManager = new BreakpointManager();

  public Debugger(Class<?> mainClass) {
    this.mainClass = mainClass;
  }

  public VirtualMachine connectAndLaunchVM() throws Exception {
    LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
    Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
    arguments.get("main").setValue(mainClass.getName());
    arguments.get("options").setValue("-cp \".:../lib/scrabble-base-jar-with-dependencies.jar\"");
    return launchingConnector.launch(arguments);
  }

  public BreakpointManager getBreakpointManager() {
    return breakpointManager;
  }

  public void enableClassPrepareRequest(VirtualMachine vm, String classFilter) {
    ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
    classPrepareRequest.addClassFilter(classFilter);
    classPrepareRequest.enable();
  }

  public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event) throws AbsentInformationException, ClassNotFoundException {
    ClassType classType = (ClassType) event.referenceType();
    for (int lineNumber : breakpointManager.keySet()) {
      if (breakpointManager.actionFor(lineNumber, Class.forName(classType.name())) != null) {
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
