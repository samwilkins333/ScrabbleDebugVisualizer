package com.swilkins.ScrabbleVisualizer.executable;

public class Fibonacci {

  public static void main(String[] args) {
    Fibonacci fibonacci = new Fibonacci();
    int value = fibonacci.get(16);
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
