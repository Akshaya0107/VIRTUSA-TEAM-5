function checkNumber(n: number): string {
  if (Number.isInteger(n)) {
    return n + " is an integer";
  } else {
    return n + " is a floating-point number";
  }
}

console.log(checkNumber(10));    // 10 is an integer
console.log(checkNumber(3.14));  // 3.14 is a floating-point number
console.log(checkNumber(-8));    // -8 is an integer
console.log(checkNumber(5.0));   // 5 is an integer