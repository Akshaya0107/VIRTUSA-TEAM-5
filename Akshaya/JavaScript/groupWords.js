const readline = require("readline");

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
});

rl.question("Enter words separated by spaces: ", (input) => {

    const words = input.trim().split(/\s+/);

    const groupedWords = new Map();

    for (const word of words) {

        const firstCharacter = word[0].toLowerCase();

        if (!groupedWords.has(firstCharacter)) {
            groupedWords.set(firstCharacter, []);
        }

        groupedWords.get(firstCharacter).push(word);
    }

    console.log("\n===== Grouped Words =====");

    for (const [character, words] of groupedWords) {
        console.log(character.toUpperCase() + " : " + words.join(", "));
    }

    rl.close();
});