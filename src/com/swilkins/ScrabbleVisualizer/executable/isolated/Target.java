package com.swilkins.ScrabbleVisualizer.executable.isolated;

public class Target {

  public static void main(String[] args) {
    int a = 1;
    int b = 2;
    int c = test(a + b);
    System.out.println(c);
  }

  private static int test(int args) {
    return 8 + args;
  }

}
