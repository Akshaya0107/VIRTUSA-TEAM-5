import java.util.Scanner;

public class PowerWithoutMathPow {
    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.print("Enter base: ");
        int a = sc.nextInt();

        System.out.print("Enter exponent: ");
        int b = sc.nextInt();

        int result = 1;

        for (int i = 1; i <= b; i++) {
            result = result * a;
        }

        System.out.println(a + "^" + b + " = " + result);

        sc.close();
    }
}
