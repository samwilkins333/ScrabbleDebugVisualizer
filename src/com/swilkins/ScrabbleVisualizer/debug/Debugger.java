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

  protected ThreadReference threadReference;
  private final Map<Integer, StepRequest> stepRequestMap = new HashMap<>(3);
  protected Integer activeStepRequestDepth;

  protected final Object eventProcessingControl = new Object();
  protected final Object stepRequestControl = new Object();
  protected final Object threadReferenceControl = new Object();

  protected final Map<String, Deserializer> deserializers = new HashMap<>();
  private final Deserializer toString = (object, thread) ->
          deserializeReference(thread, invoke(object, thread, "toString", "()Ljava/lang/String;"));

  private boolean started;

  public Debugger(Class<?> virtualMachineTargetClass) throws Exception {
    this.virtualMachineTargetClass = virtualMachineTargetClass;

    model = new DebuggerModel();
    configureModel();

    view = new DebuggerView();
    Map<DebuggerControl, ActionListener> defaultActionListeners = new LinkedHashMap<>();
    defaultActionListeners.put(RUN, e -> {
      if (!started) {
        start();
      } else {
        resume();
      }
    });
    defaultActionListeners.put(STEP_OVER, e -> activateStepRequest(StepRequest.STEP_OVER));
    defaultActionListeners.put(STEP_INTO, e -> activateStepRequest(StepRequest.STEP_INTO));
    defaultActionListeners.put(STEP_OUT, e -> activateStepRequest(StepRequest.STEP_OUT));
    defaultActionListeners.put(TOGGLE_BREAKPOINT, e -> {
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
    view.setDefaultActionListeners(defaultActionListeners);
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
                synchronized (threadReferenceControl) {
                  threadReference = ((LocatableEvent) event).thread();
                }
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

  protected void activateStepRequest(int stepRequestDepth) {
    synchronized (stepRequestControl) {
      if (activeStepRequestDepth == null || activeStepRequestDepth != stepRequestDepth) {
        disableActiveStepRequest();
        StepRequest requestedStepRequest = stepRequestMap.get(stepRequestDepth);
        if (requestedStepRequest == null) {
          synchronized (threadReferenceControl) {
            requestedStepRequest = model.createStepRequest(threadReference, stepRequestDepth);
          }
          stepRequestMap.put(stepRequestDepth, requestedStepRequest);
        }
        model.setEventRequestEnabled(requestedStepRequest, true);
        activeStepRequestDepth = stepRequestDepth;
      }
    }
    resumeEventProcessing();
  }

  protected void disableActiveStepRequest() {
    synchronized (stepRequestControl) {
      if (activeStepRequestDepth != null) {
        StepRequest activeStepRequest = stepRequestMap.get(activeStepRequestDepth);
        if (activeStepRequest != null) {
          model.setEventRequestEnabled(activeStepRequest, false);
        }
        activeStepRequestDepth = null;
      }
    }
  }

  protected void resume() {
    disableActiveStepRequest();
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

    DebugClassLocation updatedLocation = new DebugClassLocation(debugClass, location.lineNumber());
    DebugClassLocation previousLocation = view.setSelectedLocation(updatedLocation);

    if (updatedLocation.equals(previousLocation) && activeStepRequestDepth != null && StepRequest.STEP_INTO == activeStepRequestDepth) {
      virtualMachine.resume();
      return;
    }

    view.setAllControlButtonsEnabled(true);
    onVirtualMachineSuspension(location, deserializeVariables(thread));
    awaitEventProcessingContinuation();
    onVirtualMachineContinuation();
    view.setAllControlButtonsEnabled(false);
  }

  protected void awaitEventProcessingContinuation() {
    synchronized (eventProcessingControl) {
      try {
        eventProcessingControl.wait();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private void resumeEventProcessing() {
    synchronized (eventProcessingControl) {
      eventProcessingControl.notifyAll();
    }
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