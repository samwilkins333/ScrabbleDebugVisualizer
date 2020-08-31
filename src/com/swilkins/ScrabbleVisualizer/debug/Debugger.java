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
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

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

  protected final Map<Class<?>, Dereferencer> dereferencerMap = new HashMap<>();
  protected final Dereferencer toString = (object, thread) -> standardDereference(object, "toString", thread);

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

    configureDereferencers();
  }

  protected abstract void configureDebuggerModel() throws IOException, ClassNotFoundException;

  protected abstract void configureDebuggerView();

  protected abstract void configureDereferencers();

  protected abstract void configureVirtualMachineLaunch(Map<String, Connector.Argument> arguments);

  protected void onVirtualMachineLocatableEvent(LocatableEvent event, int eventSetSize) throws Exception {
    DebugClassLocation location = debuggerModel.toDebugClassLocation(event.location());
    if (location == null || (event instanceof BreakpointEvent && location.equals(debuggerSourceView.getProgrammaticSelectedLocation()))) {
        return;
    }
    ThreadReference thread = event.thread();
    debuggerSourceView.setSelectedLocation(location);
    onVirtualMachineSuspension(location, dereferenceVariables(thread));
    debuggerSourceView.setAllControlButtonsEnabled(true);
    debuggerModel.awaitEventProcessingContinuation();
    debuggerModel.respondToRequestedStepRequestDepth(thread);
    onVirtualMachineContinuation();
    debuggerSourceView.setAllControlButtonsEnabled(false);
  }

  protected void onVirtualMachineSuspension(DebugClassLocation location, Map<String, Object> dereferencedVariables) {
    if (debuggerWatchView != null) {
      debuggerWatchView.setEnabled(true);
      debuggerWatchView.updateFrom(location, dereferencedVariables);
    }
  }

  protected void onVirtualMachineContinuation() {
    if (debuggerWatchView != null) {
      debuggerWatchView.setEnabled(false);
      debuggerWatchView.clean();
    }
  }

  protected void onVirtualMachineTermination(String virtualMachineOut, String virtualMachineError) {
    if (virtualMachineOut != null && !virtualMachineOut.isEmpty()) {
      System.out.println(virtualMachineOut);
    }
    if (virtualMachineError != null && !virtualMachineError.isEmpty()) {
      System.out.println(virtualMachineError);
    }
  }

  protected void addOnSplitResizeListener(BiConsumer<Dimension, Integer> onSplitResizeListener) {
    onSplitResizeListeners.add(onSplitResizeListener);
  }

  private void start() {
    started = true;

    LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
    Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
    arguments.get("main").setValue(virtualMachineTargetClass.getName());

    configureVirtualMachineLaunch(arguments);

    new Thread(() -> {
      try {
        virtualMachine = launchingConnector.launch(arguments);
        debuggerModel.setEventRequestManager(virtualMachine.eventRequestManager());
        debuggerModel.submitDebugClassSources();
        debuggerModel.enableExceptionReporting(true, true);

        debuggerSourceView.start();

        EventSet eventSet;
        while ((eventSet = virtualMachine.eventQueue().remove()) != null) {
          for (Event event : eventSet) {
            if (event instanceof ClassPrepareEvent) {
              debuggerModel.createDebugClassFrom((ClassPrepareEvent) event);
            } else if (event instanceof ExceptionEvent) {
              ExceptionEvent exceptionEvent = (ExceptionEvent) event;
              Object exception = dereferenceValue(exceptionEvent.thread(), exceptionEvent.exception());
              debuggerSourceView.reportException(exception.toString(), DebuggerExceptionType.VIRTUAL_MACHINE);
            } else if (event instanceof LocatableEvent) {
              onVirtualMachineLocatableEvent((LocatableEvent) event, eventSet.size());
            }
          }
          virtualMachine.resume();
        }
      } catch (VMDisconnectedException e) {
        Process process = virtualMachine.process();
        String virtualMachineOut = inputStreamToString(process.getInputStream());
        String virtualMachineError = inputStreamToString(process.getErrorStream());
        onVirtualMachineTermination(virtualMachineOut, virtualMachineError);
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
      } catch (NoSuchMethodException e) {
        System.out.println(e.getMessage());
        System.exit(1);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }).start();
  }

  private Map<DebuggerControl, ActionListener> getDefaultControlActionListeners() {
    Map<DebuggerControl, ActionListener> defaultControlActionListeners = new LinkedHashMap<>();
    defaultControlActionListeners.put(RUN, e -> {
      debuggerSourceView.setControlButtonEnabled(RUN, false);
      if (!started) {
        start();
      } else {
        debuggerModel.setRequestedStepRequestDepth(null);
        debuggerModel.resumeEventProcessing();
      }
    });
    defaultControlActionListeners.put(STEP_OVER, e -> {
      debuggerModel.setRequestedStepRequestDepth(StepRequest.STEP_OVER);
      debuggerModel.resumeEventProcessing();
    });
    defaultControlActionListeners.put(STEP_INTO, e -> {
      debuggerModel.setRequestedStepRequestDepth(StepRequest.STEP_INTO);
      debuggerModel.resumeEventProcessing();
    });
    defaultControlActionListeners.put(STEP_OUT, e -> {
      debuggerModel.setRequestedStepRequestDepth(StepRequest.STEP_OUT);
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
    defaultControlActionListeners.put(RECENTER, e -> {
      DebugClassLocation location = debuggerSourceView.getProgrammaticSelectedLocation();
      debuggerSourceView.setSelectedLocation(location);
    });
    return defaultControlActionListeners;
  }

  private Dereferencer getDereferencerFor(ObjectReference objectReference) {
    Dereferencer dereferencer = toString;
    try {
      Class<?> clazz = Class.forName(objectReference.referenceType().name());
      while (clazz != null) {
        Dereferencer existing = dereferencerMap.get(clazz);
        if (existing != null) {
          dereferencer = existing;
          break;
        }
        clazz = clazz.getSuperclass();
      }
    } catch (ClassNotFoundException ignored) {
    }
    return dereferencer;
  }

  protected Value invoke(ObjectReference object, ThreadReference thread, String toInvokeName, String signature, List<? extends Value> arguments) throws NoSuchMethodException {
    ReferenceType referenceType = object.referenceType();
    List<Method> candidates;
    if (signature != null) {
      candidates = referenceType.methodsByName(toInvokeName, signature);
    } else {
      candidates = referenceType.methodsByName(toInvokeName);
    }
    Function<String, NoSuchMethodException> errorMessageBuilder = s -> new NoSuchMethodException(String.format(
            "Illegal method invocation in dereferencer: [%s (%s) invoked on %s]: %s.",
            toInvokeName, signature, referenceType.name(), s
    ));
    if (candidates.isEmpty()) {
      throw errorMessageBuilder.apply("Method does not exist");
    }
    try {
      Method toInvoke = candidates.get(0);
      if (arguments == null) {
        arguments = Collections.emptyList();
      }
      return object.invokeMethod(thread, toInvoke, arguments, 0);
    } catch (Exception e) {
      throw errorMessageBuilder.apply(e.getMessage());
    }
  }

  private Map<String, Object> dereferenceVariables(ThreadReference thread)
          throws Exception {
    StackFrame frame = thread.frame(0);
    Map<String, Object> dereferencedVariables = new HashMap<>();
    Map<LocalVariable, Value> values = frame.getValues(frame.visibleVariables());
    debuggerModel.deadlockSafeInvoke(() -> {
      for (Map.Entry<LocalVariable, Value> entry : values.entrySet()) {
        dereferencedVariables.put(entry.getKey().name(), dereferenceValue(thread, entry.getValue()));
      }
    });
    return dereferencedVariables;
  }

  protected Object standardDereference(ObjectReference value, String toInvokeName, ThreadReference thread) throws NoSuchMethodException {
    return dereferenceValue(thread, invoke(value, thread, toInvokeName, null, null));
  }

  protected Object dereferenceValue(ThreadReference thread, Value value) throws NoSuchMethodException {
    if (value instanceof ObjectReference) {
      ((ObjectReference) value).disableCollection();
    }
    if (value instanceof ArrayReference) {
      ArrayReference arrayReference = (ArrayReference) value;
      int length = arrayReference.length();
      Object[] collector = new Object[length];
      for (int i = 0; i < length; i++) {
        collector[i] = dereferenceValue(thread, arrayReference.getValue(i));
      }
      return collector;
    } else if (value instanceof StringReference) {
      return ((StringReference) value).value();
    } else if (value instanceof ObjectReference) {
      ObjectReference objectReference = (ObjectReference) value;
      return getDereferencerFor(objectReference).dereference(objectReference, thread);
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