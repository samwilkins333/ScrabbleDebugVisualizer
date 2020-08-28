package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.StepRequest;
import com.swilkins.ScrabbleVisualizer.view.DebuggerWatchView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;

import static com.swilkins.ScrabbleVisualizer.debug.DebuggerControl.*;
import static com.swilkins.ScrabbleVisualizer.utility.Utilities.inputStreamToString;

public abstract class Debugger extends JFrame {

  protected VirtualMachine virtualMachine;

  private static final Dimension screenDimension;

  static {
    Dimension resolution = Toolkit.getDefaultToolkit().getScreenSize();
    screenDimension = new Dimension(resolution.width, resolution.height - 60);
  }

  protected final DebuggerSourceView debuggerSourceView;
  protected DebuggerWatchView debuggerWatchView = null;
  protected final DebuggerModel debuggerModel;
  private final Class<?> virtualMachineTargetClass;
  private final Set<BiConsumer<Dimension, Integer>> onSplitResizeListeners = new HashSet<>();

  protected final Map<String, Deserializer> deserializers = new HashMap<>();
  private final Deserializer toString = (object, thread) ->
          deserializeReference(thread, invoke(object, thread, "toString", "()Ljava/lang/String;"));

  private boolean started;

  public Debugger(Class<?> virtualMachineTargetClass, DebuggerWatchView debuggerWatchView) throws Exception {
    super(virtualMachineTargetClass.getSimpleName());
    this.virtualMachineTargetClass = virtualMachineTargetClass;

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(screenDimension.width, screenDimension.height);
    setResizable(false);

    debuggerModel = new DebuggerModel();
    configureDebuggerModel();

    Dimension verticalScreenHalf = new Dimension(screenDimension.width, screenDimension.height / 2);

    debuggerSourceView = new DebuggerSourceView();
    debuggerSourceView.setDefaultControlActionListeners(getDefaultControlActionListeners());
    debuggerSourceView.setPreferredSize(verticalScreenHalf);
    configureDebuggerView();

    if (debuggerWatchView != null) {
      this.debuggerWatchView = debuggerWatchView;
      this.debuggerWatchView.setPreferredSize(verticalScreenHalf);
      this.debuggerWatchView.initialize(verticalScreenHalf);
      addOnSplitResizeListener(this.debuggerWatchView.onSplitResize());

      JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, debuggerSourceView, debuggerWatchView);
      splitPane.setDividerLocation(verticalScreenHalf.height);
      splitPane.addPropertyChangeListener(e -> {
        if (e.getPropertyName().equals("dividerLocation")) {
          int location = (int) e.getNewValue();
          onSplitResizeListeners.forEach(listener -> listener.accept(screenDimension, location));
        }
      });

      getContentPane().add(splitPane);
    } else {
      getContentPane().add(debuggerSourceView);
    }


    configureDeserializers();
  }

  protected abstract void configureDebuggerModel() throws IOException, ClassNotFoundException;

  protected abstract void configureDebuggerView();

  protected abstract void configureDeserializers();

  protected abstract void configureVirtualMachineLaunch(Map<String, Connector.Argument> arguments);

  protected void onVirtualMachineEvent(Event event) throws Exception {
    if (event instanceof LocatableEvent) {
      LocatableEvent locatableEvent = (LocatableEvent) event;
      DebugClassLocation location = debuggerModel.toDebugClassLocation(locatableEvent.location());
      ThreadReference thread = locatableEvent.thread();
      if (location != null) {
        trySuspend(location, thread);
      }
    }
    virtualMachine.resume();
  }

  protected abstract void onVirtualMachineTermination(String virtualMachineOut, String virtualMachineError);

  protected void onVirtualMachineSuspension(DebugClassLocation location, Map<String, Object> deserializedVariables) {
    if (debuggerWatchView != null) {
      debuggerWatchView.setEnabled(true);
      debuggerWatchView.updateFrom(location, deserializedVariables);
    }
  }

  protected void onVirtualMachineContinuation() {
    if (debuggerWatchView != null) {
      debuggerWatchView.setEnabled(false);
      debuggerWatchView.clean();
    }
  }

  protected void addOnSplitResizeListener(BiConsumer<Dimension, Integer> onSplitResizeListener) {
    onSplitResizeListeners.add(onSplitResizeListener);
  }

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
          debuggerModel.setEventRequestManager(virtualMachine.eventRequestManager());
          debuggerModel.submitDebugClassSources();
          debuggerModel.enableExceptionReporting(true, true);

          while ((eventSet = virtualMachine.eventQueue().remove()) != null) {
            for (Event event : eventSet) {
              if (event instanceof LocatableEvent) {
                debuggerModel.setThreadReference(((LocatableEvent) event).thread());
              }
              if (event instanceof ClassPrepareEvent) {
                debuggerModel.createDebugClassFrom((ClassPrepareEvent) event);
              } else if (event instanceof ExceptionEvent) {
                ExceptionEvent exceptionEvent = (ExceptionEvent) event;
                Object exception = deserializeReference(exceptionEvent.thread(), exceptionEvent.exception());
                debuggerSourceView.reportException(exception.toString(), DebuggerExceptionType.VIRTUAL_MACHINE);
              }
              onVirtualMachineEvent(event);
            }
          }
        } catch (VMDisconnectedException e) {
          Process process = virtualMachine.process();
          String virtualMachineOut = inputStreamToString(process.getInputStream());
          String virtualMachineError = inputStreamToString(process.getErrorStream());
          onVirtualMachineTermination(virtualMachineOut, virtualMachineError);
          dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }).start();
    }
  }

  private Map<DebuggerControl, ActionListener> getDefaultControlActionListeners() {
    Map<DebuggerControl, ActionListener> defaultControlActionListeners = new LinkedHashMap<>();
    defaultControlActionListeners.put(RUN, e -> {
      debuggerSourceView.setControlButtonEnabled(RUN, false);
      if (!started) {
        start();
      } else {
        debuggerModel.disableActiveStepRequest();
        debuggerModel.resumeEventProcessing();
      }
    });
    defaultControlActionListeners.put(STEP_OVER, e -> {
      debuggerModel.setActiveStepRequestDepth(StepRequest.STEP_OVER);
      debuggerModel.resumeEventProcessing();
    });
    defaultControlActionListeners.put(STEP_INTO, e -> {
      debuggerModel.setActiveStepRequestDepth(StepRequest.STEP_INTO);
      debuggerModel.resumeEventProcessing();
    });
    defaultControlActionListeners.put(STEP_OUT, e -> {
      debuggerModel.setActiveStepRequestDepth(StepRequest.STEP_OUT);
      debuggerModel.resumeEventProcessing();
    });
    defaultControlActionListeners.put(TOGGLE_BREAKPOINT, e -> {
      try {
        DebugClassLocation selectedLocation = debuggerSourceView.getSelectedLocation();
        BreakpointRequest breakpointRequest = debuggerModel.getBreakpointRequestAt(selectedLocation);
        if (breakpointRequest == null) {
          debuggerModel.createBreakpointRequest(selectedLocation);
        } else {
          debuggerModel.setEventRequestEnabled(breakpointRequest, !breakpointRequest.isEnabled());
        }
        debuggerSourceView.repaint();
      } catch (AbsentInformationException ex) {
        debuggerSourceView.reportException(ex.toString(), DebuggerExceptionType.DEBUGGER);
      }
    });
    return defaultControlActionListeners;
  }

  protected void trySuspend(DebugClassLocation location, ThreadReference thread) throws AbsentInformationException, IncompatibleThreadStateException {
    DebugClassLocation previousLocation = debuggerSourceView.setSelectedLocation(location);

    Integer activeStepRequestDepth = debuggerModel.getActiveStepRequestDepth();
    if (activeStepRequestDepth != null && location.equals(previousLocation)) {
      if (activeStepRequestDepth == StepRequest.STEP_INTO || activeStepRequestDepth == StepRequest.STEP_OUT) {
        return;
      }
    }

    onVirtualMachineSuspension(location, deserializeVariables(thread));
    debuggerSourceView.setAllControlButtonsEnabled(true);
    debuggerModel.awaitEventProcessingContinuation();
    debuggerSourceView.setAllControlButtonsEnabled(false);
    onVirtualMachineContinuation();
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
    debuggerModel.overrideAllEventRequests();
    for (Map.Entry<LocalVariable, Value> entry : values.entrySet()) {
      deserializedVariables.put(entry.getKey().name(), deserializeReference(thread, entry.getValue()));
    }
    debuggerModel.restoreAllEventRequests();
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