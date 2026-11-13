function randInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

const min = Number(prompt("Enter minimum:"));
const max = Number(prompt("Enter maximum:"));
console.log("Random number:", randInt(min, max));