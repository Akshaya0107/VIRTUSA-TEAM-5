function fakeApiCall(name: string, delayMs: number): Promise<string> {
  return new Promise((resolve) => {
    setTimeout(() => {
      console.log(name + " finished");
      resolve(name + " data");
    }, delayMs);
  });
}

async function runSequentially(): Promise<void> {
  console.log("Start");

  const r1 = await fakeApiCall("API 1", 2000);
  const r2 = await fakeApiCall("API 2", 1000);
  const r3 = await fakeApiCall("API 3", 500);

  console.log("Results:", r1, r2, r3);
}
runSequentially();