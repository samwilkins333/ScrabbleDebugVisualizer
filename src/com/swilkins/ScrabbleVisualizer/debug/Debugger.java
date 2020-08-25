package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.swilkins.ScrabbleVisualizer.debug.DefaultDebuggerControl.*;
import static com.swilkins.ScrabbleVisualizer.utility.Utilities.inputStreamToString;

public abstract class Debugger {

  protected final VirtualMachine virtualMachine;
  protected final EventRequestManager eventRequestManager;

  protected final DebuggerView view;
  protected final DebuggerModel model;

  protected ThreadReference threadReference;
  protected StepRequest activeStepRequest;

  protected final Object eventProcessingControl = new Object();
  protected final Object stepRequestLock = new Object();

  protected final Map<String, Deserializer> deserializers = new HashMap<>();
  private final Deserializer toString = (object, thread) ->
          deserializeReference(thread, invoke(object, thread, "toString", "()Ljava/lang/String;"));

  public Debugger(Class<?> virtualMachineTargetClass) throws Exception {
    view = new DebuggerView();
    Map<DefaultDebuggerControl, ActionListener> defaultActionListeners = new LinkedHashMap<>();
    defaultActionListeners.put(RESUME, e -> resume());
    defaultActionListeners.put(STEP_OVER, e -> activateStepRequest(StepRequest.STEP_OVER));
    defaultActionListeners.put(STEP_INTO, e -> activateStepRequest(StepRequest.STEP_INTO));
    defaultActionListeners.put(STEP_OUT, e -> activateStepRequest(StepRequest.STEP_OUT));
    defaultActionListeners.put(TOGGLE_BREAKPOINT, e -> {
      try {
        view.toggleBreakpointAt(view.getSelectedLocation());
      } catch (AbsentInformationException ex) {
        view.reportException(ex.toString(), DebuggerExceptionType.DEBUGGER);
      }
    });
    view.setDefaultActionListeners(defaultActionListeners);
    configureView();

    model = new DebuggerModel();
    configureModel();

    configureDeserializers();

    LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
    Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
    arguments.get("main").setValue(virtualMachineTargetClass.getName());
    onVirtualMachineLaunch(arguments);
    virtualMachine = launchingConnector.launch(arguments);
    eventRequestManager = virtualMachine.eventRequestManager();
  }

  protected abstract void configureView();

  protected abstract void configureModel() throws IOException, ClassNotFoundException;

  protected abstract void configureDeserializers();

  protected abstract void onVirtualMachineLaunch(Map<String, Connector.Argument> arguments);

  protected abstract void onVirtualMachineEvent(Event event) throws Exception;

  protected abstract void onVirtualMachineSuspension(Location location, Map<String, Object> unpackedVariables);

  protected abstract void onVirtualMachineContinuation();

  protected abstract void onVirtualMachineTermination(String virtualMachineOut, String virtualMachineError);

  public void start() {
    new Thread(() -> {
      model.submitDebugClassSources(eventRequestManager);
      model.enableExceptionReporting(eventRequestManager, false);
      EventSet eventSet;
      try {
        while ((eventSet = virtualMachine.eventQueue().remove()) != null) {
          for (Event event : eventSet) {
            if (event instanceof LocatableEvent) {
              threadReference = ((LocatableEvent) event).thread();
            }
            if (event instanceof ClassPrepareEvent) {
              model.createDebugClassFor(eventRequestManager, (ClassPrepareEvent) event);
            } else if (event instanceof ExceptionEvent) {
              ExceptionEvent exceptionEvent = (ExceptionEvent) event;
              Object exception = deserializeReference(exceptionEvent.thread(), exceptionEvent.exception());
              view.reportException(exception.toString(), DebuggerExceptionType.VIRTUAL_MACHINE);
            }
            onVirtualMachineEvent(event);
          }
        }
      } catch (VMDisconnectedException e) {
        onVirtualMachineTermination(
                inputStreamToString(virtualMachine.process().getInputStream()),
                inputStreamToString(virtualMachine.process().getErrorStream())
        );
      } catch (Exception e) {
        e.printStackTrace();
      }
    }).start();
  }

  protected void activateStepRequest(int stepRequestDepth) {
    synchronized (stepRequestLock) {
      if (activeStepRequest == null || activeStepRequest.depth() != stepRequestDepth) {
        deleteActiveStepRequest();
        activeStepRequest = virtualMachine.eventRequestManager().createStepRequest(threadReference, StepRequest.STEP_LINE, stepRequestDepth);
        activeStepRequest.enable();
      }
    }
    resumeEventProcessing();
  }

  protected void deleteActiveStepRequest() {
    synchronized (stepRequestLock) {
      if (activeStepRequest != null) {
        activeStepRequest.disable();
        virtualMachine.eventRequestManager().deleteEventRequest(activeStepRequest);
        activeStepRequest = null;
      }
    }
  }

  protected void resume() {
    deleteActiveStepRequest();
    resumeEventProcessing();
  }

  protected void suspend(LocatableEvent event) throws AbsentInformationException, IncompatibleThreadStateException {
    ThreadReference thread = event.thread();
    Location location = event.location();
    Class<?> clazz = toClass(location);

    DebugClass debugClass = model.getDebugClassFor(clazz);
    if (debugClass == null) {
      return;
    }
    view.setSelectedLocation(new DebugClassLocation(debugClass, location.lineNumber()));

    onVirtualMachineSuspension(location, deserializeVariables(thread));

    synchronized (eventProcessingControl) {
      try {
        eventProcessingControl.wait();
      } catch (InterruptedException e) {
        System.exit(0);
      }
    }

    onVirtualMachineContinuation();
  }

  private void resumeEventProcessing() {
    synchronized (eventProcessingControl) {
      eventProcessingControl.notifyAll();
    }
  }

  public Class<?> toClass(Location location) {
    Class<?> result;
    String className = location.toString().split(":")[0];
    try {
      result = Class.forName(className);
    } catch (ClassNotFoundException e) {
      result = null;
    }
    return result;
  }

  public Deserializer getFor(ObjectReference object) {
    try {
      Class<?> clazz = Class.forName(object.referenceType().name());
      while (clazz != Object.class) {
        Deserializer existing = deserializers.get(clazz.getName());
        if (existing != null) {
          return existing;
        }
        clazz = clazz.getSuperclass();
      }
    } catch (ClassNotFoundException e) {
      return toString;
    }
    return toString;
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
    for (Map.Entry<LocalVariable, Value> entry : frame.getValues(frame.visibleVariables()).entrySet()) {
      deserializedVariables.put(entry.getKey().name(), deserializeReference(thread, entry.getValue()));
    }
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
      return getFor(ref).deserialize(ref, thread);
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