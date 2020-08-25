package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.AbsentInformationException;
import com.swilkins.ScrabbleVisualizer.view.LineNumberView;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;

import static com.swilkins.ScrabbleVisualizer.debug.DebuggerControl.RUN;

public class DebuggerView extends JPanel {

  private final JScrollPane scrollWrapper;

  private final JLabel locationLabel = new JLabel();
  private final DebugClassTextView debugClassTextView;
  private final JPanel defaultControlPanel;
  private final Map<DebuggerControl, JButton> controlButtons = new LinkedHashMap<>();

  private DebugClassLocation selectedLocation;
  private boolean isCenteringPreservedOnClick = false;

  public DebuggerView() {
    super();

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    debugClassTextView = new DebugClassTextView();
    debugClassTextView.addCaretListener(e -> {
      String locationLabelText;
      if (selectedLocation != null) {
        DebugClass debugClass = selectedLocation.getDebugClass();
        int selectedLineNumber = getDebugClassTextView().getSelectedLineNumber();
        locationLabelText = String.format("%s: %d", debugClass.getClazz().getName(), selectedLineNumber);
        locationLabel.setText(locationLabelText);
        DebugClassLocation selectedLocation = new DebugClassLocation(debugClass, selectedLineNumber);
        if (isCenteringPreservedOnClick) {
          setSelectedLocation(selectedLocation);
        } else {
          this.selectedLocation = selectedLocation;
        }
      }
    });

    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
    header.add(locationLabel);
    locationLabel.setText(" ");
    locationLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
    add(header);

    scrollWrapper = new JScrollPane(debugClassTextView);
    scrollWrapper.setRowHeaderView(debugClassTextView.lineNumberView);
    add(scrollWrapper);

    defaultControlPanel = new JPanel();
    defaultControlPanel.setLayout(new BoxLayout(defaultControlPanel, BoxLayout.X_AXIS));
    defaultControlPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
    add(defaultControlPanel);
  }

  public void setDefaultActionListeners(Map<DebuggerControl, ActionListener> defaultActionListeners) {
    JButton controlButton;
    for (Map.Entry<DebuggerControl, ActionListener> defaultControlButton : defaultActionListeners.entrySet()) {
      DebuggerControl control = defaultControlButton.getKey();
      controlButton = new JButton(control.getLabel());
      controlButton.addActionListener(defaultControlButton.getValue());
      controlButton.setEnabled(control == RUN);
      controlButtons.put(control, controlButton);
    }
  }

  public void setControlButtonEnabled(DebuggerControl control, boolean enabled) {
    JButton controlButton = controlButtons.get(control);
    if (controlButton != null) {
      controlButton.setEnabled(enabled);
    }
  }

  public void setAllControlButtonsEnabled(boolean enabled) {
    controlButtons.values().forEach(controlButton -> controlButton.setEnabled(enabled));
  }

  public void setOptions(DebuggerViewOptions options) {
    DebuggerViewOptions resolvedOptions = Objects.requireNonNullElseGet(options, DebuggerViewOptions::new);
    debugClassTextView.setOptions(resolvedOptions);

    isCenteringPreservedOnClick = resolvedOptions.isCenteringPreservedOnClick();

    boolean isScrollable = resolvedOptions.isScrollable();
    scrollWrapper.setWheelScrollingEnabled(isScrollable);
    if (!isScrollable) {
      scrollWrapper.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
      scrollWrapper.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    }
  }

  public JButton addDefaultControlButton(DebuggerControl control) {
    JButton controlButton = controlButtons.get(control);
    defaultControlPanel.add(controlButton);
    return controlButton;
  }

  public DebugClassLocation getSelectedLocation() {
    return selectedLocation;
  }

  public void setSelectedLocation(DebugClassLocation updatedLocation) {
    DebugClass updatedDebugClass = updatedLocation.getDebugClass();
    if (selectedLocation == null || updatedDebugClass != selectedLocation.getDebugClass()) {
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

  public void reportException(String exception, DebuggerExceptionType type) {
    debugClassTextView.setText(String.format("Exception in %s\n%s\n\n", type.getLocationName(), exception));
  }

  public static class DebugClassTextView extends JTextArea {
    private final LineNumberView lineNumberView;
    private DebuggerViewOptions options = new DebuggerViewOptions();
    private List<Rectangle2D> breakpointViews = new ArrayList<>();

    public DebugClassTextView() {
      super();
      setOpaque(false);
      setEditable(false);
      setHighlighter(null);
      lineNumberView = new LineNumberView(this);
    }

    public void setOptions(DebuggerViewOptions options) {
      this.options = options;
      setForeground(options.getTextColor());
      setBackground(options.getBackgroundColor());
      lineNumberView.repaint();
      repaint();
    }

    public void repaintWith(DebugClass debugClass) {
      lineNumberView.setBreakpointLines(debugClass);
      breakpointViews.clear();
      for (int lineNumber : debugClass.getBreakpoints()) {
        try {
          Element element = getDocument().getDefaultRootElement().getElement(lineNumber - 1);
          if (element != null) {
            breakpointViews.add(modelToView2D(element.getStartOffset()));
          }
        } catch (BadLocationException e) {
          e.printStackTrace();
        }
      }
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
      Color breakpointColor = options.getBreakpointColor();
      try {
        for (Rectangle2D breakpointView : breakpointViews) {
          paintRectangle(g, breakpointView, breakpointColor);
        }
        paintRectangle(g, modelToView2D(getCaretPosition()), options.getSelectedLocationColor());
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