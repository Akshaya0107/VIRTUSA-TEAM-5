function mergeArrays(a: number[], b: number[]): number[] {
  return [...a, ...b];
}

const first: number[] = [1, 2, 3];
const second: number[] = [4, 5, 6];

console.log(mergeArrays(first, second)); // [1, 2, 3, 4, 5, 6]

//i can also use return a.concat(b); apparently