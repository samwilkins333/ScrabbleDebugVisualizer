package com.swilkins.ScrabbleVisualizer.executable;

import com.swilkins.ScrabbleVisualizer.debug.interfaces.DebugTarget;

@DebugTarget
public class Fibonacci {

  public static void main(String[] args) throws NumberFormatException {
    if (args.length == 1) {
      int value = new Fibonacci().get(Integer.parseInt(args[0]));
      System.out.println(value);
    }
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
