package com.swilkins.ScrabbleViz.debug;

import com.sun.jdi.*;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.swilkins.ScrabbleViz.debug.BreakpointManager.Breakpoint;
import com.swilkins.ScrabbleViz.debug.exception.InvalidBreakpointException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.swilkins.ScrabbleViz.utility.Unpackers.unpackReference;

public class Debugger {

  private BreakpointManager breakpointManager = new BreakpointManager();

  public BreakpointManager getBreakpointManager() {
    return breakpointManager;
  }

  public void submitClassPrepareRequests(VirtualMachine vm) {
    for (String className : breakpointManager.getClassNames()) {
      enableClassPrepareRequest(vm, className);
    }
  }

  public void enableExceptionRequest(VirtualMachine vm) {
    vm.eventRequestManager().createExceptionRequest(null, true, true).enable();
  }

  public void enableClassPrepareRequest(VirtualMachine vm, String className) {
    ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
    classPrepareRequest.addClassFilter(className);
    classPrepareRequest.enable();
  }

  public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event) throws AbsentInformationException, ClassNotFoundException {
    ClassType classType = (ClassType) event.referenceType();
    Class<?> clazz = Class.forName(classType.name());
    for (Map.Entry<Integer, Breakpoint> entry : breakpointManager.get(clazz).entrySet()) {
      int lineNumber = entry.getKey();
      List<Location> possibleLocations = classType.locationsOfLine(lineNumber);
      if (!possibleLocations.isEmpty()) {
        Location location = possibleLocations.get(0);
        BreakpointRequest breakpointRequest = vm.eventRequestManager().createBreakpointRequest(location);
        entry.getValue().setRequest(breakpointRequest);
        breakpointRequest.enable();
      } else {
        throw new InvalidBreakpointException(clazz, lineNumber);
      }
    }
  }

  public Map<String, Object> unpackVariables(ThreadReference thread) throws AbsentInformationException, IncompatibleThreadStateException {
    StackFrame frame = thread.frame(0);
    Map<String, Object> unpackedVariables = new HashMap<>();
    for (Map.Entry<LocalVariable, Value> entry : frame.getValues(frame.visibleVariables()).entrySet()) {
      unpackedVariables.put(entry.getKey().name(), unpackReference(thread, entry.getValue()));
    }
    return unpackedVariables;
  }

}
