package com.swilkins.ScrabbleVisualizer.executable;

import com.swilkins.ScrabbleVisualizer.debug.interfaces.DebugTarget;

@DebugTarget(compileTimeBreakpoints = {23})
public class Fibonacci {

  public static void main(String[] args) throws NumberFormatException {
    if (args.length != 1) {
      throw new IllegalArgumentException();
    }
    int index;
    try {
      index = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException();
    }
    int value = new Fibonacci().get(index);
    System.out.println(value);
  }

  public int get(int index) {
    return getRecursive(index);
  }

  private int getRecursive(int number) {
    if (number == 1 || number == 0) {
      return number;
    }
    return getRecursive(number - 2) + getRecursive(number - 1);
  }

}
