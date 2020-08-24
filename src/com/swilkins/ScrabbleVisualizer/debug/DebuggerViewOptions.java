package com.swilkins.ScrabbleVisualizer.debug;

import java.awt.*;

public class DebuggerViewOptions {

  private Color textColor = Color.BLACK;
  private Color backgroundColor = Color.WHITE;
  private Color selectedLocationColor = Color.CYAN;
  private Color breakpointColor = Color.RED;

  private boolean isScrollable = true;
  private boolean centeringPreservedOnClick = false;

  public DebuggerViewOptions(Color textColor, Color backgroundColor, Color selectedLocationColor, Color breakpointColor) {
    this.textColor = textColor;
    this.backgroundColor = backgroundColor;
    this.selectedLocationColor = selectedLocationColor;
    this.breakpointColor = breakpointColor;
  }

  public DebuggerViewOptions(Color textColor, Color backgroundColor) {
    this.textColor = textColor;
    this.backgroundColor = backgroundColor;
  }

  public DebuggerViewOptions() {

  }

  public boolean isCenteringPreservedOnClick() {
    return centeringPreservedOnClick;
  }

  public void setCenteringPreservedOnClick(boolean centeringPreservedOnClick) {
    this.centeringPreservedOnClick = centeringPreservedOnClick;
  }

  public boolean isScrollable() {
    return isScrollable;
  }

  public void setScrollable(boolean scrollable) {
    isScrollable = scrollable;
  }

  public Color getTextColor() {
    return textColor;
  }

  public void setTextColor(Color textColor) {
    this.textColor = textColor;
  }

  public Color getBackgroundColor() {
    return backgroundColor;
  }

  public void setBackgroundColor(Color backgroundColor) {
    this.backgroundColor = backgroundColor;
  }

  public Color getSelectedLocationColor() {
    return selectedLocationColor;
  }

  public void setSelectedLocationColor(Color selectedLocationColor) {
    this.selectedLocationColor = selectedLocationColor;
  }

  public Color getBreakpointColor() {
    return breakpointColor;
  }

  public void setBreakpointColor(Color breakpointColor) {
    this.breakpointColor = breakpointColor;
  }

}
