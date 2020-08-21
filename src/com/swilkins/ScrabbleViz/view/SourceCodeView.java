package com.swilkins.ScrabbleViz.view;

import com.swilkins.ScrabbleViz.utility.Utilities;

import javax.swing.*;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.FocusListener;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;

public class SourceCodeView extends JTextArea {

  public SourceCodeView(InputStream sourceCodeStream) {
    super(Utilities.inputStreamToString(sourceCodeStream));
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

  public void jumpToLine(int line) {
    Element root = getDocument().getDefaultRootElement();
    line = Math.max(line, 1);
    line = Math.min(line, root.getElementCount());
    int startOfLineOffset = root.getElement(line - 1).getStartOffset();
    setCaretPosition(startOfLineOffset);

    Container container = SwingUtilities.getAncestorOfClass(JViewport.class, this);

    if (container == null) {
      return;
    }

    try {
      Rectangle2D r = this.modelToView2D(this.getCaretPosition());
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

}