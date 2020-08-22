package com.swilkins.ScrabbleViz.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.Map;

import static com.swilkins.ScrabbleBase.Board.Configuration.STANDARD_BOARD_DIMENSIONS;

public class WatchView extends JPanel {
  private final JPanel boardView;
  private final Dimension dimension;
  private final JLabel[][] cells = new JLabel[STANDARD_BOARD_DIMENSIONS][STANDARD_BOARD_DIMENSIONS];

  public WatchView(Dimension dimension) {
    super();
    this.dimension = dimension;
    setBackground(Color.WHITE);
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    boardView = new JPanel(new GridLayout(STANDARD_BOARD_DIMENSIONS, STANDARD_BOARD_DIMENSIONS, 0, 0));
    boardView.setBackground(Color.WHITE);
    boardView.setPreferredSize(dimension);
    boardView.setBorder(new EmptyBorder(0, 5, 0, 5));
    for (int y = 0; y < STANDARD_BOARD_DIMENSIONS; y++) {
      for (int x = 0; x < STANDARD_BOARD_DIMENSIONS; x++) {
        JLabel cell = new JLabel("", SwingConstants.CENTER);
        int resolvedTop = y == 0 ? 1 : 0;
        int resolvedLeft = x == 0 ? 1 : 0;
        cell.setBorder(new MatteBorder(resolvedTop, resolvedLeft, 1, 1, Color.BLACK));
        cells[y][x] = cell;
        boardView.add(cell);
      }
    }
  }

  public void updateFrom(Map<String, Object> unpackedVariables) {
    Object board = unpackedVariables.get("board");
    if (board != null) {
      Object[] rows = (Object[]) unpackedVariables.get("board");
      for (int y = 0; y < STANDARD_BOARD_DIMENSIONS; y++) {
        Object[] row = (Object[]) rows[y];
        for (int x = 0; x < STANDARD_BOARD_DIMENSIONS; x++) {
          Character playedLetter = (Character) row[x];
          if (playedLetter != null) {
            cells[y][x].setText(String.valueOf(playedLetter));
          }
        }
      }
    }
  }

  public void setRunning(boolean running) {
    removeAll();
    validate();
    if (running) {
      add(new JLabel("Running..."));
    } else {
      add(boardView);
      JPanel dummy = new JPanel();
      dummy.setBackground(Color.WHITE);
      dummy.setPreferredSize(new Dimension(dimension.width * 2, dimension.height));
      add(dummy);
    }
    validate();
  }

}
