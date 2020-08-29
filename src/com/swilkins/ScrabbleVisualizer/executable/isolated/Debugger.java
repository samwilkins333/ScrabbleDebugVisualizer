package com.swilkins.ScrabbleVisualizer.executable.isolated;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.StepRequest;
import com.swilkins.ScrabbleVisualizer.executable.GeneratorTarget;
import com.swilkins.ScrabbleVisualizer.utility.Utilities;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class Debugger {

  private static final Object lock = new Object();
  private static Integer stepRequestDepth = null;
  private static StepRequest activeStepRequest = null;

  private static void resume() {
    synchronized (lock) {
      lock.notifyAll();
    }
  }

  public static void main(String[] args) {
    EventQueue.invokeLater(() -> {
      JFrame frame = new JFrame();
      JPanel jPanel = new JPanel();
      jPanel.setLayout(new BoxLayout(jPanel, BoxLayout.X_AXIS));
      frame.getContentPane().add(jPanel);

      JButton controlButton;

      controlButton = new JButton("Continue");
      controlButton.addActionListener(e -> {
        stepRequestDepth = null;
        resume();
      });
      jPanel.add(controlButton);

      controlButton = new JButton("Step Over");
      controlButton.addActionListener(e -> {
        stepRequestDepth = StepRequest.STEP_OVER;
        resume();
      });
      jPanel.add(controlButton);

      controlButton = new JButton("Step Into");
      controlButton.addActionListener(e -> {
        stepRequestDepth = StepRequest.STEP_INTO;
        resume();
      });
      jPanel.add(controlButton);

      frame.setPreferredSize(new Dimension(300, 300));
      frame.setSize(new Dimension(300, 300));
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setResizable(true);
      frame.setVisible(true);
    });

    new Thread(childThread(GeneratorTarget.class.getName())).start();
  }

  private static Runnable childThread(String targetClassName) {
    return () -> {
      LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
      Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
      arguments.get("main").setValue(targetClassName);
      arguments.get("options").setValue("-cp \".:../lib/scrabble-base-jar-with-dependencies.jar\"");
      VirtualMachine virtualMachine = null;
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
              for (int lineNumber : new int[]{15, 17}) {
                List<Location> candidateLocations = referenceType.locationsOfLine(lineNumber);
                if (candidateLocations.isEmpty()) {
                  System.out.printf("Class has no mapping for line %d. Skipping.\n", lineNumber);
                  continue;
                }
                virtualMachine.eventRequestManager().createBreakpointRequest(candidateLocations.get(0)).enable();
              }
            } else if (event instanceof LocatableEvent) {
              LocatableEvent locatableEvent = (LocatableEvent) event;
              System.out.println(locatableEvent + " " + locatableEvent.thread().isSuspended() + " " + locatableEvent.thread().frame(0).visibleVariables().size());
              synchronized (lock) {
                lock.wait();
                if (stepRequestDepth != null) {
                  if (activeStepRequest == null || activeStepRequest.depth() != stepRequestDepth) {
                    if (activeStepRequest != null) {
                      activeStepRequest.disable();
                    }
                    activeStepRequest = virtualMachine.eventRequestManager().createStepRequest(locatableEvent.thread(), StepRequest.STEP_LINE, stepRequestDepth);
                    activeStepRequest.addClassFilter(targetClassName);
                    activeStepRequest.enable();
                  }
                } else if (activeStepRequest != null) {
                  activeStepRequest.disable();
                  activeStepRequest = null;
                }
              }
            }
          }
          virtualMachine.resume();
        }
      } catch (VMDisconnectedException e) {
        System.out.println("Virtual machine disconnected.");
        if (virtualMachine != null) {
          System.out.println(Utilities.inputStreamToString(virtualMachine.process().getInputStream()));
        }
        System.exit(0);
      } catch (Exception e) {
        e.printStackTrace();
      }
    };
  }

}
