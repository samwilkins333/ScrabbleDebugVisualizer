package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.StepRequest;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.swilkins.ScrabbleVisualizer.debug.DebuggerControl.*;
import static com.swilkins.ScrabbleVisualizer.utility.Utilities.inputStreamToString;

public abstract class Debugger {

  protected VirtualMachine virtualMachine;

  protected final DebuggerView view;
  protected final DebuggerModel model;
  private final Class<?> virtualMachineTargetClass;

  protected final Map<String, Deserializer> deserializers = new HashMap<>();
  private final Deserializer toString = (object, thread) ->
          deserializeReference(thread, invoke(object, thread, "toString", "()Ljava/lang/String;"));

  private boolean started;

  public Debugger(Class<?> virtualMachineTargetClass) throws Exception {
    this.virtualMachineTargetClass = virtualMachineTargetClass;

    model = new DebuggerModel();
    configureModel();

    view = new DebuggerView();

    view.setDefaultControlActionListeners(getDefaultControlActionListeners());
    configureView();

    configureDeserializers();
  }

  protected abstract void configureModel() throws IOException, ClassNotFoundException;

  protected abstract void configureView();

  protected abstract void configureDeserializers();

  protected abstract void configureVirtualMachineLaunch(Map<String, Connector.Argument> arguments);

  protected abstract void onVirtualMachineEvent(Event event) throws Exception;

  protected abstract void onVirtualMachineSuspension(Location location, Map<String, Object> unpackedVariables);

  protected abstract void onVirtualMachineContinuation();

  protected abstract void onVirtualMachineTermination(String virtualMachineOut, String virtualMachineError);

  private void start() {
    if (!started) {
      LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
      Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
      arguments.get("main").setValue(virtualMachineTargetClass.getName());

      configureVirtualMachineLaunch(arguments);

      started = true;
      new Thread(() -> {
        EventSet eventSet;
        try {
          virtualMachine = launchingConnector.launch(arguments);
          model.setEventRequestManager(virtualMachine.eventRequestManager());
          model.submitDebugClassSources();
          model.enableExceptionReporting(true, true);

          while ((eventSet = virtualMachine.eventQueue().remove()) != null) {
            for (Event event : eventSet) {
              if (event instanceof LocatableEvent) {
                model.setThreadReference(((LocatableEvent) event).thread());
              }
              if (event instanceof ClassPrepareEvent) {
                model.createDebugClassFrom((ClassPrepareEvent) event);
              } else if (event instanceof ExceptionEvent) {
                ExceptionEvent exceptionEvent = (ExceptionEvent) event;
                Object exception = deserializeReference(exceptionEvent.thread(), exceptionEvent.exception());
                view.reportException(exception.toString(), DebuggerExceptionType.VIRTUAL_MACHINE);
              }
              onVirtualMachineEvent(event);
            }
          }
        } catch (VMDisconnectedException e) {
          Process process = virtualMachine.process();
          String virtualMachineOut = inputStreamToString(process.getInputStream());
          String virtualMachineError = inputStreamToString(process.getErrorStream());
          onVirtualMachineTermination(virtualMachineOut, virtualMachineError);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }).start();
    }
  }

  private Map<DebuggerControl, ActionListener> getDefaultControlActionListeners() {
    Map<DebuggerControl, ActionListener> defaultControlActionListeners = new LinkedHashMap<>();
    defaultControlActionListeners.put(RUN, e -> {
      view.setControlButtonEnabled(RUN, false);
      if (!started) {
        start();
      } else {
        model.disableActiveStepRequest();
        model.resumeEventProcessing();
      }
    });
    defaultControlActionListeners.put(STEP_OVER, e -> {
      model.setActiveStepRequestDepth(StepRequest.STEP_OVER);
      model.resumeEventProcessing();
    });
    defaultControlActionListeners.put(STEP_INTO, e -> {
      model.setActiveStepRequestDepth(StepRequest.STEP_INTO);
      model.resumeEventProcessing();
    });
    defaultControlActionListeners.put(STEP_OUT, e -> {
      model.setActiveStepRequestDepth(StepRequest.STEP_OUT);
      model.resumeEventProcessing();
    });
    defaultControlActionListeners.put(TOGGLE_BREAKPOINT, e -> {
      try {
        DebugClassLocation selectedLocation = view.getSelectedLocation();
        BreakpointRequest breakpointRequest = model.getBreakpointRequestAt(selectedLocation);
        if (breakpointRequest == null) {
          model.createBreakpointRequest(selectedLocation);
        } else {
          model.setEventRequestEnabled(breakpointRequest, !breakpointRequest.isEnabled());
        }
        view.repaint();
      } catch (AbsentInformationException ex) {
        view.reportException(ex.toString(), DebuggerExceptionType.DEBUGGER);
      }
    });
    return defaultControlActionListeners;
  }

  protected void trySuspend(LocatableEvent event) throws AbsentInformationException, IncompatibleThreadStateException {
    Location location = event.location();
    Class<?> clazz = toClass(location);

    DebugClass debugClass = model.getDebugClassFor(clazz);
    if (debugClass == null) {
      return;
    }

    DebugClassLocation updatedLocation = new DebugClassLocation(debugClass, location.lineNumber());
    DebugClassLocation previousLocation = view.setSelectedLocation(updatedLocation);

    Integer activeStepRequestDepth = model.getActiveStepRequestDepth();
    if (activeStepRequestDepth != null && updatedLocation.equals(previousLocation)) {
      if (activeStepRequestDepth == StepRequest.STEP_INTO || activeStepRequestDepth == StepRequest.STEP_OUT) {
        return;
      }
    }

    onVirtualMachineSuspension(location, deserializeVariables(event.thread()));
    view.setAllControlButtonsEnabled(true);
    model.awaitEventProcessingContinuation();
    view.setAllControlButtonsEnabled(false);
    onVirtualMachineContinuation();
  }

  protected Class<?> toClass(Location location) {
    Class<?> result;
    String className = location.toString().split(":")[0];
    try {
      result = Class.forName(className);
    } catch (ClassNotFoundException e) {
      result = null;
    }
    return result;
  }

  private Deserializer getDeserializerFor(ObjectReference object) {
    Deserializer deserializer = toString;
    try {
      Class<?> clazz = Class.forName(object.referenceType().name());
      while (clazz != Object.class) {
        Deserializer existing = deserializers.get(clazz.getName());
        if (existing != null) {
          deserializer = existing;
          break;
        }
        clazz = clazz.getSuperclass();
      }
    } catch (ClassNotFoundException ignored) {
    }
    return deserializer;
  }

  protected Value invoke(ObjectReference object, ThreadReference thread, String toInvokeName, String signature) {
    try {
      Method toInvoke;
      ReferenceType referenceType = object.referenceType();
      if (signature != null) {
        toInvoke = referenceType.methodsByName(toInvokeName, signature).get(0);
      } else {
        toInvoke = referenceType.methodsByName(toInvokeName).get(0);
      }
      return object.invokeMethod(thread, toInvoke, Collections.emptyList(), 0);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private Map<String, Object> deserializeVariables(ThreadReference thread) throws AbsentInformationException, IncompatibleThreadStateException {
    StackFrame frame = thread.frame(0);
    Map<String, Object> deserializedVariables = new HashMap<>();
    Map<LocalVariable, Value> values = frame.getValues(frame.visibleVariables());
    model.overrideAllEventRequests();
    for (Map.Entry<LocalVariable, Value> entry : values.entrySet()) {
      deserializedVariables.put(entry.getKey().name(), deserializeReference(thread, entry.getValue()));
    }
    model.restoreAllEventRequests();
    return deserializedVariables;
  }

  protected Object deserializeReference(ThreadReference thread, Value value) {
    if (value instanceof ObjectReference) {
      ((ObjectReference) value).disableCollection();
    }
    if (value instanceof ArrayReference) {
      ArrayReference arrayReference = (ArrayReference) value;
      Object[] collector = new Object[arrayReference.length()];
      for (int i = 0; i < arrayReference.length(); i++) {
        collector[i] = (deserializeReference(thread, arrayReference.getValue(i)));
      }
      return collector;
    } else if (value instanceof StringReference) {
      return ((StringReference) value).value();
    } else if (value instanceof ObjectReference) {
      ObjectReference ref = (ObjectReference) value;
      return getDeserializerFor(ref).deserialize(ref, thread);
    } else if (value instanceof PrimitiveValue) {
      PrimitiveValue primitiveValue = (PrimitiveValue) value;
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