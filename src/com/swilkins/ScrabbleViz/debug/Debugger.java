package com.swilkins.ScrabbleViz.debug;

import com.sun.jdi.*;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.StepRequest;
import com.swilkins.ScrabbleViz.debug.exception.InvalidBreakpointException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.swilkins.ScrabbleViz.utility.Utilities.toClass;
import static com.swilkins.ScrabbleViz.utility.Utilities.unpackReference;

public class Debugger {

  private BreakpointManager breakpointManager = new BreakpointManager();

  public BreakpointManager getBreakpointManager() {
    return breakpointManager;
  }

  public void prepare(VirtualMachine vm) {
    enableExceptionRequest(vm);
    for (Class<?> clazz : breakpointManager.getClasses()) {
      enableClassPrepareRequest(vm, clazz);
    }
  }

  public void enableExceptionRequest(VirtualMachine vm) {
    vm.eventRequestManager().createExceptionRequest(null, true, true).enable();
  }

  public void enableClassPrepareRequest(VirtualMachine vm, Class<?> clazz) {
    ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
    classPrepareRequest.addClassFilter(clazz.getName());
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
        } else {
          throw new InvalidBreakpointException(clazz, lineNumber);
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

  public Map<String, Object> unpackVariables(StackFrame frame, ThreadReference thread) throws AbsentInformationException {
    Map<String, Object> unpackedVariables = new HashMap<>();
    for (Map.Entry<LocalVariable, Value> entry : frame.getValues(frame.visibleVariables()).entrySet()) {
      unpackedVariables.put(entry.getKey().name(), unpackReference(thread, entry.getValue()));
    }
    return unpackedVariables;
  }

}
