package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;

import java.io.IOException;
import java.util.Map;

import static com.swilkins.ScrabbleVisualizer.utility.Unpackers.unpackVariables;

public abstract class Debugger {

  protected final VirtualMachine virtualMachine;

  protected final DebuggerView view;
  protected final DebuggerModel model;
  protected final Object eventProcessingControl = new Object();
  protected final Object stepRequestLock = new Object();
  protected ThreadReference threadReference;
  protected StepRequest activeStepRequest;

  public Debugger(Class<?> virtualMachineTargetClass) throws Exception {
    view = new DebuggerView();
    configureView();
    model = new DebuggerModel();
    configureModel();

    LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
    Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
    arguments.get("main").setValue(virtualMachineTargetClass.getName());
    onVirtualMachineLaunch(arguments);
    virtualMachine = launchingConnector.launch(arguments);
  }

  protected abstract void configureView();

  protected abstract void configureModel() throws IOException;

  protected abstract void onVirtualMachineLaunch(Map<String, Connector.Argument> arguments);

  protected abstract void onVirtualMachineEvent(Event event) throws Exception;

  protected abstract void onVirtualMachineSuspension(Location location, Map<String, Object> unpackedVariables);

  protected abstract void onVirtualMachineContinuation();

  protected abstract void onVirtualMachineTermination();

  public void start() {
    new Thread(() -> {
      EventSet eventSet;
      try {
        EventRequestManager eventRequestManager = virtualMachine.eventRequestManager();
        model.submitDebugClassSources(eventRequestManager);
        model.enableExceptionReporting(eventRequestManager, false);
        while ((eventSet = virtualMachine.eventQueue().remove()) != null) {
          for (Event event : eventSet) {
            if (event instanceof LocatableEvent) {
              threadReference = ((LocatableEvent) event).thread();
            }
            if (event instanceof ClassPrepareEvent) {
              model.createDebugClassFor(eventRequestManager, (ClassPrepareEvent) event);
            } else if (event instanceof ExceptionEvent) {
              view.reportVirtualMachineException((ExceptionEvent) event);
            }
            onVirtualMachineEvent(event);
          }
        }
      } catch (VMDisconnectedException e) {
        onVirtualMachineTermination();
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
    DebugClassLocation selectedLocation = new DebugClassLocation(debugClass, location.lineNumber());
    view.setSelectedLocation(selectedLocation);

    onVirtualMachineSuspension(location, unpackVariables(thread));

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

}
