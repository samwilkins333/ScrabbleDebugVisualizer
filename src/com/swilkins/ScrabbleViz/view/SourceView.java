package com.swilkins.ScrabbleViz.view;

import com.sun.jdi.event.ExceptionEvent;
import com.swilkins.ScrabbleViz.debug.exception.MissingSourceException;

import javax.swing.*;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.FocusListener;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

import static com.swilkins.ScrabbleViz.utility.Utilities.unpackReference;

public class SourceView extends JScrollPane {
  private final Map<Class<?>, String> sources = new HashMap<>();
  private final Contents contents;

  public SourceView(Color fg, Color bg) {
    super();
    contents = new Contents(fg, bg);
    initialize();
  }

  public SourceView() {
    super();
    contents = new Contents();
    initialize();
  }

  private void initialize() {
    setViewportView(contents);
    setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    setWheelScrollingEnabled(false);
    setRowHeaderView(new TextLineNumber(contents));
  }

  public Contents getContents() {
    return contents;
  }

  public void addSource(Class<?> clazz, String raw) {
    sources.put(clazz, raw);
  }

  public void highlightLine(Class<?> clazz, int lineNumber) {
    String raw = sources.get(clazz);
    if (raw == null) {
      throw new MissingSourceException(clazz);
    }
    contents.setText(raw);

    Element root = contents.getDocument().getDefaultRootElement();
    lineNumber = Math.max(lineNumber, 1);
    lineNumber = Math.min(lineNumber, root.getElementCount());
    int startOfLineOffset = root.getElement(lineNumber - 1).getStartOffset();
    contents.setCaretPosition(startOfLineOffset);

    Container container = SwingUtilities.getAncestorOfClass(JViewport.class, contents);

    if (container != null) {
      try {
        Rectangle2D r = contents.modelToView2D(contents.getCaretPosition());
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

  public void reportException(ExceptionEvent event) {
    Object exception = unpackReference(event.thread(), event.exception());
    contents.setText(String.format("EXCEPTION\n%s\n\n", exception));
  }

  public static class Contents extends JTextArea {

    public Contents() {
      super();
      initialize();
    }

    public Contents(Color fg, Color bg) {
      super();
      initialize();
      if (fg != null) {
        setForeground(fg);
      }
      if (bg != null) {
        setBackground(bg);
      }
    }

    private void initialize() {
      setOpaque(false);
      setRequestFocusEnabled(false);
      setFocusable(false);
      setEditable(false);
      setHighlighter(null);
      for (CaretListener listener : getCaretListeners()) {
        removeCaretListener(listener);
      }
      for (MouseListener listener : getMouseListeners()) {
        removeMouseListener(listener);
      }
      for (FocusListener listener : getFocusListeners()) {
        removeFocusListener(listener);
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
      try {
        Rectangle2D rect = modelToView2D(getCaretPosition());
        if (rect != null) {
          g.setColor(Color.MAGENTA);
          g.fillRect(0, (int) rect.getY(), getWidth(), (int) rect.getHeight());
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

  }

}