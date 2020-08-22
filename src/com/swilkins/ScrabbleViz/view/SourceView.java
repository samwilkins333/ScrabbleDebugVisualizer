package com.swilkins.ScrabbleViz.view;

import com.sun.jdi.event.ExceptionEvent;
import com.swilkins.ScrabbleViz.debug.exception.MissingSourceException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

import static com.swilkins.ScrabbleViz.utility.Unpackers.unpackReference;

public class SourceView extends JPanel {
  private final Map<Class<?>, String> sources = new HashMap<>();
  private final Contents contents;
  private final JLabel locationLabel = new JLabel(" ");
  private Class<?> displayedClazz;

  public SourceView(Color foreground, Color background, Color highlight) {
    super();
    contents = new Contents(foreground, background, highlight);
    initialize();
  }

  public SourceView() {
    super();
    contents = new Contents();
    initialize();
  }

  private void initialize() {
    contents.addCaretListener(e -> {
      String contents = displayedClazz == null
              ? " "
              : String.format("%s: %d", displayedClazz.getName(), getContents().getCurrentLineNumber());
      locationLabel.setText(contents);
    });

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
    header.add(locationLabel);
    locationLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
    add(header);

    JScrollPane scrollPane = new JScrollPane(contents);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setWheelScrollingEnabled(false);
    scrollPane.setRowHeaderView(new TextLineNumber(contents));
    add(scrollPane);
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
    displayedClazz = clazz;
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
    private Color highlight = Color.LIGHT_GRAY;

    public Contents() {
      super();
      initialize();
    }

    public Contents(Color foreground, Color background, Color highlight) {
      super();
      if (highlight != null) {
        this.highlight = highlight;
      }
      if (foreground != null) {
        setForeground(foreground);
      }
      if (background != null) {
        setBackground(background);
      }
      initialize();
    }

    private void initialize() {
      setOpaque(false);
      setEditable(false);
      setHighlighter(null);
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
      try {
        Rectangle2D rect = modelToView2D(getCaretPosition());
        if (rect != null) {
          g.setColor(highlight);
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

    public int getCurrentLineNumber() {
      return getDocument().getDefaultRootElement().getElementIndex(getCaretPosition()) + 1;
    }

  }

}