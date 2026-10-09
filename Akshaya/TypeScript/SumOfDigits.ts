import * as readline from "readline";

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
});

rl.question("Enter a number: ", (input: string) => {

    let number: number = Number(input);
    let sum: number = 0;
    let temp: number = number;

    while (temp > 0) {

        let digit: number = temp % 10;

        sum = sum + digit;

        temp = Math.floor(temp / 10);
    }

    console.log("Sum of digits:", sum);

    rl.close();
});