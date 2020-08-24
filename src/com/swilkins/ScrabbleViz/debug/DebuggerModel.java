package com.swilkins.ScrabbleViz.debug;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ClassType;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;

import java.util.HashMap;
import java.util.Map;

public class DebuggerModel {

  private final Map<Class<?>, DebugClassSource> debugClassSources = new HashMap<>();
  private final Map<Class<?>, DebugClass> debugClasses = new HashMap<>();

  public void addDebugClassSource(Class<?> clazz, DebugClassSource debugClassSource) {
    debugClassSources.put(clazz, debugClassSource);
  }

  public void submitDebugClassSources(EventRequestManager eventRequestManager) {
    for (Class<?> clazz : debugClassSources.keySet()) {
      ClassPrepareRequest request = eventRequestManager.createClassPrepareRequest();
      request.addClassFilter(clazz.getName());
      request.enable();
    }
  }

  public void enableExceptionReporting(EventRequestManager eventRequestManager, boolean notifyCaught) {
    eventRequestManager.createExceptionRequest(null, notifyCaught, true).enable();
  }

  public void createDebugClassFor(EventRequestManager eventRequestManager, ClassPrepareEvent event) throws ClassNotFoundException, AbsentInformationException {
    ClassType classType = (ClassType) event.referenceType();
    Class<?> clazz = Class.forName(classType.name());
    DebugClassOperations operations = new DebugClassOperations(
            classType::locationsOfLine,
            eventRequestManager::createBreakpointRequest,
            eventRequestManager::deleteEventRequest
    );
    DebugClassSource debugClassSource = debugClassSources.get(clazz);
    DebugClass debugClass = new DebugClass(clazz, debugClassSource, operations);
    for (int compileTimeBreakpoint : debugClassSource.getCompileTimeBreakpoints()) {
      if (!debugClass.requestBreakpointAt(compileTimeBreakpoint)) {
        System.out.printf("Unable to create breakpoint at line %d in %s\n", compileTimeBreakpoint, clazz.getName());
      }
    }
    debugClasses.put(clazz, debugClass);
  }

  public DebugClass getDebugClassFor(Class<?> clazz) {
    return debugClasses.get(clazz);
  }

}
