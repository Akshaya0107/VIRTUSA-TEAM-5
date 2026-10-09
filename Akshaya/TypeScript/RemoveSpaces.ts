import * as readline from "readline";

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
});

rl.question("Enter a string: ", (text: string) => {

    const result: string = text.replace(/\s/g, "");

    console.log("\nOriginal String:", text);
    console.log("String without spaces:", result);

    rl.close();
});
