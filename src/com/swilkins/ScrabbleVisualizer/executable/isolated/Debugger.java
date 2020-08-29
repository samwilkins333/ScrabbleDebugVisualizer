package com.swilkins.ScrabbleVisualizer.executable.isolated;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.StepRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

public class Debugger {

  private static final Object lock = new Object();

  public static void main(String[] args) {
    new Thread(childThread(Target.class.getName())).start();

    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String line;
    try {
      while ((line = reader.readLine()) != null) {
        if (line.trim().equals("Continue")) {
          synchronized (lock) {
            lock.notifyAll();
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static Runnable childThread(String targetClassName) {
    return () -> {
      LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
      Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
      arguments.get("main").setValue(targetClassName);
      VirtualMachine virtualMachine;
      try {
        virtualMachine = launchingConnector.launch(arguments);

        ClassPrepareRequest classPrepareRequest = virtualMachine.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(targetClassName);
        classPrepareRequest.enable();

        EventSet eventSet;
        while ((eventSet = virtualMachine.eventQueue().remove()) != null) {
          for (Event event : eventSet) {
            if (event instanceof ClassPrepareEvent) {
              ReferenceType referenceType = ((ClassPrepareEvent) event).referenceType();
              for (int lineNumber : new int[]{6, 8}) {
                Location breakpointLocation = referenceType.locationsOfLine(lineNumber).get(0);
                virtualMachine.eventRequestManager().createBreakpointRequest(breakpointLocation).enable();
              }
            } else if (event instanceof LocatableEvent) {
              LocatableEvent locatableEvent = (LocatableEvent) event;
              System.out.println(locatableEvent + " " + locatableEvent.thread().isSuspended() + " " + locatableEvent.thread().frame(0).visibleVariables().size());
              if (event instanceof BreakpointEvent) {
                if (locatableEvent.location().lineNumber() == 6) {
                  StepRequest request = virtualMachine.eventRequestManager().createStepRequest(locatableEvent.thread(), StepRequest.STEP_LINE, StepRequest.STEP_OVER);
                  request.addClassFilter(targetClassName);
                  request.enable();
                }
              }
              synchronized (lock) {
                lock.wait();
              }
            }
          }
          virtualMachine.resume();
        }
      } catch (VMDisconnectedException e) {
        System.out.println("Virtual machine disconnected.");
        System.exit(0);
      } catch (Exception e) {
        e.printStackTrace();
      }
    };
  }

}
