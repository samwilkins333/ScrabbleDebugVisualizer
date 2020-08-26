package com.swilkins.ScrabbleVisualizer.view;

import com.sun.jdi.Location;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;

import static com.swilkins.ScrabbleBase.Board.Configuration.STANDARD_BOARD_DIMENSIONS;
import static com.swilkins.ScrabbleBase.Board.Configuration.STANDARD_RACK_CAPACITY;
import static com.swilkins.ScrabbleVisualizer.debug.ScrabbleVisualizer.ICON_DIMENSION;
import static com.swilkins.ScrabbleVisualizer.utility.Utilities.createImageIconFrom;

public class WatchView extends JPanel {

  private JTabbedPane tabbedPane;

  private final JLabel[][] cells = new JLabel[STANDARD_BOARD_DIMENSIONS][STANDARD_BOARD_DIMENSIONS];
  private JLabel currentCell;

  private final JLabel[] rack = new JLabel[STANDARD_RACK_CAPACITY];

  private final Map<String, ImageIcon> directionIcons = new HashMap<>();

  private final JPanel boardView;

  private JTextArea candidates = new JTextArea();
  private JTextArea rawWatchedName = new JTextArea();
  private JTextArea rawWatchedValue = new JTextArea();

  private List<Object[]> currentPlacements = new ArrayList<>();
  private Map<String[], BiConsumer<Location, Iterator<Object>>> updaters = new LinkedHashMap<>();

  public WatchView(Dimension dimension) {
    super();

    registerUpdaters();

    createIcon("up");
    createIcon("down");
    createIcon("left");
    createIcon("right");

    setPreferredSize(dimension);
    setBackground(Color.WHITE);
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    boardView = new JPanel(new GridLayout(STANDARD_BOARD_DIMENSIONS, STANDARD_BOARD_DIMENSIONS, 0, 0));
    boardView.setBackground(Color.WHITE);
    int boardViewSize = dimension.height;
    boardView.setPreferredSize(new Dimension(boardViewSize, boardViewSize));
    boardView.setBorder(new EmptyBorder(5, 9, 3, 0));
    for (int y = 0; y < STANDARD_BOARD_DIMENSIONS; y++) {
      for (int x = 0; x < STANDARD_BOARD_DIMENSIONS; x++) {
        JLabel cell = new JLabel("", SwingConstants.CENTER);
        cell.setOpaque(true);
        cell.setBackground(Color.WHITE);
        int resolvedTop = y == 0 ? 1 : 0;
        int resolvedLeft = x == 0 ? 1 : 0;
        cell.setBorder(new MatteBorder(resolvedTop, resolvedLeft, 1, 1, Color.BLACK));
        cells[y][x] = cell;
        boardView.add(cell);
      }
    }

    boardView.setEnabled(false);
    add(boardView);

    tabbedPane = new JTabbedPane();
    tabbedPane.setPreferredSize(new Dimension(dimension.width - dimension.height, dimension.height));
    tabbedPane.setBorder(new EmptyBorder(6, 1, -1, 3));
    JScrollPane scrollPane;

    JPanel rack = new JPanel();
    scrollPane = new JScrollPane(rack);
    rack.setLayout(new GridLayout(1, STANDARD_RACK_CAPACITY));
    rack.setBackground(Color.WHITE);
    rack.setPreferredSize(new Dimension());
    for (int i = 0; i < STANDARD_RACK_CAPACITY; i++) {
      JLabel rackTile = new JLabel("", SwingConstants.CENTER);
      this.rack[i] = rackTile;
      rackTile.setBackground(Color.WHITE);
      rack.add(rackTile);
    }
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    tabbedPane.addTab("Visuals", scrollPane);

    rawWatchedName.setEditable(false);
    rawWatchedName.setHighlighter(null);
    rawWatchedName.setBackground(Color.WHITE);
    rawWatchedValue.setEditable(false);
    rawWatchedValue.setHighlighter(null);
    rawWatchedValue.setBackground(Color.WHITE);
    JPanel rawWatched = new JPanel();
    rawWatched.setLayout(new GridLayout(1, 2));
    rawWatched.setBackground(Color.WHITE);
    rawWatched.add(rawWatchedName);
    rawWatched.add(rawWatchedValue);
    scrollPane = new JScrollPane(rawWatched);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    tabbedPane.addTab("Raw", scrollPane);

    candidates.setEditable(false);
    candidates.setHighlighter(null);
    scrollPane = new JScrollPane(candidates);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    tabbedPane.addTab("Candidates (0)", scrollPane);

    JTextArea outputView = new JTextArea();
    outputView.setEditable(false);
    outputView.setHighlighter(null);
    scrollPane = new JScrollPane(outputView);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    tabbedPane.addTab("Output", scrollPane);

    tabbedPane.setEnabled(false);
    add(tabbedPane);
  }

  private void createIcon(String name) {
    URL url = getClass().getResource(String.format("../resource/icons/%s.png", name));
    directionIcons.put(name, createImageIconFrom(url, ICON_DIMENSION));
  }

  private void registerUpdaters() {
    updaters.put(new String[]{"board"}, (loc, args) -> {
      Object[] rows = (Object[]) args.next();
      for (int y = 0; y < STANDARD_BOARD_DIMENSIONS; y++) {
        Object[] row = (Object[]) rows[y];
        for (int x = 0; x < STANDARD_BOARD_DIMENSIONS; x++) {
          Object[] components = (Object[]) row[x];
          if (components != null) {
            JLabel target = cells[y][x];
            target.setText(tileRepresentation(components));
            target.setBackground(Color.LIGHT_GRAY);
          }
        }
      }
    });
    updaters.put(new String[]{"y", "x", "dir"}, (loc, args) -> {
      currentCell = cells[(int) args.next()][(int) args.next()];
      currentCell.setBackground(Color.YELLOW);
      currentCell.setIcon(directionIcons.get(args.next().toString()));
    });
    updaters.put(new String[]{"placed"}, (loc, args) -> {
      Object[] placements = (Object[]) args.next();
      for (Object placement : placements) {
        Object[] components = (Object[]) placement;
        currentPlacements.add(components);
        JLabel cell = cells[(int) components[1]][(int) components[0]];
        cell.setText(tileRepresentation((Object[]) components[2]));
        if (loc.lineNumber() == 203) {
          cell.setBackground(Color.GREEN);
          if (cell.equals(currentCell)) {
            cell.setIcon(null);
          }
        }
      }
    });
    updaters.put(new String[]{"rack"}, (loc, args) -> {
      int i = 0;
      for (Object tile : (Object[]) args.next()) {
        Object[] components = (Object[]) tile;
        if (components != null) {
          rack[i++].setText(tileRepresentation(components));
        }
      }
    });
    updaters.put(new String[]{"all"}, (loc, args) -> {
      Object[] all = (Object[]) args.next();
      tabbedPane.setTitleAt(2, String.format("Candidates (%d)", all.length));
      StringBuilder builder = new StringBuilder();
      Arrays.sort(all, Comparator.comparingInt(candidate -> ((int) ((Object[]) candidate)[0])).reversed());
      for (Object candidate : all) {
        builder.append(((Object[]) candidate)[1]).append("\n");
      }
      candidates.setText(builder.toString());
    });
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    boardView.setEnabled(enabled);
    tabbedPane.setEnabled(enabled);
  }

  public void updateFrom(Location location, Map<String, Object> unpackedVariables) {
    StringBuilder rawNameBuilder = new StringBuilder();
    StringBuilder rawValueBuilder = new StringBuilder();

    List<Map.Entry<String, Object>> variables = new ArrayList<>(unpackedVariables.entrySet());
    variables.sort(Map.Entry.comparingByKey());
    for (Map.Entry<String, Object> entry : variables) {
      rawNameBuilder.append(entry.getKey()).append("\n");
      rawValueBuilder.append(entry.getValue()).append("\n");
    }
    rawWatchedName.setText(rawNameBuilder.toString());
    rawWatchedValue.setText(rawValueBuilder.toString());

    for (Map.Entry<String[], BiConsumer<Location, Iterator<Object>>> entry : updaters.entrySet()) {
      String[] dependencies = entry.getKey();
      List<Object> args = new ArrayList<>();
      for (String dependencyName : entry.getKey()) {
        Object arg = unpackedVariables.get(dependencyName);
        if (arg != null) {
          args.add(arg);
        }
      }
      if (dependencies.length == args.size()) {
        entry.getValue().accept(location, args.iterator());
      }
    }
  }

  private String tileRepresentation(Object[] components) {
    Character letter = (Character) components[0];
    Character proxy = (Character) components[1];
    String display;
    if (proxy != null) {
      display = String.format("%s%s", letter, proxy);
    } else {
      display = String.valueOf(letter);
    }
    return display;
  }

  public void clean() {
    if (currentCell != null) {
      currentCell.setBackground(Color.white);
      currentCell.setIcon(null);
    }
    for (Object[] placement : currentPlacements) {
      JLabel cell = cells[(int) placement[1]][(int) placement[0]];
      cell.setText("");
      cell.setBackground(Color.WHITE);
    }
    rawWatchedName.setText("");
    rawWatchedValue.setText("");
    for (int i = 0; i < STANDARD_RACK_CAPACITY; i++) {
      rack[i].setText("");
    }
  }

}
