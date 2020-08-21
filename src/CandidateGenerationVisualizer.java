import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.ExceptionRequest;
import com.sun.jdi.request.StepRequest;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class CandidateGenerationVisualizer<T> {
  private static SourceCodeView SOURCE_CODE_VIEW;

  static class SourceCodeView extends JTextArea {
    public SourceCodeView(InputStream sourceCodeStream) {
      super(getSourceAsString(sourceCodeStream));
      setOpaque(false);
      setRequestFocusEnabled(false);
      setFocusable(false);
      setEditable(false);
      setHighlighter(null);
      getCaret().addChangeListener(e -> System.out.println("Caret updated."));
    }
    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
      try {
        Rectangle2D rect = modelToView2D(getCaretPosition());
        if (rect != null) {
          g.setColor(Color.MAGENTA);
          g.fillRect(0, (int)rect.getY(), getWidth(), (int)rect.getHeight());
        }
      } catch (BadLocationException e) {
        e.printStackTrace();
      }
      super.paintComponent(g);
    }

    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
      super.repaint(tm, 0, 0, getWidth(), getHeight());
    }

    public void gotoStartOfLine(int line)
    {
      Element root = getDocument().getDefaultRootElement();
      line = Math.max(line, 1);
      line = Math.min(line, root.getElementCount());
      int startOfLineOffset = root.getElement(line - 1).getStartOffset();
      setCaretPosition(startOfLineOffset);
      centerLineInScrollPane(this);
    }

  }

  private Class<T> debugClass;
  private Map<Integer, Boolean> breakPointLines;

  public void setDebugClass(Class<T> debugClass) {
    this.debugClass = debugClass;
  }

  public void setBreakPointLines(Map<Integer, Boolean> breakPointLines) {
    this.breakPointLines = breakPointLines;
  }

  public VirtualMachine connectAndLaunchVM() throws Exception {
    LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
    Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
    arguments.get("main").setValue(debugClass.getName());
    arguments.get("options").setValue("-cp \".:../lib/scrabble-base-jar-with-dependencies.jar\"");
    return launchingConnector.launch(arguments);
  }

  private static final Object LOCK = new Object();

  public static void main(String[] args) throws IOException {
    JFrame frame = new JFrame("Candidate Generation Visualizer");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
    frame.setSize(dimension.width,dimension.height);
    JButton button = new JButton("Continue");
    button.addActionListener(e -> {
      synchronized (LOCK) {
        LOCK.notifyAll();
      }
    });
    button.setAlignmentX(Component.CENTER_ALIGNMENT);
    File file = new File("../lib/scrabble-base-jar-with-dependencies.jar");
    JarFile jarFile = new JarFile(file);
    JarEntry generator = jarFile.getJarEntry("com/swilkins/ScrabbleBase/Generation/Generator.java");
    SOURCE_CODE_VIEW = new SourceCodeView(jarFile.getInputStream(generator));
    SOURCE_CODE_VIEW.gotoStartOfLine(15);
    JScrollPane scrollPane = new JScrollPane(SOURCE_CODE_VIEW);
    scrollPane.setAutoscrolls(true);
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(button);
    panel.add(scrollPane);
    frame.getContentPane().add(panel);
    frame.setVisible(true);

    new Thread(getDebuggerAsChild()).start();
  }

  public static void centerLineInScrollPane(JTextComponent component)
  {
    Container container = SwingUtilities.getAncestorOfClass(JViewport.class, component);

    if (container == null) {
      System.out.println("GAAAHHHHHH");
      return;
    }

    try {
      Rectangle2D r = component.modelToView2D(component.getCaretPosition());
      JViewport viewport = (JViewport)container;
      int extentHeight = viewport.getExtentSize().height;
      int viewHeight = viewport.getViewSize().height;

      int y = Math.max(0, (int)r.getY() - ((extentHeight - (int)r.getHeight()) / 2));
      y = Math.min(y, viewHeight - extentHeight);

      viewport.setViewPosition(new Point(0, y));
    }  catch (BadLocationException e) {
      e.printStackTrace();
    }
  }

  public static String getSourceAsString(InputStream inputStream) {
    String generatorSourceString;
    try {

      final int bufferSize = 1024;
      final char[] buffer = new char[bufferSize];
      final StringBuilder out = new StringBuilder();
      Reader in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
      int charsRead;
      while((charsRead = in.read(buffer, 0, buffer.length)) > 0) {
        out.append(buffer, 0, charsRead);
      }
      generatorSourceString = out.toString();
    } catch (IOException e) {
      generatorSourceString = null;
      e.printStackTrace();
    }
    return generatorSourceString;
  }

  public static Runnable getDebuggerAsChild() {
    return () -> {
      CandidateGenerationVisualizer<JDIExampleDebuggee> debuggerInstance = new CandidateGenerationVisualizer<>();
      debuggerInstance.setDebugClass(JDIExampleDebuggee.class);
      Map<Integer, Boolean> breakPointLines = new HashMap<>();
      breakPointLines.put(12, false);
      breakPointLines.put(16, true);
      breakPointLines.put(21, false);
      debuggerInstance.setBreakPointLines(breakPointLines);
      VirtualMachine vm;
      try {
        vm = debuggerInstance.connectAndLaunchVM();
        debuggerInstance.enableClassPrepareRequest(vm);
        EventSet eventSet;
        StepRequest activeStepRequest = null;
        int stepCounter = 0;
        while ((eventSet = vm.eventQueue().remove()) != null) {
          for (Event event : eventSet) {
            if (event instanceof ClassPrepareEvent) {
              debuggerInstance.setBreakPoints(vm, (ClassPrepareEvent)event);
            }
            if (event instanceof ExceptionEvent) {
//              VARIABLES_DISPLAY.setText("EXCEPTION\n" + unpackReference(((ExceptionEvent) event).thread(), ((ExceptionEvent) event).exception()) + "\n\n");
            }
            if (event instanceof BreakpointEvent) {
              displayUnpackedVariables("BREAKPOINT", debuggerInstance, ((BreakpointEvent) event).thread());
              StepRequest candidate = debuggerInstance.enableStepRequest(vm, (BreakpointEvent)event);
              if (candidate != null) {
                activeStepRequest = candidate;
                stepCounter = 0;
              }
            }
            if (event instanceof StepEvent) {
              Location location = ((StepEvent) event).location();
              if (location.sourceName().split("\\.")[0].equals(debuggerInstance.debugClass.getName())) {
                displayUnpackedVariables("STEP", debuggerInstance, ((StepEvent) event).thread());
                if (++stepCounter == 1 && activeStepRequest != null) {
                  activeStepRequest.disable();
                }
              }
            }
            vm.resume();
          }
        }
      } catch (VMDisconnectedException e) {
        if (!SOURCE_CODE_VIEW.getText().startsWith("EXCEPTION")) {
//          VARIABLES_DISPLAY.setText("VM cleanly disconnected.");
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    };
  }

  private static void displayUnpackedVariables(
          String prompt,
          CandidateGenerationVisualizer<JDIExampleDebuggee> debuggerInstance,
          ThreadReference thread
  ) throws AbsentInformationException, IncompatibleThreadStateException {
    System.out.println(prompt);
    StringBuilder displayText = new StringBuilder(prompt).append("\n");
    StackFrame frame = thread.frame(0);
    displayText.append(frame.location().toString()).append("\n\n");
    Map<String, Object> unpackedVariables = debuggerInstance.unpackVariables(frame, thread);
    if (unpackedVariables != null) {
      for (Map.Entry<String, Object> variable : unpackedVariables.entrySet()) {
        String resolved;
        if (variable.getValue() instanceof Object[]) {
          resolved = Arrays.deepToString((Object[]) variable.getValue());
        } else {
          resolved = variable.getValue().toString();
        }
        displayText.append(variable.getKey()).append(" = ").append(resolved).append("\n");
      }
//      VARIABLES_DISPLAY.setText(displayText.toString());
    }
    synchronized (LOCK) {
      try {
        LOCK.wait();
      } catch(InterruptedException e) {
        System.exit(0);
      }
//      VARIABLES_DISPLAY.setText("Running...");
    }
  }

  public void enableClassPrepareRequest(VirtualMachine vm) {
    ExceptionRequest exceptionRequest = vm.eventRequestManager().createExceptionRequest(null, true, true);
    exceptionRequest.enable();
    ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
    classPrepareRequest.addClassFilter(debugClass.getName());
    classPrepareRequest.enable();
  }

  public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event) throws AbsentInformationException {
    ClassType classType = (ClassType) event.referenceType();
    for (int line : breakPointLines.keySet()) {
      Location location = classType.locationsOfLine(line).get(0);
      BreakpointRequest breakpointRequest = vm.eventRequestManager().createBreakpointRequest(location);
      breakpointRequest.enable();
    }
  }

  public StepRequest enableStepRequest(VirtualMachine vm, BreakpointEvent event) throws AbsentInformationException {
    Location location = event.location();
    int lineNumber = location.lineNumber();
    if (breakPointLines.get(lineNumber) && location.sourceName().split("\\.")[0].equals(debugClass.getName())) {
      StepRequest request = vm.eventRequestManager().createStepRequest(event.thread(), StepRequest.STEP_LINE, StepRequest.STEP_OVER);
      request.enable();
      return request;
    }
    return null;
  }

  public Map<String, Object> unpackVariables(StackFrame frame, ThreadReference thread) throws AbsentInformationException {
    if (frame.location().toString().contains(debugClass.getName())) {
      Map<String, Object> unpackedVariables = new HashMap<>();
      for (Map.Entry<LocalVariable, Value> entry : frame.getValues(frame.visibleVariables()).entrySet()) {
        unpackedVariables.put(entry.getKey().name(), unpackReference(thread, entry.getValue()));
      }
      return unpackedVariables;
    }
    return null;
  }

  private static Object unpackReference(ThreadReference thread, Value value) {
      if (value instanceof ArrayReference) {
        ArrayReference arrayReference = (ArrayReference)value;
        Object[] collector = new Object[arrayReference.length()];
        for (int i = 0; i < arrayReference.length(); i++) {
          collector[i] = (unpackReference(thread, arrayReference.getValue(i)));
        }
        return collector;
      } else if (value instanceof StringReference) {
        return ((StringReference) value).value();
      } else if (value instanceof ObjectReference) {
        ObjectReference ref = (ObjectReference) value;
        Method toString = ref.referenceType()
                .methodsByName("toString", "()Ljava/lang/String;").get(0);
        try {
          Value returned = ref.invokeMethod(thread, toString, Collections.emptyList(), 0);
          if (returned instanceof StringReference) {
            return ((StringReference) returned).value();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      } else if (value instanceof PrimitiveValue) {
        PrimitiveValue primitiveValue = (PrimitiveValue)value;
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
