package com.swilkins.ScrabbleViz.debugClass;

import java.awt.*;

public class DebugClassViewerOptions {

  private Color textColor;
  private Color backgroundColor;
  private Color selectedLocationColor;
  private Color breakpointColor;

  public DebugClassViewerOptions(Color textColor, Color backgroundColor, Color selectedLocationColor, Color breakpointColor) {
    this.textColor = textColor;
    this.backgroundColor = backgroundColor;
    this.selectedLocationColor = selectedLocationColor;
    this.breakpointColor = breakpointColor;
  }

  public DebugClassViewerOptions(Color textColor, Color backgroundColor) {
    this.textColor = textColor;
    this.backgroundColor = backgroundColor;
  }

  public DebugClassViewerOptions() {

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
