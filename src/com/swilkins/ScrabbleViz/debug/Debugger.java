package com.swilkins.ScrabbleViz.debug;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;

import java.io.IOException;
import java.util.Map;

import static com.swilkins.ScrabbleViz.utility.Unpackers.unpackVariables;
import static com.swilkins.ScrabbleViz.utility.Utilities.toClass;

public abstract class Debugger {

  protected final VirtualMachine virtualMachine;

  protected final DebuggerView view;
  protected final DebuggerModel model;

  protected ThreadReference threadReference;
  protected StepRequest activeStepRequest;

  protected final Object eventProcessingControl = new Object();
  protected final Object stepRequestLock = new Object();

  public Debugger(Class<?> virtualMachineTargetClass) throws Exception {
    LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
    Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
    arguments.get("main").setValue(virtualMachineTargetClass.getName());
    this.onVirtualMachineLaunch(arguments);
    virtualMachine = launchingConnector.launch(arguments);

    this.view = new DebuggerView();
    this.configureView();
    this.model = new DebuggerModel();
    this.configureModel();
  }

  protected abstract void onVirtualMachineLaunch(Map<String, Connector.Argument> arguments);

  protected abstract void configureView();

  protected abstract void configureModel() throws IOException;

  protected abstract void onVirtualMachineSuspension(Location location, Map<String, Object> unpackedVariables);

  protected abstract void onVirtualMachineContinuation();

  protected abstract void onTermination();

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
            } else if (event instanceof BreakpointEvent) {
              deleteActiveStepRequest();
              suspend((LocatableEvent) event);
            } else if (event instanceof StepEvent) {
              Class<?> clazz = toClass(((StepEvent) event).location());
              if (clazz != null && model.getDebugClassFor(clazz) != null) {
                suspend((LocatableEvent) event);
              }
            }
            virtualMachine.resume();
          }
        }
      } catch (VMDisconnectedException e) {
        onTermination();
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

  protected void resumeEventProcessing() {
    synchronized (eventProcessingControl) {
      deleteActiveStepRequest();
      eventProcessingControl.notifyAll();
    }
  }

  private void awaitParentApproval() {
    synchronized (eventProcessingControl) {
      try {
        eventProcessingControl.wait();
      } catch (InterruptedException e) {
        System.exit(0);
      }
    }
  }

  private void deleteActiveStepRequest() {
    synchronized (stepRequestLock) {
      if (activeStepRequest != null) {
        activeStepRequest.disable();
        virtualMachine.eventRequestManager().deleteEventRequest(activeStepRequest);
        activeStepRequest = null;
      }
    }
  }

  private void suspend(LocatableEvent event) throws AbsentInformationException, IncompatibleThreadStateException {
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
    awaitParentApproval();
    onVirtualMachineContinuation();
  }

}
