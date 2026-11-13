function secondSmallest(arr) {
  const unique = [...new Set(arr)];
  unique.sort((a, b) => a - b);
  if (unique.length < 2) return null;
  return unique[1];
}

const input = prompt("Enter numbers separated by spaces:"); // e.g. 5 2 9 2 7
const arr = input.split(" ").map(Number);                   // text -> array of numbers
console.log("Array:", arr);
console.log("Second smallest:", secondSmallest(arr));