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

public class SourceView extends JTextArea {
  private final Map<Class<?>, String> sources = new HashMap<>();

  public SourceView() {
    super();
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

  public void addSource(Class<?> clazz, String raw) {
    sources.put(clazz, raw);
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

  public void highlightLine(Class<?> clazz, int lineNumber) {
    String raw = sources.get(clazz);
    if (raw == null) {
      throw new MissingSourceException(clazz);
    }
    setText(raw);
    Element root = getDocument().getDefaultRootElement();
    lineNumber = Math.max(lineNumber, 1);
    lineNumber = Math.min(lineNumber, root.getElementCount());
    int startOfLineOffset = root.getElement(lineNumber - 1).getStartOffset();
    setCaretPosition(startOfLineOffset);

    Container container = SwingUtilities.getAncestorOfClass(JViewport.class, this);

    if (container == null) {
      return;
    }

    try {
      Rectangle2D r = this.modelToView2D(this.getCaretPosition());
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

  public void reportException(ExceptionEvent event) {
    Object exception = unpackReference(event.thread(), event.exception());
    setText(String.format("EXCEPTION\n%s\n\n", exception));
  }

}