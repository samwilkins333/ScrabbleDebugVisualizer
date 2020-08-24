package com.swilkins.ScrabbleViz.debugClass;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ClassType;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.swilkins.ScrabbleViz.view.LineNumberViewer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;

import static com.swilkins.ScrabbleViz.utility.Unpackers.unpackReference;

public class DebugClassViewer extends JPanel {
  private final Map<Class<?>, DebugClass> debugClasses = new HashMap<>();
  private final Map<Class<?>, DebugClassSource> debugClassSources = new HashMap<>();
  private final DebugClassTextView debugClassTextView;
  private final JLabel locationLabel = new JLabel(" ");
  private DebugClassLocation selectedLocation;

  public DebugClassViewer(DebugClassViewerOptions options) {
    super();
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    debugClassTextView = new DebugClassTextView(options);
    debugClassTextView.addCaretListener(e -> {
      String locationLabelText;
      if (selectedLocation == null) {
        locationLabelText = "?: ?";
      } else {
        String selectedClassName = selectedLocation.getDebugClass().getClazz().getName();
        int selectedLineNumber = getDebugClassTextView().getSelectedLineNumber();
        locationLabelText = String.format("%s: %d", selectedClassName, selectedLineNumber);
      }
      locationLabel.setText(locationLabelText);
    });

    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
    header.add(locationLabel);
    locationLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
    add(header);

    JScrollPane scrollWrapper = new JScrollPane(debugClassTextView);
    scrollWrapper.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    scrollWrapper.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollWrapper.setWheelScrollingEnabled(false);
    scrollWrapper.setRowHeaderView(debugClassTextView.lineNumberViewer);
    add(scrollWrapper);
  }

  public DebugClassLocation getSelectedLocation() {
    return selectedLocation;
  }

  public void setSelectedLocation(DebugClassLocation updatedLocation) {
    DebugClass updatedDebugClass = updatedLocation.getDebugClass();
    if (selectedLocation == null || updatedDebugClass != selectedLocation.getDebugClass()) {
      if (!debugClasses.containsValue(updatedDebugClass)) {
        throw new IllegalArgumentException("No DebugClass exists for " +
                updatedDebugClass.getClazz().getName());
      }
      debugClassTextView.setText(updatedDebugClass.getContentsAsString());
      debugClassTextView.repaintWith(updatedDebugClass);
    }

    int updatedLineNumber = updatedLocation.getLineNumber();
    Element root = debugClassTextView.getDocument().getDefaultRootElement();
    updatedLineNumber = Math.max(updatedLineNumber, 1);
    updatedLineNumber = Math.min(updatedLineNumber, root.getElementCount());
    if (updatedLineNumber != updatedLocation.getLineNumber()) {
      updatedLocation = new DebugClassLocation(updatedDebugClass, updatedLineNumber);
    }

    this.selectedLocation = updatedLocation;

    int startOfLineOffset = root.getElement(updatedLineNumber - 1).getStartOffset();
    debugClassTextView.setCaretPosition(startOfLineOffset);

    Container container = SwingUtilities.getAncestorOfClass(JViewport.class, debugClassTextView);

    if (container != null) {
      try {
        Rectangle2D r = debugClassTextView.modelToView2D(debugClassTextView.getCaretPosition());
        JViewport viewport = (JViewport) container;
        int extentHeight = viewport.getExtentSize().height;
        int viewHeight = viewport.getViewSize().height;

        int y = Math.max(0, (int) r.getY() - ((extentHeight - (int) r.getHeight()) / 2));
        y = Math.min(y, viewHeight - extentHeight);

        viewport.setViewPosition(new Point(0, y));
      } catch (BadLocationException e) {
        e.printStackTrace();
      }
    }
  }

  public void toggleBreakpointAt(DebugClassLocation debugClassLocation) throws AbsentInformationException {
    DebugClass debugClass = debugClassLocation.getDebugClass();
    int lineNumber = debugClassLocation.getLineNumber();
    if (debugClass.hasBreakpointAt(lineNumber)) {
      debugClass.removeBreakpointAt(lineNumber);
    } else {
      debugClass.requestBreakpointAt(lineNumber);
    }
    debugClassTextView.repaintWith(debugClass);
  }

  public DebugClassTextView getDebugClassTextView() {
    return debugClassTextView;
  }

  public void addDebugClassSource(Class<?> clazz, DebugClassSource debugClassSource) {
    debugClassSources.put(clazz, debugClassSource);
  }

  public void submitDebugClassSources(EventRequestManager eventRequestManager) {
    for (Class<?> clazz : debugClassSources.keySet()) {
      ClassPrepareRequest request = eventRequestManager.createClassPrepareRequest();
      request.addClassFilter(clazz.getName());
      request.enable();
    }
  }

  public void enableExceptionReporting(EventRequestManager eventRequestManager, boolean notifyCaught) {
    eventRequestManager.createExceptionRequest(null, notifyCaught, true).enable();
  }

  public void createDebugClassFor(EventRequestManager eventRequestManager, ClassPrepareEvent event) throws ClassNotFoundException, AbsentInformationException {
    ClassType classType = (ClassType) event.referenceType();
    Class<?> clazz = Class.forName(classType.name());
    DebugClassOperations operations = new DebugClassOperations(
            classType::locationsOfLine,
            eventRequestManager::createBreakpointRequest,
            eventRequestManager::deleteEventRequest
    );
    DebugClassSource debugClassSource = debugClassSources.get(clazz);
    DebugClass debugClass = new DebugClass(clazz, debugClassSource, operations);
    for (int compileTimeBreakpoint : debugClassSource.getCompileTimeBreakpoints()) {
      if (!debugClass.requestBreakpointAt(compileTimeBreakpoint)) {
        System.out.printf("Unable to create breakpoint at line %d in %s\n", compileTimeBreakpoint, clazz.getName());
      }
    }
    debugClasses.put(clazz, debugClass);
  }

  public DebugClass getDebugClassFor(Class<?> clazz) {
    return debugClasses.get(clazz);
  }


  public void reportVirtualMachineException(ExceptionEvent event) {
    Object exception = unpackReference(event.thread(), event.exception());
    debugClassTextView.setText(String.format("Exception in Virtual Machine\n%s\n\n", exception));
  }

  public void reportException(Exception exception) {
    debugClassTextView.setText(String.format("Exception\n%s\n\n", exception));
  }

  public static class DebugClassTextView extends JTextArea {
    private final DebugClassViewerOptions options;
    private List<Rectangle2D> breakpointViews = new ArrayList<>();
    private final LineNumberViewer lineNumberViewer;

    public DebugClassTextView(DebugClassViewerOptions options) {
      super();
      this.options = options = Objects.requireNonNullElseGet(options, DebugClassViewerOptions::new);
      Color textColor = options.getTextColor();
      if (textColor != null) {
        setForeground(textColor);
      }
      Color backgroundColor = options.getBackgroundColor();
      if (backgroundColor != null) {
        setBackground(backgroundColor);
      }
      setOpaque(false);
      setEditable(false);
      setHighlighter(null);
      lineNumberViewer = new LineNumberViewer(this);
    }

    public void repaintWith(DebugClass debugClass) {
      lineNumberViewer.setBreakpointLines(debugClass);
      breakpointViews.clear();
      for (int lineNumber : debugClass.getEnabledBreakpoints()) {
        try {
          Element element = getDocument().getDefaultRootElement().getElement(lineNumber - 1);
          if (element != null) {
            breakpointViews.add(modelToView2D(element.getStartOffset()));
          }
        } catch (BadLocationException ignored) {
        }
      }
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());

      Color breakpointColor = options.getBreakpointColor();
      if (breakpointColor == null) {
        breakpointColor = Color.RED;
      }
      Color selectedLocationColor = options.getSelectedLocationColor();
      if (selectedLocationColor == null) {
        selectedLocationColor = Color.CYAN;
      }
      try {
        for (Rectangle2D breakpointView : breakpointViews) {
          paintRectangle(g, breakpointView, breakpointColor);
        }
        paintRectangle(g, modelToView2D(getCaretPosition()), selectedLocationColor);
      } catch (BadLocationException e) {
        e.printStackTrace();
      }
      super.paintComponent(g);
    }

    private void paintRectangle(Graphics g, Rectangle2D rect, Color color) {
      if (rect != null) {
        g.setColor(color);
        g.fillRect(0, (int) rect.getY(), getWidth(), (int) rect.getHeight());
      }
    }

    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
      super.repaint(tm, 0, 0, getWidth(), getHeight());
    }

    public int getSelectedLineNumber() {
      return getDocument().getDefaultRootElement().getElementIndex(getCaretPosition()) + 1;
    }

  }

}