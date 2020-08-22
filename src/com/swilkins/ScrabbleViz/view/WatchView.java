package com.swilkins.ScrabbleViz.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.swilkins.ScrabbleBase.Board.Configuration.STANDARD_BOARD_DIMENSIONS;
import static com.swilkins.ScrabbleBase.Board.Configuration.STANDARD_RACK_CAPACITY;

public class WatchView extends JPanel {
  private final JLabel[][] cells = new JLabel[STANDARD_BOARD_DIMENSIONS][STANDARD_BOARD_DIMENSIONS];
  private final JLabel[] rack = new JLabel[STANDARD_RACK_CAPACITY];

  private final Map<String, ImageIcon> directionIcons = new HashMap<>();
  private static final int ICON_SIZE = 12;

  private JLabel currentCell;
  private List<Object[]> currentPlacements = new ArrayList<>();

  private void create(String name) {
    ImageIcon icon = new ImageIcon(WatchView.class.getResource(String.format("../resource/icons/%s.png", name)));
    Image image = icon.getImage();
    Image scaled = image.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
    directionIcons.put(name, new ImageIcon(scaled));
  }

  public WatchView(Dimension dimension) {
    super();

    create("up");
    create("down");
    create("left");
    create("right");

    setBackground(Color.WHITE);
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    JPanel boardView = new JPanel(new GridLayout(STANDARD_BOARD_DIMENSIONS, STANDARD_BOARD_DIMENSIONS, 0, 0));
    boardView.setBackground(Color.WHITE);
    boardView.setPreferredSize(dimension);
    boardView.setBorder(new EmptyBorder(0, 5, 0, 5));
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

    add(boardView);
    JPanel padding = new JPanel();
    padding.setLayout(new BoxLayout(padding, BoxLayout.X_AXIS));
    padding.setPreferredSize(new Dimension(dimension.width * 2, dimension.height));
    padding.setBackground(Color.WHITE);

    JPanel rack = new JPanel();
    for (int i = 0; i < STANDARD_RACK_CAPACITY; i++) {
      JLabel rackTile = new JLabel("");
      this.rack[i] = rackTile;
      rackTile.setBorder(new MatteBorder(0, 0, 1, 0, Color.BLACK));
      rack.add(rackTile);
    }
    rack.setLayout(new BoxLayout(rack, BoxLayout.X_AXIS));
    padding.add(rack);
    add(padding);
  }

  public void updateFrom(Map<String, Object> unpackedVariables) {
    Object board = unpackedVariables.get("board");
    if (board != null) {
      Object[] rows = (Object[]) unpackedVariables.get("board");
      for (int y = 0; y < STANDARD_BOARD_DIMENSIONS; y++) {
        Object[] row = (Object[]) rows[y];
        for (int x = 0; x < STANDARD_BOARD_DIMENSIONS; x++) {
          Object[] components = (Object[]) row[x];
          if (components != null) {
            cells[y][x].setText(tileRepresentation(components));
          }
        }
      }
    }

    int x = (int) unpackedVariables.get("x");
    int y = (int) unpackedVariables.get("y");
    String direction = (String) unpackedVariables.get("dir");
    currentCell = cells[y][x];
    currentCell.setBackground(Color.YELLOW);
    currentCell.setIcon(directionIcons.get(direction));

    Object[] placements = (Object[]) unpackedVariables.get("placed");
    for (Object placement : placements) {
      Object[] components = (Object[]) placement;
      currentPlacements.add(components);
      cells[(int) components[1]][(int) components[0]].setText(tileRepresentation((Object[]) components[2]));
    }

    int i = 0;
    for (Object tile : (Object[]) unpackedVariables.get("rack")) {
      Object[] components = (Object[]) tile;
      if (components != null) {
        rack[i++].setText(tileRepresentation(components));
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
    currentCell.setBackground(Color.white);
    currentCell.setIcon(null);
    for (Object[] placement : currentPlacements) {
      cells[(int) placement[1]][(int) placement[0]].setText("");
    }
    for (int i = 0; i < STANDARD_RACK_CAPACITY; i++) {
      rack[i].setText("");
    }
  }

}
