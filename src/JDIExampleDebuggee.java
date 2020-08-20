public class JDIExampleDebuggee {

  public static void main(String[] args) {
    String[][] test = new String[15][15];
    test[7][7] = "fish";
    int number = 4;
    String jpda = "Java Platform Debugger Architecture";
    System.out.println("Hi Everyone, Welcome to " + jpda); // add a break point here

    String jdi = "Java Debug Interface"; // add a break point here and also stepping in here
    String text = "Today, we'll dive into " + jdi;
    System.out.println(text);
  }

}
